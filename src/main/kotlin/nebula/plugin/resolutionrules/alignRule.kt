package nebula.plugin.resolutionrules

import com.netflix.nebula.dependencybase.DependencyManagement
import com.netflix.nebula.interop.*
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.SubVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionRangeSelector
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.util.*
import java.util.regex.Matcher

data class AlignRule(val name: String?,
                     val group: Regex,
                     val includes: List<Regex>,
                     val excludes: List<Regex>,
                     val match: String?,
                     override var ruleSet: String?,
                     override val reason: String,
                     override val author: String,
                     override val date: String) : BasicRule {
    private var groupMatcher: Matcher? = null
    private lateinit var includesMatchers: List<Matcher>
    private lateinit var excludesMatchers: List<Matcher>

    override fun apply(project: Project,
                       configuration: Configuration,
                       resolutionStrategy: ResolutionStrategy,
                       extension: NebulaResolutionRulesExtension,
                       insight: DependencyManagement) {
        throw UnsupportedOperationException("Align rules are not applied directly")
    }

    fun ruleMatches(dep: ModuleVersionIdentifier) = ruleMatches(dep.group, dep.name)

    fun ruleMatches(inputGroup: String, inputName: String): Boolean {
        if (groupMatcher == null) {
            groupMatcher = group.toPattern().matcher(inputGroup)
            includesMatchers = includes.map { it.toPattern().matcher(inputName) }
            excludesMatchers = excludes.map { it.toPattern().matcher(inputName) }
        }

        return groupMatcher!!.matches(inputGroup) &&
                (includes.isEmpty() || includesMatchers.any { it.matches(inputName) }) &&
                (excludes.isEmpty() || excludesMatchers.none { it.matches(inputName) })
    }
}

data class AlignRules(val aligns: List<AlignRule>) : Rule {
    lateinit var insight: DependencyManagement

