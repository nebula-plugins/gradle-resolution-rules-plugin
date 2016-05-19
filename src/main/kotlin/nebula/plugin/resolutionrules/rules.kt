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

import com.fasterxml.jackson.databind.JsonDeserializer
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.specs.Specs
import java.util.*
import java.util.regex.Pattern

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

fun RuleSet.withName(ruleSetName: String) : RuleSet {
    name = ruleSetName
    listOf(replace, substitute, reject, deny, exclude, align).flatten().forEach { it.ruleSet = ruleSetName }
    return this
}

fun List<RuleSet>.flatten() = RuleSet(
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
        val withSelector = substitution.module(withModuleId.toString())
        resolutionStrategy.dependencySubstitution {
            it.substitute(selector).with(withSelector)
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

data class AlignRule(val name: String?, val group: String, val includes: List<String>, val excludes: List<String>, val match: String?, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : BasicRule {
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension) {
        throw UnsupportedOperationException("Align rules are not applied directly")
    }

    fun resolvedMatches(dep: ResolvedModuleVersion) = ruleMatches(dep.id.group, dep.id.name)

    fun dependencyMatches(details: DependencyResolveDetails) = ruleMatches(details.requested.group, details.requested.name)

    fun ruleMatches(inputGroup: String, inputName: String): Boolean {
        val matchedIncludes = includes.filter { inputName.matches(it.toRegex()) }
        val matchedExcludes = excludes.filter { inputName.matches(it.toRegex()) }
        return inputGroup.matches(group.toRegex()) &&
                (includes.isEmpty() || !matchedIncludes.isEmpty()) &&
                (excludes.isEmpty() || matchedExcludes.isEmpty())
    }
}

data class AlignRules(val aligns: List<AlignRule>) : Rule {
    val logger: Logger = Logging.getLogger(AlignRules::class.java)

    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension) {
        if (aligns.size == 0) { // don't do extra resolves if there are no align rules
            return
        }

        val copy = configuration.copyRecursive()
        // Hacky workaround to prevent Gradle from attempting to resolve a project dependency as an external dependency
        copy.exclude(project.group.toString(), project.name)
        val resolvedConfiguration = copy.resolvedConfiguration
        val artifacts = if (resolvedConfiguration.hasError()) {
            val lenientConfiguration = resolvedConfiguration.lenientConfiguration
            logger.info("Resolution rules could not resolve all dependencies to align in configuration '${configuration.name}' should also fail to resolve")
            lenientConfiguration.getArtifacts(Specs.SATISFIES_ALL)
        } else {
            resolvedConfiguration.resolvedArtifacts
        }

        val moduleVersions = artifacts.filter {
            // Exclude project artifacts from alignment
            it.id.componentIdentifier !is ProjectComponentIdentifier
        }.map { it.moduleVersion }


        val comparator = DefaultVersionComparator()
        val scheme = DefaultVersionSelectorScheme(comparator)
        val selectedVersion = LinkedHashMap<AlignRule, String>()
        aligns.forEach { align ->
            val matches = moduleVersions.filter { dep: ResolvedModuleVersion -> align.resolvedMatches(dep) }
            if (matches.isNotEmpty()) {
                selectedVersion[align] = alignedVersion(align, matches, configuration, scheme, comparator.asStringComparator())
            }
        }

        resolutionStrategy.eachDependency { details ->
            val foundMatch = selectedVersion.filter { it.key.dependencyMatches(details) }
            if (foundMatch.isNotEmpty()) {
                val (rule, version) = foundMatch.entries.first()
                if (version != matchedVersion(rule, details.requested.version)) {
                    logger.info("Resolution rule $rule aligning ${details.requested.group}:${details.requested.name} to $version")
                    details.useVersion(version)
                }
            }
        }
    }

    fun alignedVersion(rule: AlignRule, moduleVersions: List<ResolvedModuleVersion>, configuration: Configuration,
                       scheme: VersionSelectorScheme, comparator: Comparator<String>): String {
        val versions = moduleVersions.map { matchedVersion(rule, it.id.version) }.distinct()
        val highestVersion = versions.maxWith(comparator)!!

        val forced = moduleVersions.flatMap { moduleVersion ->
            configuration.resolutionStrategy.forcedModules.filter {
                val id = moduleVersion.id
                it.group == id.group && it.name == id.name
            }
        }
        if (forced.isNotEmpty()) {
            val forcedVersions = forced.map { it.version }.distinct()
            val (dynamicVersions, staticVersions) = forcedVersions.partition { version ->
                val selector = scheme.parseSelector(version)!!
                selector.isDynamic
            }
            if (!dynamicVersions.isEmpty()) {
                logger.warn("Resolution rule $rule is unable to honor forced versions $dynamicVersions. For a force to take precedence on an align rule, it must use a static version")
            }
            if (!staticVersions.isEmpty()) {
                val forcedVersion = staticVersions.minWith(comparator)!!
                logger.info("Found force(s) $forced that supersede resolution rule $rule. Will use $forcedVersion instead of $highestVersion")
                return forcedVersion
            } else {
                logger.warn("No static forces found for rule $rule. Falling back to default alignment logic")
            }
        }
        return highestVersion
    }

    fun matchedVersion(rule: AlignRule, version: String): String {
        val match = rule.match
        if (match != null) {
            val pattern = if (VersionMatcher.values().filter { it.name == match }.isNotEmpty()) VersionMatcher.valueOf(match).pattern else Pattern.compile(match)
            val matcher = pattern.matcher(version)
            if (matcher.find()) {
                return matcher.group()
            } else {
                logger.warn("Resolution rule $rule is unable to honor match. $match does not match $version. Will use $version")
            }
        }
        return version
    }

    enum class VersionMatcher(val regex: String) {
        EXCLUDE_SUFFIXES("^(\\d+\\.)?(\\d+\\.)?(\\*|\\d+)");

        val pattern = regex.toRegex().toPattern()
    }
}

data class ExcludeRule(override val module: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : ModuleRule {
    val logger: Logger = Logging.getLogger(ExcludeRule::class.java)

    @Override
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension) {
        val moduleId = ModuleIdentifier.valueOf(module)
        logger.info("Resolution rule $this excluding ${moduleId.organization}:${moduleId.name}")
        configuration.exclude(moduleId.organization, moduleId.name)
    }
}

class DependencyDeniedException(moduleId: ModuleVersionIdentifier, rule: DenyRule) : Exception("Dependency $moduleId denied by dependency rule: ${rule.reason}")

class SubstituteRuleMissingVersionException(moduleId: ModuleVersionIdentifier, rule: SubstituteRule) : Exception("The dependency to be substituted ($moduleId) must have a version. Invalid rule: $rule")

fun Configuration.exclude(group: String, module: String) {
    exclude(mapOf("group" to group, "module" to module))
}