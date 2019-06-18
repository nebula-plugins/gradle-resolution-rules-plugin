package nebula.plugin.resolutionrules

import com.netflix.nebula.interop.VersionWithSelector
import com.netflix.nebula.interop.selectedId
import com.netflix.nebula.interop.selectedModuleVersion
import com.netflix.nebula.interop.selectedVersion
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.SubVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionRangeSelector
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.Serializable
import java.util.*
import java.util.regex.Matcher
import javax.inject.Inject

data class AlignRule(val name: String?,
                     val group: Regex,
                     val includes: List<Regex> = emptyList(),
                     val excludes: List<Regex> = emptyList(),
                     val match: String?,
                     override var ruleSet: String?,
                     override val reason: String,
                     override val author: String,
                     override val date: String,
                     var belongsToName: String?) : BasicRule, Serializable {

    private var groupMatcher: Matcher? = null
    private lateinit var includesMatchers: List<Matcher>
    private lateinit var excludesMatchers: List<Matcher>

    override fun apply(project: Project,
                       configuration: Configuration,
                       resolutionStrategy: ResolutionStrategy,
                       extension: NebulaResolutionRulesExtension,
                       reasons: MutableSet<String>) {
        //TODO this rule is applied repeatedly for each configuration. Ideally it should be taken out and
        //applied only once per project
        project.dependencies.components.all(AlignedPlatformMetadataRule::class.java) {
            it.params(this)
        }
    }

    fun ruleMatches(dep: ModuleVersionIdentifier) = ruleMatches(dep.group, dep.name)

    fun ruleMatches(inputGroup: String, inputName: String): Boolean {
        groupMatcher = safelyCreatesMatcher(group, inputGroup, "group")
        includesMatchers = includes.map { safelyCreatesMatcher(it, inputName, "includes") }
        excludesMatchers = excludes.map { safelyCreatesMatcher(it, inputName, "excludes") }


        return safelyMatches(groupMatcher!!, inputGroup, "group") &&
                (includes.isEmpty() || includesMatchers.any { safelyMatches(it, inputName, "includes") }) &&
                (excludes.isEmpty() || excludesMatchers.none { safelyMatches(it, inputName, "excludes") })
    }

    private fun safelyCreatesMatcher(it: Regex, input: String, type: String): Matcher {
        return try {
            it.toPattern().matcher(input)
        } catch (e: Exception) {
            throw java.lang.IllegalArgumentException("Failed to use regex '$it' from type '$type' to create matcher for '$input'\n" +
                    "Rule: ${this}")
        }
    }

    private fun safelyMatches(it: Matcher, input: String, type: String): Boolean {
        return try {
            it.matches()
        } catch (e: Exception) {
            throw java.lang.IllegalArgumentException("Failed to use matcher '$it' from type '$type' to match '$input'\n" +
                    "Rule: ${this}")
        }
    }
}

//@CacheableRule TODO: this is disable to test RealisedMavenModuleResolveMetadataSerializationHelper duplicate objects
open class AlignedPlatformMetadataRule : ComponentMetadataRule {
    val rule: AlignRule

    @Inject
    constructor(rule: AlignRule) {
        this.rule = rule
    }

    override fun execute(componentMetadataContext: ComponentMetadataContext?) {
        modifyDetails(componentMetadataContext!!.details)
    }

    fun modifyDetails(details: ComponentMetadataDetails) {
        if (rule.ruleMatches(details.id)) {
            details.belongsTo("aligned-platform:${rule.belongsToName}:${details.id.version}")
        }
    }
}

data class AlignRules(val aligns: List<AlignRule>) : Rule {
    lateinit var reasons: MutableSet<String>

