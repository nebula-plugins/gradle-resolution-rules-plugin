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

import com.netflix.nebula.interop.VersionWithSelector
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencySubstitutions
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ExactVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.Serializable

interface Rule : Serializable {
    fun apply(
        project: Project,
        configuration: Configuration,
        resolutionStrategy: ResolutionStrategy,
        extension: NebulaResolutionRulesExtension
    )
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
    val replace: List<ReplaceRule> = emptyList(),
    val substitute: List<SubstituteRule> = emptyList(),
    val reject: List<RejectRule> = emptyList(),
    val deny: List<DenyRule> = emptyList(),
    val exclude: List<ExcludeRule> = emptyList(),
    val align: List<AlignRule> = emptyList()
) : Serializable {

    fun dependencyRulesPartOne() =
        listOf(replace, deny, exclude).flatten() + listOf(SubstituteRules(substitute), RejectRules(reject))

    fun dependencyRulesPartTwo() = listOf(align).flatten()

    fun generateAlignmentBelongsToName() {
        align.forEachIndexed { index, alignRule ->
            var abbreviatedAlignGroup = alignRule.group.toString()
                .replace("|", "-or-")

            val onlyAlphabeticalRegex = Regex("[^A-Za-z.\\-]")
            abbreviatedAlignGroup = onlyAlphabeticalRegex.replace(abbreviatedAlignGroup, "")

            alignRule.belongsToName = "$name-$index-for-$abbreviatedAlignGroup"
        }
    }
}

