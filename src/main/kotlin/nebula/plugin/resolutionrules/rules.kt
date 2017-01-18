/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package nebula.plugin.resolutionrules

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.*
import org.gradle.api.internal.collections.CollectionEventRegister
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.util.Path
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.*
import java.util.regex.Pattern
import org.gradle.api.artifacts.ModuleVersionIdentifier as GradleModuleVersionIdentifier

val versionComparator = DefaultVersionComparator()
val versionScheme = DefaultVersionSelectorScheme(versionComparator)

interface Rule {
    fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension)
}

interface BasicRule : Rule {
    var ruleSet: String?
    val reason: String
    val author: String
    val date: String
}

interface ModuleRule : BasicRule {
    val module: String
}

data class RuleSet(
        var name: String?,
        val replace: List<ReplaceRule>,
        val substitute: List<SubstituteRule>,
        val reject: List<RejectRule>,
        val deny: List<DenyRule>,
        val exclude: List<ExcludeRule>,
        val align: List<AlignRule>) {

    fun afterEvaluateRules() = listOf(replace, substitute, reject, deny, exclude).flatten()

    fun beforeResolveRules() = listOf(AlignRules(align))
}

fun RuleSet.withName(ruleSetName: String): RuleSet {
    name = ruleSetName
    listOf(replace, substitute, reject, deny, exclude, align).flatten().forEach { it.ruleSet = ruleSetName }
    return this
}

fun Collection<RuleSet>.flatten() = RuleSet(
        "flattened",
        flatMap { it.replace },
        flatMap { it.substitute },
        flatMap { it.reject },
        flatMap { it.deny },
        flatMap { it.exclude },
        flatMap { it.align })

data class ReplaceRule(override val module: String, val with: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : ModuleRule {
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension) {
        val moduleId = ModuleIdentifier.valueOf(module)
        val withModuleId = ModuleIdentifier.valueOf(with)
        project.dependencies.modules.module(moduleId.toString()) {
            val details = it as ComponentModuleMetadataDetails
            details.replacedBy(withModuleId.toString())
        }
    }
}

data class SubstituteRule(val module: String, val with: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : BasicRule {
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension) {
        val substitution = resolutionStrategy.dependencySubstitution
        val selector = substitution.module(module)
        val withModuleId = ModuleVersionIdentifier.valueOf(with)
        if (!withModuleId.hasVersion()) {
            throw SubstituteRuleMissingVersionException(withModuleId, this)
        }
        val withSelector = substitution.module(withModuleId.toString()) as ModuleComponentSelector

        if (selector is ModuleComponentSelector) {
            resolutionStrategy.dependencySubstitution.all(action {
                if (requested is ModuleComponentSelector) {
                    val requestedSelector = requested as ModuleComponentSelector
                    if (requestedSelector.group == selector.group && requestedSelector.module == selector.module) {
                        if (versionScheme.parseSelector(selector.version).accept(requestedSelector.version)) {
                            useTarget(withSelector)
                        }
                    }
                }
            })
        } else {
            resolutionStrategy.dependencySubstitution {
                it.substitute(selector).with(withSelector)
            }
        }
    }
}

data class RejectRule(override val module: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : ModuleRule {
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension) {
        val moduleId = ModuleVersionIdentifier.valueOf(module)
        resolutionStrategy.componentSelection.all(Action<ComponentSelection> { selection ->
            val candidate = selection.candidate
            if (candidate.group == moduleId.organization && candidate.module == moduleId.name) {
                if (!moduleId.hasVersion() || candidate.version == moduleId.version) {
                    selection.reject("Rejected by resolution rule $ruleSet - $reason")
                }
            }
        })
    }
}

data class DenyRule(override val module: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : ModuleRule {
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension) {
        val moduleId = ModuleVersionIdentifier.valueOf(module)
        val match = configuration.allDependencies.find {
            it is ExternalModuleDependency && it.group == moduleId.organization && it.name == moduleId.name
        }
        if (match != null && (!moduleId.hasVersion() || match.version == moduleId.version)) {
            throw DependencyDeniedException(moduleId, this)
        }
    }
}

data class AlignRule(val name: String?, val group: Regex, val includes: List<Regex>, val excludes: List<Regex>, val match: String?, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : BasicRule {
    val matchPattern: Pattern by lazy {
        if (VersionMatcher.values().filter { it.name == match }.isNotEmpty()) VersionMatcher.valueOf(match!!).pattern else Pattern.compile(match)
    }

    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension) {
        throw UnsupportedOperationException("Align rules are not applied directly")
    }

    fun resolvedMatches(dep: GradleModuleVersionIdentifier) = ruleMatches(dep.group, dep.name)

    fun ruleMatches(inputGroup: String, inputName: String): Boolean {
        val matchedIncludes = includes.filter { inputName.matches(it) }
        val matchedExcludes = excludes.filter { inputName.matches(it) }
        return inputGroup.matches(group) &&
                (includes.isEmpty() || !matchedIncludes.isEmpty()) &&
                (excludes.isEmpty() || matchedExcludes.isEmpty())
    }
}

