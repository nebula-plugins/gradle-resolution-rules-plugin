package nebula.plugin.resolutionrules

import com.netflix.nebula.dependencybase.DependencyManagement
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.Versioned
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.*
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

data class AlignRule(val name: String?,
                     val group: Regex,
                     val includes: List<Regex>,
                     val excludes: List<Regex>,
                     val match: String?,
                     override var ruleSet: String?,
                     override val reason: String,
                     override val author: String,
                     override val date: String) : BasicRule {

    companion object {
        val logger: Logger = Logging.getLogger(AlignRules::class.java)
    }

    private var groupMatcher: Matcher? = null
    private lateinit var includesMatchers: List<Matcher>
    private lateinit var excludesMatchers: List<Matcher>

    private val matchPattern: Pattern by lazy {
        val isBuiltIn = VersionMatchers.values().any { it.name == match }
        if (isBuiltIn) VersionMatchers.valueOf(match!!).pattern else Pattern.compile(match)
    }
    private var matchMatcher: Matcher? = null

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

    fun matchedVersion(version: String): String {
        if (match != null) {
            val matcher = if (matchMatcher == null) {
                matchMatcher = matchPattern.matcher(version)
                matchMatcher!!
            } else matchMatcher!!.reset(version)
            if (matcher.find()) {
                return matcher.group()
            } else if (!VERSION_SCHEME.parseSelector(version).isDynamic) {
                logger.warn("Resolution rule $this is unable to honor match. $match does not match $version. Will use $version")
            }
        }
        return version
    }

    private enum class VersionMatchers(regex: String) {
        EXCLUDE_SUFFIXES("^(\\d+\\.)?(\\d+\\.)?(\\*|\\d+)");

        val pattern = regex.toRegex().toPattern()
    }
}

data class AlignRules(val aligns: List<AlignRule>) : Rule {
    lateinit var insight: DependencyManagement

    companion object {
        val logger: Logger = Logging.getLogger(AlignRules::class.java)

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
                                .mapToSet { it.selectedVersion }
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
        val partitioned = incoming.resolutionResult.allDependencies
                .partition { it is ResolvedDependencyResult }
        @Suppress("UNCHECKED_CAST")
        val resolved = partitioned.first as List<ResolvedDependencyResult>
        val unresolved = partitioned.second

        if (shouldLog && unresolved.isNotEmpty()) {
            logger.warn("Resolution rules could not resolve all dependencies to align $source, those dependencies will not be used during alignment (use --info to list unresolved dependencies)")
            logger.info("Could not resolve:\n ${unresolved.distinct().map { " - $it" }.joinToString("\n")}")
        }
        val resolvedDependencies = resolved
                .filter { it.selectedId is ModuleComponentIdentifier }
                .distinctBy { it.selectedModule }
        val resolvedVersions = resolvedDependencies.map(versionSelector)

        val selectedVersions = ArrayList<AlignedVersionWithDependencies>()
        aligns.forEach { align ->
            val matches = resolvedVersions.filter { dep: ModuleVersionIdentifier -> align.ruleMatches(dep) }
            if (matches.isNotEmpty()) {
                val alignedVersion = alignedVersion(align, matches, this)
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
        alignedVersionsWithDependencies.map { it.alignedVersion }.let {
            resolutionStrategy.eachDependency(ApplyAlignsAction(this, it, shouldLog))
        }
        return this
    }

    private inner class ApplyAlignsAction(val configuration: Configuration, val alignedVersions: List<AlignedVersion>, val shouldLog: Boolean) : Action<DependencyResolveDetails> {
        override fun execute(details: DependencyResolveDetails) {
            val target = details.target
            val alignedVersion = alignedVersions.firstOrNull {
                it.rule.ruleMatches(target.group, target.name)
            }
            if (alignedVersion != null) {
                val (rule, version) = alignedVersion
                if (version != rule.matchedVersion(details.target.version)) {
                    if (shouldLog) {
                        logger.debug("Resolution rule $rule aligning ${details.requested.group}:${details.requested.name} to $version")
                    }
                    details.useVersion(version)
                    insight.addReason(configuration.name, "${details.requested.group}:${details.requested.name}", "aligned to $version by ${rule.name}", "nebula.resolution-rules")
                }
            }
        }
    }

    private fun alignedVersion(rule: AlignRule, moduleVersions: List<ModuleVersionIdentifier>, configuration: Configuration): String {
        val versions = moduleVersions.mapToSet { rule.matchedVersion(it.version) }
        val highestVersion = versions.maxWith(STRING_VERSION_COMPARATOR)!!

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
            val (dynamic, static) = forced
                    .mapToSet { VersionWithSelector(it.version) }
                    .partition { it.selector.isDynamic }
            if (static.isNotEmpty()) {
                val forcedVersion = static.minWith(VERSION_COMPARATOR)!!
                logger.debug("Found force(s) $forced that supersede resolution rule $rule. Will use $forcedVersion instead of $highestVersion")
                return forcedVersion.stringVersion
            } else {
                val mostSpecific = dynamic.minBy {
                    when (it.selector.javaClass.kotlin) {
                        LatestVersionSelector::class -> 2
                        SubVersionSelector::class -> 1
                        VersionRangeSelector::class -> 0
                        else -> throw IllegalArgumentException("Unknown selector type $it")
                    }
                }!!
                val forcedVersion = if (mostSpecific.selector is LatestVersionSelector) {
                    highestVersion
                } else {
                    versions.filter { mostSpecific.selector.accept(it) }.maxWith(STRING_VERSION_COMPARATOR)!!
                }
                logger.debug("Found force(s) $forced that supersede resolution rule $rule. Will use highest dynamic version $forcedVersion that matches most specific selector $mostSpecific")
                return forcedVersion
            }
        }

        return highestVersion
    }

    data class VersionWithSelector(val stringVersion: String): Versioned {
        override fun getVersion(): String =
                stringVersion

        val selector: VersionSelector by lazy {
            VERSION_SCHEME.parseSelector(stringVersion)!!
        }

        override fun toString(): String =
                stringVersion
    }
}

