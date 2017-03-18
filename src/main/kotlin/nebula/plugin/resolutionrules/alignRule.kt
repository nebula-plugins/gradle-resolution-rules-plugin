package nebula.plugin.resolutionrules

import com.netflix.nebula.dependencybase.DependencyManagement
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
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.SubVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionRangeSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.util.*
import java.util.regex.Pattern

data class AlignRule(val name: String?, val group: Regex, val includes: List<Regex>, val excludes: List<Regex>, val match: String?, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : BasicRule {
    val matchPattern: Pattern by lazy {
        if (VersionMatcher.values().filter { it.name == match }.isNotEmpty()) VersionMatcher.valueOf(match!!).pattern else Pattern.compile(match)
    }

    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, insight: DependencyManagement) {
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
    lateinit var insight: DependencyManagement

    companion object {
        const val CONFIG_SUFFIX = "Align"
    }

    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, insight: DependencyManagement) {
        this.insight = insight
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

    private fun CopiedConfiguration.baselineAligns(): List<AlignedVersionWithDependencies> =
            selectedVersions({ it.selectedModuleVersion }, true)
                    .filter {
                        val resolvedVersions = it.resolvedDependencies
                                .map { it.selectedVersion }
                                .distinct()
                        resolvedVersions.size > 1 || resolvedVersions.single() != it.alignedVersion.version
                    }

    private fun CopiedConfiguration.resolvedAligns(baselineAligns: List<AlignedVersionWithDependencies>) =
            selectedVersions({ dependency ->
                val selectedModuleVersion = dependency.selectedModuleVersion
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

    tailrec private fun CopiedConfiguration.stableResolvedAligns(baselineAligns: List<AlignedVersionWithDependencies>, pass: Int = 1): List<AlignedVersionWithDependencies> {
        val resolvedAligns = resolvedAligns(baselineAligns)
        val copy = copyConfiguration("StabilityPass$pass").applyAligns(resolvedAligns)
        val copyResolvedAligns = copy.resolvedAligns(baselineAligns)
        if (resolvedAligns == copyResolvedAligns) {
            return resolvedAligns
        } else {
            return copy.stableResolvedAligns(baselineAligns, pass.inc())
        }
    }

    private fun AlignedVersionWithDependencies?.selectedVersionExpected(moduleVersion: ModuleVersionIdentifier): Boolean {
        if (this == null) {
            return false
        }
        val dependency = resolvedDependencies
                .map { it.selected }
                .singleOrNull { it.moduleVersion.module == moduleVersion.module }
        return dependency == null || dependency.selectionReason.isExpected
    }

    private fun AlignedVersionWithDependencies.ruleMatches(dep: ModuleVersionIdentifier) = alignedVersion.rule.ruleMatches(dep)

    private fun CopiedConfiguration.selectedVersions(versionSelector: (ResolvedDependencyResult) -> ModuleVersionIdentifier, shouldLog: Boolean = false): List<AlignedVersionWithDependencies> {
        val (resolved, unresolved) = incoming.resolutionResult.allDependencies
                .partition { it is ResolvedDependencyResult }
        if (shouldLog && unresolved.isNotEmpty()) {
            logger.warn("Resolution rules could not resolve all dependencies to align $source, those dependencies will not be used during alignment (use --info to list unresolved dependencies)")
            logger.info("Could not resolve:\n ${unresolved.distinct().map { " - $it" }.joinToString("\n")}")
        }
        val resolvedDependencies = resolved
                .map { it as ResolvedDependencyResult }
                .filter { it.selectedId is ModuleComponentIdentifier }
                .distinctBy { it.selectedModule }
        val resolvedVersions = resolvedDependencies.map(versionSelector)

        val selectedVersions = ArrayList<AlignedVersionWithDependencies>()
        aligns.forEach { align ->
            val matches = resolvedVersions.filter { dep: ModuleVersionIdentifier -> align.ruleMatches(dep) }
            if (matches.isNotEmpty()) {
                val alignedVersion = alignedVersion(align, matches, this, versionScheme, versionComparator.asStringComparator())
                selectedVersions += AlignedVersion(align, alignedVersion).addResolvedDependencies(resolvedDependencies)
            }
        }
        return selectedVersions
    }

    private data class AlignedVersion(val rule: AlignRule, val version: String)

    private data class AlignedVersionWithDependencies(val alignedVersion: AlignedVersion) {
        // Non-constructor property to prevent it from being included in equals/hashcode
        lateinit var resolvedDependencies: List<ResolvedDependencyResult>
    }

    private fun AlignedVersion.addResolvedDependencies(resolvedDependencies: List<ResolvedDependencyResult>): AlignedVersionWithDependencies {
        val withDependencies = AlignedVersionWithDependencies(this)
        withDependencies.resolvedDependencies = resolvedDependencies.filter {
            val moduleVersion = it.selectedModuleVersion
            rule.ruleMatches(moduleVersion)
        }
        return withDependencies
    }

    private fun CopiedConfiguration.applyAligns(alignedVersionsWithDependencies: List<AlignedVersionWithDependencies>)
            = (this as Configuration).applyAligns(alignedVersionsWithDependencies) as CopiedConfiguration

    private fun Configuration.applyAligns(alignedVersionsWithDependencies: List<AlignedVersionWithDependencies>, shouldLog: Boolean = false): Configuration {
        val alignedVersions = alignedVersionsWithDependencies.map { it.alignedVersion }
        resolutionStrategy.eachDependency { details ->
            val target = details.target
            val alignedVersion = alignedVersions.firstOrNull {
                it.rule.ruleMatches(target.group, target.name)
            }
            if (alignedVersion != null) {
                val (rule, version) = alignedVersion
                if (version != matchedVersion(rule, details.target.version)) {
                    if (shouldLog) {
                        logger.debug("Resolution rule $rule aligning ${details.requested.group}:${details.requested.name} to $version")
                    }
                    details.useVersion(version)
                    insight.addReason(this.name, "${details.requested.group}:${details.requested.name}", "aligned to $version by ${rule.name}", "nebula.resolution-rules")
                }
            }
        }
        return this
    }

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