data class AlignRules(val aligns: List<AlignRule>) : Rule {
    val logger: Logger = Logging.getLogger(AlignRules::class.java)

    companion object {
        const val CONFIG_SUFFIX = "Align"
    }

    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension) {
        if (aligns.isEmpty() || configuration.name.endsWith(CONFIG_SUFFIX)) { // don't do extra resolves if there are no align rules, and ignore copied configurations
            return
        }

        val baseVersions = project
                .copyConfiguration(configuration, configuration.name, "Baseline")
                .baseSelectedVersions()

        val resolvedVersions = project
                .copyConfiguration(configuration, configuration.name, "Resolved")
                .applyAligns(baseVersions)
                .stableResolvedSelectedVersions()

        configuration.applyAligns(resolvedVersions, true)
    }

    private fun CopiedConfiguration.baseSelectedVersions() = selectedVersions({ it.selected.moduleVersion }, true)

    tailrec private fun CopiedConfiguration.stableResolvedSelectedVersions(pass: Int = 1): Map<AlignRule, String> {
        val resolvedVersions = resolvedSelectedVersions()
        val copy = copyConfiguration("StabilityPass$pass").applyAligns(resolvedVersions)
        if (resolvedVersions == copy.resolvedSelectedVersions()) {
            return resolvedVersions
        } else {
            return copy.stableResolvedSelectedVersions(pass.inc())
        }
    }

    private fun CopiedConfiguration.resolvedSelectedVersions() = selectedVersions({
        val selector = it.requested as ModuleComponentSelector
        DefaultModuleVersionIdentifier(selector.group, selector.module, selector.version)
    })

    private fun CopiedConfiguration.selectedVersions(versionSelector: (ResolvedDependencyResult) -> GradleModuleVersionIdentifier, shouldLog: Boolean = false): Map<AlignRule, String> {
        val (resolved, unresolved) = incoming.resolutionResult.allDependencies
                .partition { it is ResolvedDependencyResult }
        if (shouldLog && unresolved.isNotEmpty()) {
            logger.warn("Resolution rules could not resolve all dependencies to align in configuration '${source.name}' should also fail to resolve (use --info to list unresolved dependencies)")
            logger.info("Resolution rules could not resolve:\n ${unresolved.map { " - $it" }.joinToString("\n")}")
        }
        val resolvedVersions = resolved
                .map { it as ResolvedDependencyResult }
                .filter { it.selected.id is ModuleComponentIdentifier }
                .distinctBy { it.selected.moduleVersion.module }
                .map(versionSelector)

        val selectedVersions = LinkedHashMap<AlignRule, String>()
        aligns.forEach { align ->
            val matches = resolvedVersions.filter { dep: GradleModuleVersionIdentifier -> align.resolvedMatches(dep) }
            if (matches.isNotEmpty()) {
                selectedVersions[align] = alignedVersion(align, matches, this, versionScheme, versionComparator.asStringComparator())
            }
        }
        return selectedVersions
    }

    private fun CopiedConfiguration.applyAligns(selectedVersions: Map<AlignRule, String>)
            = (this as Configuration).applyAligns(selectedVersions) as CopiedConfiguration

    private fun Configuration.applyAligns(selectedVersions: Map<AlignRule, String>, shouldLog: Boolean = false): Configuration {
        resolutionStrategy.eachDependency { details ->
            val target = details.target
            val foundMatch = selectedVersions.filter { it.key.ruleMatches(target.group, target.name) }
            if (foundMatch.isNotEmpty()) {
                val (rule, version) = foundMatch.entries.first()
                if (version != matchedVersion(rule, details.target.version)) {
                    if (shouldLog) {
                        logger.debug("Resolution rule $rule aligning ${details.requested.group}:${details.requested.name} to $version")
                    }
                    details.useVersion(version)
                }
            }
        }
        return this
    }

    /**
     * Various reflection hackiness follows due to deficiencies in the Gradle configuration APIs:
     *
     * - We can't add the configuration to the configuration container to get the addAction handlers, because it causes ConcurrentModificationExceptions
     * - We can't set the configuration name on copyRecursive, which makes for confusing logging output when we're resolving our configurations
     */

    private fun CopiedConfiguration.copyConfiguration(alignmentPhase: String) = project.copyConfiguration(this, baseName, alignmentPhase)

    private fun Project.copyConfiguration(configuration: Configuration, baseName: String, alignmentPhase: String): CopiedConfiguration {
        val copy = configuration.copyRecursive()
        val name = "$baseName$alignmentPhase$CONFIG_SUFFIX"
        copy.setName(name)

        // Apply container register actions, without risking concurrently modifying the configuration container
        val container = configurations
        val method = container.javaClass.getDeclaredMethod("getEventRegister")
        @Suppress("UNCHECKED_CAST")
        val eventRegister = method.invoke(container) as CollectionEventRegister<Configuration>
        eventRegister.addAction.execute(copy)

        // Hacky workaround to prevent Gradle from attempting to resolve a project dependency as an external dependency
        copy.exclude(this.group.toString(), this.name)

        return CopiedConfiguration(configuration, this, baseName, copy)
    }

    private fun Configuration.setName(name: String) {
        setField("name", name)
        val internalConfig = this as ConfigurationInternal
        val parentPath = Path.path(internalConfig.path).parent
        try {
            val path = parentPath.child(name)
            setField("path", path)
            setField("identityPath", path)
        } catch (e: NoSuchMethodError) {
            setField("path", "$parentPath:$name")
        }
    }

    private fun Any.setField(name: String, value: Any) {
        val field = javaClass.findDeclaredField(name)
        field.isAccessible = true

        val modifiersField = Field::class.java.getDeclaredField("modifiers")
        modifiersField.isAccessible = true
        modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())

        field.set(this, value)
    }

    private tailrec fun <T> Class<T>.findDeclaredField(name: String): Field {
        val field = declaredFields
                .filter { it.name == name }
                .singleOrNull()
        if (field != null) {
            return field
        } else if (superclass != null) {
            return superclass.findDeclaredField(name)
        }
        throw IllegalArgumentException("Could not find field $name")
    }

    private class CopiedConfiguration(val source: Configuration, val project: Project, val baseName: String, copy: Configuration) : Configuration by copy

    private fun alignedVersion(rule: AlignRule, moduleVersions: List<GradleModuleVersionIdentifier>, configuration: Configuration,
                               scheme: VersionSelectorScheme, comparator: Comparator<String>): String {
        val versions = moduleVersions.map { matchedVersion(rule, it.version) }.distinct()
        val highestVersion = versions.maxWith(comparator)!!

        val forcedModules = moduleVersions.flatMap { moduleVersion ->
            configuration.resolutionStrategy.forcedModules.filter {
                it.group == moduleVersion.group && it.name == moduleVersion.name
            }
        }

        val forcedDependencies = moduleVersions.flatMap { moduleVersion ->
            configuration.dependencies.filter {
                it is ExternalDependency && it.isForce && it.group == moduleVersion.group && it.name == moduleVersion.name
            }
        }.map {
            DefaultModuleVersionSelector(it.group, it.name, it.version)
        }

        val forced = forcedModules + forcedDependencies

        if (forced.isNotEmpty()) {
            val versionsBySelector = forced
                    .map { it.version }
                    .distinct()
                    .associateBy { scheme.parseSelector(it)!! }
            val static = versionsBySelector.filter { !it.key.isDynamic }
            val dynamic = versionsBySelector.filter { it.key.isDynamic }
            if (static.isNotEmpty()) {
                val staticVersions = static.values
                val forcedVersion = staticVersions.minWith(comparator)!!
                logger.debug("Found force(s) $forced that supersede resolution rule $rule. Will use $forcedVersion instead of $highestVersion")
                return forcedVersion
            } else {
                val selector = dynamic.keys.sortedBy {
                    when (it.javaClass.kotlin) {
                        LatestVersionSelector::class -> 2
                        SubVersionSelector::class -> 1
                        VersionRangeSelector::class -> 0
                        else -> throw IllegalArgumentException("Unknown selector type $it")
                    }
                }.first()
                val forcedVersion = if (selector is LatestVersionSelector) {
                    highestVersion
                } else {
                    versions.filter { selector.accept(it) }.maxWith(comparator)!!
                }
                logger.debug("Found force(s) $forced that supersede resolution rule $rule. Will use highest dynamic version $forcedVersion that matches most selective selector ${dynamic[selector]}")
                return forcedVersion
            }
        }

        return highestVersion
    }

    fun matchedVersion(rule: AlignRule, version: String): String {
        val match = rule.match
        if (match != null) {
            val pattern = rule.matchPattern
            val matcher = pattern.matcher(version)
            if (matcher.find()) {
                return matcher.group()
            } else if (!versionScheme.parseSelector(version).isDynamic) {
                logger.warn("Resolution rule $rule is unable to honor match. $match does not match $version. Will use $version")
            }
        }
        return version
    }
}

enum class VersionMatcher(regex: String) {
    EXCLUDE_SUFFIXES("^(\\d+\\.)?(\\d+\\.)?(\\*|\\d+)");

    val pattern = regex.toRegex().toPattern()
}

data class ExcludeRule(override val module: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : ModuleRule {
    val logger: Logger = Logging.getLogger(ExcludeRule::class.java)

    @Override
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension) {
        val moduleId = ModuleIdentifier.valueOf(module)
        logger.debug("Resolution rule $this excluding ${moduleId.organization}:${moduleId.name}")
        configuration.exclude(moduleId.organization, moduleId.name)
    }
}

class DependencyDeniedException(moduleId: ModuleVersionIdentifier, rule: DenyRule) : Exception("Dependency $moduleId denied by dependency rule: ${rule.reason}")

class SubstituteRuleMissingVersionException(moduleId: ModuleVersionIdentifier, rule: SubstituteRule) : Exception("The dependency to be substituted ($moduleId) must have a version. Invalid rule: $rule")

fun Configuration.exclude(group: String, module: String) {
    exclude(mapOf("group" to group, "module" to module))
}