fun RuleSet.withName(ruleSetName: String): RuleSet {
    name = ruleSetName
    listOf(replace, substitute, reject, deny, exclude, align).flatten().forEach { it.ruleSet = ruleSetName }
    generateAlignmentBelongsToName()
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

data class ReplaceRule(
    override val module: String,
    val with: String,
    override var ruleSet: String?,
    override val reason: String,
    override val author: String,
    override val date: String
) : ModuleRule {
    private val moduleId = module.toModuleId()
    private val withId = with.toModuleId()

    override fun apply(
        project: Project,
        configuration: Configuration,
        resolutionStrategy: ResolutionStrategy,
        extension: NebulaResolutionRulesExtension
    ) {
        project.dependencies.modules.module(moduleId) {
            val details = it as ComponentModuleMetadataDetails
            val message = "replaced $module -> $with because '$reason' by rule $ruleSet"
            details.replacedBy(withId, message)
        }
    }
}

data class SubstituteRule(
    val module: String, val with: String, override var ruleSet: String?,
    override val reason: String, override val author: String, override val date: String
) : BasicRule, Serializable {
    @Transient lateinit var substitutedVersionId: ModuleVersionIdentifier
    @Transient lateinit var withComponentSelector: ModuleComponentSelector
    @Transient lateinit var versionSelector: VersionSelector

    override fun apply(
        project: Project,
        configuration: Configuration,
        resolutionStrategy: ResolutionStrategy,
        extension: NebulaResolutionRulesExtension
    ) {
        throw UnsupportedOperationException("Substitution rules cannot be applied directly and must be applied via SubstituteRules")
    }

    fun isInitialized(): Boolean = this::substitutedVersionId.isInitialized

    fun acceptsVersion(version: String): Boolean {
        return if (substitutedVersionId.version.isNotEmpty()) {
            when (VersionWithSelector(version).asSelector()) {
                is ExactVersionSelector -> versionSelector.accept(version)
                else -> false
            }
        } else true
    }
}

class SubstituteRules(val rules: List<SubstituteRule>) : Rule {
    companion object {
        private val SUBSTITUTIONS_ADD_RULE = DefaultDependencySubstitutions::class.java.getDeclaredMethod(
            "addSubstitution",
            Action::class.java,
            Boolean::class.java
        ).apply { isAccessible = true }
    }

    @Transient private lateinit var rulesById: Map<ModuleIdentifier, List<SubstituteRule>>

    override fun apply(
        project: Project,
        configuration: Configuration,
        resolutionStrategy: ResolutionStrategy,
        extension: NebulaResolutionRulesExtension
    ) {
        if (!this::rulesById.isInitialized) {
            val substitution = resolutionStrategy.dependencySubstitution
            rulesById = rules.map { rule ->
                if (!rule.isInitialized()) {
                    rule.substitutedVersionId = rule.module.toModuleVersionId()
                    val withModule = substitution.module(rule.with)
                    if (withModule !is ModuleComponentSelector) {
                        throw SubstituteRuleMissingVersionException(rule.with, rule)
                    }
                    rule.withComponentSelector = withModule
                    rule.versionSelector = VersionWithSelector(rule.substitutedVersionId.version).asSelector()
                }
                rule
            }.groupBy { it.substitutedVersionId.module }
                .mapValues { entry -> entry.value.sortedBy { it.substitutedVersionId.version } }
        }

        val substitutionAction = Action<DependencySubstitution> { details ->
            val requested = details.requested
            if (requested is ModuleComponentSelector) {
                val rules = rulesById[requested.moduleIdentifier] ?: return@Action
                rules.forEach { rule ->
                    val withComponentSelector = rule.withComponentSelector
                    if (rule.acceptsVersion(requested.version)) {
                        val message =
                            "substituted ${rule.substitutedVersionId} with $withComponentSelector because '${rule.reason}' by rule ${rule.ruleSet}"
                        details.useTarget(
                            withComponentSelector,
                            message
                        )
                        return@Action
                    }
                }
            }
        }

        /*
         * Unfortunately impossible to avoid an internal/protected method dependency for now:
         *
         * - We can't dependencySubstitutions.all because it causes the configuration to be resolved at task graph calculation time due to the possibility of project substitutions there
         * - Likewise eachDependency has it's own performance issues - https://github.com/gradle/gradle/issues/16151
         *
         * There's no alternative to all that only allows module substitution and we only ever substitute modules for modules, so this is completely safe.
         */
        SUBSTITUTIONS_ADD_RULE.invoke(resolutionStrategy.dependencySubstitution, substitutionAction, false)
    }
}

data class RejectRule(
    override val module: String,
    override var ruleSet: String?,
    override val reason: String,
    override val author: String,
    override val date: String
) : ModuleRule {
    val moduleVersionId = module.toModuleVersionId()
    @Transient lateinit var versionSelector: VersionSelector

    override fun apply(
        project: Project,
        configuration: Configuration,
        resolutionStrategy: ResolutionStrategy,
        extension: NebulaResolutionRulesExtension
    ) {
        throw UnsupportedOperationException("Reject rules cannot be applied directly and must be applied via RejectRules")
    }

    fun hasVersionSelector(): Boolean = this::versionSelector.isInitialized
}

data class RejectRules(val rules: List<RejectRule>) : Rule {
    private val ruleByModuleIdentifier = rules.groupBy { it.moduleVersionId.module }

    override fun apply(
        project: Project,
        configuration: Configuration,
        resolutionStrategy: ResolutionStrategy,
        extension: NebulaResolutionRulesExtension
    ) {
        resolutionStrategy.componentSelection.all { selection ->
            val candidate = selection.candidate
            val rules = ruleByModuleIdentifier[candidate.moduleIdentifier] ?: return@all
            rules.forEach { rule ->
                rule.versionSelector = VersionWithSelector(rule.moduleVersionId.version).asSelector()
                if (!rule.hasVersionSelector() || rule.versionSelector.accept(candidate.version)) {
                    val message = "rejected by rule ${rule.ruleSet} because '${rule.reason}'"
                    selection.reject(message)
                    if (!rule.hasVersionSelector()) {
                        return@forEach
                    }
                }
            }
        }
    }
}

data class DenyRule(
    override val module: String,
    override var ruleSet: String?,
    override val reason: String,
    override val author: String,
    override val date: String
) : ModuleRule {
    private val moduleVersionId = module.toModuleVersionId()

    override fun apply(
        project: Project,
        configuration: Configuration,
        resolutionStrategy: ResolutionStrategy,
        extension: NebulaResolutionRulesExtension
    ) {
        val moduleId = moduleVersionId.module
        val match = configuration.allDependencies.find {
            it is ExternalModuleDependency && it.group == moduleId.group && it.name == moduleId.name
        }
        if (match != null && (moduleVersionId.version.isEmpty() || match.version == moduleVersionId.version)) {
            resolutionStrategy.componentSelection.withModule(moduleId) { selection ->
                val message = "denied by rule $ruleSet because '$reason'"
                selection.reject(message)
            }
            throw DependencyDeniedException(moduleVersionId, this)
        }
    }
}

data class ExcludeRule(
    override val module: String,
    override var ruleSet: String?,
    override val reason: String,
    override val author: String,
    override val date: String
) : ModuleRule {
    private val moduleId = module.toModuleId()

    @Override
    override fun apply(
        project: Project,
        configuration: Configuration,
        resolutionStrategy: ResolutionStrategy,
        extension: NebulaResolutionRulesExtension
    ) {
        val message =
            "excluded $moduleId and transitive dependencies for all dependencies of this configuration by rule $ruleSet"
        ResolutionRulesPlugin.Logger.debug(message)
        // TODO: would like a core Gradle feature that accepts a reason
        configuration.exclude(moduleId.group, moduleId.name)
        resolutionStrategy.componentSelection.withModule(moduleId.toString()) { selection ->
            selection.reject(message)
        }
    }
}

class DependencyDeniedException(moduleVersionId: ModuleVersionIdentifier, rule: DenyRule) :
    Exception("Dependency $moduleVersionId denied by rule ${rule.ruleSet}")

class SubstituteRuleMissingVersionException(moduleId: String, rule: SubstituteRule) :
    Exception("The dependency to be substituted ($moduleId) must have a version. Rule ${rule.ruleSet} is invalid")

fun Configuration.exclude(group: String, module: String) {
    exclude(mapOf("group" to group, "module" to module))
}

fun String.toModuleId(): ModuleIdentifier {
    val parts = split(":")
    check(parts.size == 2) { "$this is an invalid module identifier" }
    return DefaultModuleIdentifier.newId(parts[0], parts[1])
}

fun String.toModuleVersionId(): ModuleVersionIdentifier {
    val parts = split(":")
    val id = DefaultModuleIdentifier.newId(parts[0], parts[1])
    check((2..3).contains(parts.size)) { "$this is an invalid module identifier" }
    return DefaultModuleVersionIdentifier.newId(id, if (parts.size == 3) parts[2] else "")
}