    companion object {
        val logger: Logger = Logging.getLogger(AlignRules::class.java)

        const val MAX_PASSES = 5
    }

    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, reasons: MutableSet<String>) {
        this.reasons = reasons
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
                if (alignedVersion.useRequestedVersion(dependency)) {
                    /**
                     * If the selected version for an aligned dependency was unaffected by resolutionStrategies etc.,
                     * then we choose the requested version so we can reflect the requested dependency pre-alignment.
                     *
                     * We ignore dynamic selectors, which would be a problem when an aligned dependency brings in a
                     * dynamic selector - but we'll have to live with that very small chance of inconsistency while
                     * we have to do alignment like this...
                     */
                    // FIXME the requested version is the last seen, not the one that contributed the highest version
                    val selector = dependency.requested
                    if (selector is ModuleComponentSelector) {
                        if (VersionWithSelector(selector.version).asSelector().isDynamic) {
                            selectedModuleVersion
                        } else {
                            DefaultModuleVersionIdentifier.newId(selector.group, selector.module, selector.version)
                        }
                    } else {
                        selectedModuleVersion
                    }
                } else {
                    selectedModuleVersion
                }
            })

    private tailrec fun CopiedConfiguration.stableResolvedAligns(baselineAligns: List<AlignedVersionWithDependencies>, pass: Int = 1): List<AlignedVersionWithDependencies> {
        check(pass <= MAX_PASSES) {
            "The maximum number of alignment passes were attempted ($MAX_PASSES) for $source"
        }
        val resolvedAligns = resolvedAligns(baselineAligns)
        val copy = copyConfiguration().applyAligns(resolvedAligns)
        val copyResolvedAligns = copy.resolvedAligns(baselineAligns)
        return when {
            resolvedAligns.isEmpty() -> copyResolvedAligns // Alignment caused the configuration to be unresolvable, apply the broken alignments so that failures bubble up
            resolvedAligns != copyResolvedAligns -> copy.stableResolvedAligns(baselineAligns, pass.inc())
            else -> resolvedAligns
        }
    }

    private fun AlignedVersionWithDependencies?.useRequestedVersion(newRoundDependency: ResolvedDependencyResult): Boolean {
        if (this == null) {
            return false
        }
        //resolved dependencies contain the same dependency multiple times based on who brought it when we compare it
        //with a newly resolved dependency from the next alignment run we need to much the same from
        val dependency = resolvedDependencies
                .filter { it.from.moduleVersion?.module == newRoundDependency.from.moduleVersion?.module }
                .map { it.selected }
                .singleOrNull { it.moduleVersion?.module == newRoundDependency.selectedModuleVersion.module }
                ?: return true
        val selectionReason = dependency.selectionReason
        return selectionReason
                .descriptions
                .map { it.cause }
                .all { it == ComponentSelectionCause.REQUESTED || it == ComponentSelectionCause.CONFLICT_RESOLUTION }
    }

    private fun AlignedVersionWithDependencies.ruleMatches(dep: ModuleVersionIdentifier) = alignedVersion.rule.ruleMatches(dep)

    @Suppress("UNCHECKED_CAST")
    private fun CopiedConfiguration.selectedVersions(versionSelector: (ResolvedDependencyResult) -> ModuleVersionIdentifier): List<AlignedVersionWithDependencies> {
        val partitioned = incoming.resolutionResult.allDependencies
                .partition { it is ResolvedDependencyResult }
        val resolved = partitioned.first as List<ResolvedDependencyResult>
        val unresolved = partitioned.second as List<UnresolvedDependencyResult>

        if (unresolved.isNotEmpty()) {
            val unresolvedDetails = unresolved.distinct().joinToString("\n") { " - $it" }
            val message = "Resolution rules could not resolve all dependencies to align $source:\n$unresolvedDetails"
            logger.error(message)
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

    private fun CopiedConfiguration.applyAligns(alignedVersionsWithDependencies: List<AlignedVersionWithDependencies>) = (this as Configuration).applyAligns(alignedVersionsWithDependencies) as CopiedConfiguration

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
                    }
                    details.because("aligned to $version by ${rule.ruleSet}\n" +
                            "\twith reasons: ${reasons.joinToString()}")
                            .useVersion("(,$version]")
                }
            }
        }
    }

    private fun alignedRange(rule: AlignRule, moduleVersions: List<ModuleVersionIdentifier>, configuration: Configuration): VersionWithSelector {
        try {
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
                val moduleIdentifier = DefaultModuleIdentifier.newId(it.group, it.name)
                DefaultModuleVersionSelector.newSelector(moduleIdentifier, it.version)
            }

            val forced = forcedModules + forcedDependencies
            if (forced.isNotEmpty()) {
                val (dynamic, static) = forced
                        .mapToSet { VersionWithSelector(it.version!!) }
                        .partition { it.asSelector().isDynamic }
                return if (static.isNotEmpty()) {
                    val forcedVersion = static.min()!!
                    logger.debug("Found force(s) $forced that supersede resolution rule $rule. Will use $forcedVersion") // FIXME: What about locks?
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
                    logger.debug("Found force(s) $forced that supersede resolution rule $rule. Will use highest dynamic version $forcedVersion that matches most specific selector $mostSpecific") // FIXME: What about locks?
                    forcedVersion
                }
            }

            return highestVersion
        } catch (e: Exception) {
            throw GradleException("Could not apply alignment rule ${rule.name} | Reason: ${e.message}", e)
        }
    }
}