    companion object {
        val logger: Logger = Logging.getLogger(AlignRules::class.java)

        const val MAX_PASSES = 5
    }

    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, insight: DependencyManagement) {
        this.insight = insight
        if (configuration.isCopy) {
            // Don't attempt to align one of our copied configurations
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
                .copyConfiguration(configuration)
                .baselineAligns()

        if (baselineAligns.isEmpty()) {
            logger.debug("Short-circuiting alignment for $configuration - No align rules matched the configured configurations")
            return
        }

        val stableAligns = project
                .copyConfiguration(configuration)
                .applyAligns(baselineAligns)
                .stableResolvedAligns(baselineAligns)

        configuration.applyAligns(stableAligns, true)
    }

    private fun CopiedConfiguration.baselineAligns(): List<AlignedVersionWithDependencies> =
            selectedVersions({ it.selectedModuleVersion })
                    .filter {
                        val resolvedVersions = it.resolvedDependencies
                                .mapToSet { it.selectedVersion }
                        resolvedVersions.size > 1 || resolvedVersions.single() != it.alignedVersion.version.stringVersion
                    }

    private fun CopiedConfiguration.resolvedAligns(baselineAligns: List<AlignedVersionWithDependencies>) =
            selectedVersions({ dependency ->
                val selectedModuleVersion = dependency.selectedModuleVersion
                val alignedVersion = baselineAligns.singleOrNull {
                    it.ruleMatches(selectedModuleVersion)
                }
                if (alignedVersion.useRequestedVersion(selectedModuleVersion)) {
                    /**
                     * If the selected version for an aligned dependency was unaffected by resolutionStrategies etc.,
                     * then we choose the requested version so we can reflect the requested dependency pre-alignment.
                     *
                     * We ignore dynamic selectors, which would be a problem when an aligned dependency brings in a
                     * dynamic selector - but we'll have to live with that very small chance of inconsistency while
                     * we have to do alignment like this...
                     */
                    // FIXME the requested version is the last seen, not the one that contributed the highest version
                    val selector = dependency.requested as ModuleComponentSelector
                    if (VersionWithSelector(selector.version).asSelector().isDynamic) {
                        selectedModuleVersion
                    } else {
                        DefaultModuleVersionIdentifier(selector.group, selector.module, selector.version)
                    }
                } else {
                    selectedModuleVersion
                }
            })

    tailrec private fun CopiedConfiguration.stableResolvedAligns(baselineAligns: List<AlignedVersionWithDependencies>, pass: Int = 1): List<AlignedVersionWithDependencies> {
        check(pass <= MAX_PASSES) {
            "The maximum number of alignment passes were attempted ($MAX_PASSES) for $source"
        }
        val resolvedAligns = resolvedAligns(baselineAligns)
        val copy = copyConfiguration().applyAligns(resolvedAligns)
        val copyResolvedAligns = copy.resolvedAligns(baselineAligns)
        return if (resolvedAligns != copyResolvedAligns) {
            copy.stableResolvedAligns(baselineAligns, pass.inc())
        } else {
            resolvedAligns
        }
    }

    private fun AlignedVersionWithDependencies?.useRequestedVersion(moduleVersion: ModuleVersionIdentifier): Boolean {
        if (this == null) {
            return false
        }
        val dependency = resolvedDependencies
                .map { it.selected }
                .singleOrNull { it.moduleVersion?.module == moduleVersion.module } ?: return true
        val selectionReason = dependency.selectionReason
        return selectionReason.isExpected || selectionReason.isConflictResolution
    }

    private fun AlignedVersionWithDependencies.ruleMatches(dep: ModuleVersionIdentifier) = alignedVersion.rule.ruleMatches(dep)

    private fun CopiedConfiguration.selectedVersions(versionSelector: (ResolvedDependencyResult) -> ModuleVersionIdentifier): List<AlignedVersionWithDependencies> {
        val partitioned = incoming.resolutionResult.allDependencies
                .partition { it is ResolvedDependencyResult }
        @Suppress("UNCHECKED_CAST")
        val resolved = partitioned.first as List<ResolvedDependencyResult>
        val unresolved = partitioned.second

        if (unresolved.isNotEmpty()) {
            logger.warn("Resolution rules could not resolve all dependencies to align $source. This configuration will not be aligned (use --info to list unresolved dependencies)")
            logger.info("Could not resolve:\n ${unresolved.distinct().joinToString("\n") { " - $it" }}")
            return emptyList()
        }
        val resolvedDependencies = resolved
                .filter { it.selectedId is ModuleComponentIdentifier }
        val resolvedVersions = resolvedDependencies
                .map(versionSelector)
                .distinct()

        val selectedVersions = ArrayList<AlignedVersionWithDependencies>()
        aligns.forEach { align ->
            val matches = resolvedVersions.filter { dep: ModuleVersionIdentifier -> align.ruleMatches(dep) }
            if (matches.isNotEmpty()) {
                val version = alignedRange(align, matches, this)
                selectedVersions += AlignedVersion(align, version).addResolvedDependencies(resolvedDependencies)
            }
        }
        return selectedVersions
    }

    private data class AlignedVersion(val rule: AlignRule, val version: VersionWithSelector)

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

    private fun Configuration.applyAligns(alignedVersionsWithDependencies: List<AlignedVersionWithDependencies>, finalConfiguration: Boolean = false): Configuration {
        alignedVersionsWithDependencies.map { it.alignedVersion }.let {
            resolutionStrategy.eachDependency(ApplyAlignsAction(this, it, finalConfiguration))
        }
        return this
    }

    private inner class ApplyAlignsAction(val configuration: Configuration, val alignedVersions: List<AlignedVersion>, val finalConfiguration: Boolean) : Action<DependencyResolveDetails> {
        override fun execute(details: DependencyResolveDetails) {
            val target = details.target
            val alignedVersion = alignedVersions.firstOrNull {
                it.rule.ruleMatches(target.group, target.name)
            }
            if (alignedVersion != null) {
                val (rule, version) = alignedVersion
                if (version.stringVersion != details.target.version) {
                    if (finalConfiguration) {
                        logger.debug("Resolution rule $rule aligning ${details.requested.group}:${details.requested.name} to $version")
                        insight.addReason(configuration, "${details.requested.group}:${details.requested.name}", "aligned to $version by ${rule.ruleSet}")
                    }
                    details.useVersion("(,$version]")
                }
            }
        }
    }

    private fun alignedRange(rule: AlignRule, moduleVersions: List<ModuleVersionIdentifier>, configuration: Configuration): VersionWithSelector {
        val versions = moduleVersions.mapToSet { VersionWithSelector(it.version) }
        check(versions.all { !it.asSelector().isDynamic }) { "A dynamic version was included in $versions for $rule" }
        val highestVersion = versions.max()!!

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
            DefaultModuleVersionSelector.newSelector(it.group, it.name, it.version)
        }

        val forced = forcedModules + forcedDependencies
        if (forced.isNotEmpty()) {
            val (dynamic, static) = forced
                    .mapToSet { VersionWithSelector(it.version) }
                    .partition { it.asSelector().isDynamic }
            return if (static.isNotEmpty()) {
                val forcedVersion = static.min()!!
                logger.debug("Found force(s) $forced that supersede resolution rule $rule. Will use $forcedVersion")
                forcedVersion
            } else {
                val mostSpecific = dynamic.minBy {
                    when (it.asSelector().javaClass.kotlin) {
                        LatestVersionSelector::class -> 2
                        SubVersionSelector::class -> 1
                        VersionRangeSelector::class -> 0
                        else -> throw IllegalArgumentException("Unknown selector type $it")
                    }
                }!!
                val forcedVersion = if (mostSpecific.asSelector() is LatestVersionSelector) {
                    highestVersion
                } else {
                    versions.filter { mostSpecific.asSelector().accept(it.stringVersion) }.max()!!
                }
                logger.debug("Found force(s) $forced that supersede resolution rule $rule. Will use highest dynamic version $forcedVersion that matches most specific selector $mostSpecific")
                forcedVersion
            }
        }

        return highestVersion
    }
}
