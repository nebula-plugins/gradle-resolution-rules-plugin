package nebula.plugin.resolutionrules

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.SubVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionRangeSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.internal.collections.CollectionEventRegister
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.util.Path
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.*
import java.util.regex.Pattern

data class AlignRule(val name: String?, val group: Regex, val includes: List<Regex>, val excludes: List<Regex>, val match: String?, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : BasicRule {
    val matchPattern: Pattern by lazy {
        if (VersionMatcher.values().filter { it.name == match }.isNotEmpty()) VersionMatcher.valueOf(match!!).pattern else Pattern.compile(match)
    }

    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension) {
        throw UnsupportedOperationException("Align rules are not applied directly")
    }

    fun ruleMatches(dep: ModuleVersionIdentifier) = ruleMatches(dep.group, dep.name)

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
        if (configuration.name.endsWith(CONFIG_SUFFIX)) {
            // Don't attempt to align an alignment configuration
            return
        }

        if (aligns.isEmpty()) {
            logger.debug("Skipping alignment for $configuration - No alignment rules are configured")
            return
        }

        if (!configuration.isTransitive) {
            logger.debug("Skipping alignment for $configuration - Configuration is not transitive")
            return
        }

        val baselineAligns = project
                .copyConfiguration(configuration, configuration.name, "Baseline")
                .baselineAligns()

        if (baselineAligns.isEmpty()) {
            logger.debug("Short-circuiting alignment for $configuration - No align rules matched the configured configurations")
            return
        }

        val stableAligns = project
                .copyConfiguration(configuration, configuration.name, "Resolved")
                .applyAligns(baselineAligns)
                .stableResolvedAligns(baselineAligns)

        configuration.applyAligns(stableAligns, true)
    }

    private fun CopiedConfiguration.baselineAligns() = selectedVersions({ it.selected.moduleVersion }, true)

    tailrec private fun CopiedConfiguration.stableResolvedAligns(baselineAligns: List<AlignedVersion>, pass: Int = 1): List<AlignedVersion> {
        val resolvedAligns = resolvedAligns(baselineAligns)
        val copy = copyConfiguration("StabilityPass$pass").applyAligns(resolvedAligns)
        val copyResolvedAligns = copy.resolvedAligns(baselineAligns)
        if (resolvedAligns == copyResolvedAligns) {
            return resolvedAligns
        } else {
            return copy.stableResolvedAligns(baselineAligns, pass.inc())
        }
    }

    private fun CopiedConfiguration.resolvedAligns(baselineAligns: List<AlignedVersion>) = selectedVersions({ dependency ->
        val selectedModuleVersion = dependency.selected.moduleVersion
        val alignedVersion = baselineAligns.singleOrNull {
            it.ruleMatches(selectedModuleVersion)
        }
        if (alignedVersion.selectedVersionExpected(selectedModuleVersion)) {
            /**
             * If the selected version for an aligned dependency was expected in the baseline pass (i.e. unaffected by
             * resolutionStrategies etc), then we choose the requested version so we can reflect the requested
             * dependency pre-alignment.
             */
            val selector = dependency.requested as ModuleComponentSelector
            DefaultModuleVersionIdentifier(selector.group, selector.module, selector.version)
        } else {
            selectedModuleVersion
        }
    })

    private fun AlignedVersion?.selectedVersionExpected(moduleVersion: ModuleVersionIdentifier): Boolean {
        if (this == null) {
            return false
        }
        val dependency = resolvedDependencies
                .map { it.selected }
                .singleOrNull { it.moduleVersion.module == moduleVersion.module }
        return dependency == null || dependency.selectionReason.isExpected
    }

    private fun AlignedVersion.ruleMatches(dep: ModuleVersionIdentifier) = rule.ruleMatches(dep)

    private fun CopiedConfiguration.selectedVersions(versionSelector: (ResolvedDependencyResult) -> ModuleVersionIdentifier, shouldLog: Boolean = false): List<AlignedVersion> {
        val (resolved, unresolved) = incoming.resolutionResult.allDependencies
                .partition { it is ResolvedDependencyResult }
        if (shouldLog && unresolved.isNotEmpty()) {
            logger.warn("Resolution rules could not resolve all dependencies to align in configuration '${source.name}' should also fail to resolve (use --info to list unresolved dependencies)")
            logger.info("Resolution rules could not resolve:\n ${unresolved.map { " - $it" }.joinToString("\n")}")
        }
        val resolvedDependencies = resolved
                .map { it as ResolvedDependencyResult }
                .filter { it.selected.id is ModuleComponentIdentifier }
                .distinctBy { it.selected.moduleVersion.module }
        val resolvedVersions = resolvedDependencies.map(versionSelector)

        val selectedVersions = ArrayList<AlignedVersion>()
        aligns.forEach { align ->
            val matches = resolvedVersions.filter { dep: ModuleVersionIdentifier -> align.ruleMatches(dep) }
            if (matches.isNotEmpty()) {
                val alignedVersion = alignedVersion(align, matches, this, versionScheme, versionComparator.asStringComparator())
                selectedVersions += AlignedVersion(align, alignedVersion).addResolvedDependencies(resolvedDependencies)
            }
        }
        return selectedVersions
    }

    private data class AlignedVersion(val rule: AlignRule, val version: String) {
        // Non-constructor property to prevent it from being included in equals/hashcode
        lateinit var resolvedDependencies: List<ResolvedDependencyResult>
    }

    private fun AlignedVersion.addResolvedDependencies(resolvedDependencies: List<ResolvedDependencyResult>): AlignedVersion {
        this.resolvedDependencies = resolvedDependencies.filter {
            val moduleVersion = it.selected.moduleVersion
            rule.ruleMatches(moduleVersion)
        }
        return this
    }

    private fun CopiedConfiguration.applyAligns(alignedVersions: List<AlignedVersion>)
            = (this as Configuration).applyAligns(alignedVersions) as CopiedConfiguration

    private fun Configuration.applyAligns(alignedVersions: List<AlignedVersion>, shouldLog: Boolean = false): Configuration {
        resolutionStrategy.eachDependency { details ->
            val target = details.target
            val foundMatch = alignedVersions.filter { it.rule.ruleMatches(target.group, target.name) }
            if (foundMatch.isNotEmpty()) {
                val (rule, version) = foundMatch.first()
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

    private fun alignedVersion(rule: AlignRule, moduleVersions: List<ModuleVersionIdentifier>, configuration: Configuration,
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
