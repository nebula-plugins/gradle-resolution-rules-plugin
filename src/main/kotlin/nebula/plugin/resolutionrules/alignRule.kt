package nebula.plugin.resolutionrules

import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.ReusableAction
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern
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

    private val groupPattern = group.toPattern()
    private val includesPatterns = includes.map { it.toPattern() }
    private val excludesPatterns = excludes.map { it.toPattern() }
    private val alignMatchers = ConcurrentHashMap<Thread, AlignMatcher>()

    override fun apply(project: Project,
                       configuration: Configuration,
                       resolutionStrategy: ResolutionStrategy,
                       extension: NebulaResolutionRulesExtension) {
        //TODO this rule is applied repeatedly for each configuration. Ideally it should be taken out and
        //applied only once per project
        if (configuration.name == "compileClasspath") { // This is one way to ensure it'll be run for only one configuration
            project.dependencies.components.all(AlignedPlatformMetadataRule::class.java) {
                it.params(this)
            }
        }
    }

    fun ruleMatches(dep: ModuleVersionIdentifier) = ruleMatches(dep.group, dep.name)

    fun ruleMatches(group: String, name: String) = alignMatchers.computeIfAbsent(Thread.currentThread()) {
        AlignMatcher(this, groupPattern, includesPatterns, excludesPatterns)
    }.matches(group, name)
}

class AlignMatcher(val rule: AlignRule, groupPattern: Pattern, includesPatterns: List<Pattern>, excludesPatterns: List<Pattern>) {
    private val groupMatcher = groupPattern.matcher("")
    private val includeMatchers = includesPatterns.map { it.matcher("") }
    private val excludeMatchers = excludesPatterns.map { it.matcher("") }

    private fun Matcher.matches(input: String, type: String): Boolean {
        reset(input)
        return try {
            matches()
        } catch (e: Exception) {
            throw java.lang.IllegalArgumentException("Failed to use matcher '$this' from type '$type' to match '$input'\n" +
                    "Rule: $rule", e)
        }
    }

    fun matches(group: String, name: String): Boolean {
        return groupMatcher.matches(group, "group") &&
                (includeMatchers.isEmpty() || includeMatchers.any { it.matches(name, "includes") }) &&
                (excludeMatchers.isEmpty() || excludeMatchers.none { it.matches(name, "excludes") })
    }
}

@CacheableRule
open class AlignedPlatformMetadataRule @Inject constructor(val rule: AlignRule) : ComponentMetadataRule, Serializable, ReusableAction {
    private val logger: Logger = Logging.getLogger(AlignedPlatformMetadataRule::class.java)

    override fun execute(componentMetadataContext: ComponentMetadataContext?) {
        modifyDetails(componentMetadataContext!!.details)
    }

    fun modifyDetails(details: ComponentMetadataDetails) {
        if (rule.ruleMatches(details.id)) {
            details.belongsTo("aligned-platform:${rule.belongsToName}:${details.id.version}")
            logger.debug("Aligning platform based on '${details.id.group}:${details.id.name}:${details.id.version}' from align rule with group '${rule.group}'")
        }
    }
}
