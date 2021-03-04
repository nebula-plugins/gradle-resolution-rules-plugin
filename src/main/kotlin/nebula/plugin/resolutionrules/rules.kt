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
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dsl.ModuleVersionSelectorParsers
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.Serializable

interface Rule {
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
) {

    fun dependencyRulesPartOne() =
        listOf(replace, deny, exclude).flatten() + listOf(SubstituteRules(substitute), RejectRules(reject))

    fun dependencyRulesPartTwo(coreAlignmentEnabled: Boolean) =
        if (coreAlignmentEnabled)
            listOf(align).flatten()
        else
            emptyList()

    fun resolveRules(coreAlignmentEnabled: Boolean) =
        if (coreAlignmentEnabled) emptyList() else listOf(AlignRules(align))

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
    override fun apply(
        project: Project,
        configuration: Configuration,
        resolutionStrategy: ResolutionStrategy,
        extension: NebulaResolutionRulesExtension
    ) {
        project.dependencies.modules.module(module) {
            val details = it as ComponentModuleMetadataDetails
            val message = "replaced $module -> $with because '$reason' by rule $ruleSet"
            details.replacedBy(with, message)
        }
    }
}

data class SubstituteRule(
    val module: String, val with: String, override var ruleSet: String?,
    override val reason: String, override val author: String, override val date: String
) : BasicRule, Serializable {
    lateinit var substitutedModule: ComponentSelector
    lateinit var withComponentSelector: ModuleComponentSelector
    lateinit var withVersionSelector: ModuleVersionSelector
    val versionSelector by lazy {
        val version = (substitutedModule as ModuleComponentSelector).version
        VersionWithSelector(version).asSelector()
    }

    override fun apply(
        project: Project,
        configuration: Configuration,
        resolutionStrategy: ResolutionStrategy,
        extension: NebulaResolutionRulesExtension
    ) {
        throw UnsupportedOperationException("Substitution rules cannot be applied directly and must be applied via SubstituteRules")
    }

    fun isInitialized(): Boolean = this::substitutedModule.isInitialized
}

class SubstituteRules(val rules: List<SubstituteRule>) : Rule {
    private lateinit var versionedRulesById: Map<ModuleIdentifier, List<SubstituteRule>>
    private lateinit var unversionedRules: List<SubstituteRule>

    override fun apply(
        project: Project,
        configuration: Configuration,
        resolutionStrategy: ResolutionStrategy,
        extension: NebulaResolutionRulesExtension
    ) {
        if (!this::versionedRulesById.isInitialized) {
            val substitution = resolutionStrategy.dependencySubstitution

            val (versionedRules, unversionedRules) = rules.map { rule ->
                if (!rule.isInitialized()) {
                    rule.substitutedModule = substitution.module(rule.module)
                    val withModule = substitution.module(rule.with)
                    if (withModule !is ModuleComponentSelector) {
                        throw SubstituteRuleMissingVersionException(rule.with, rule)
                    }
                    rule.withComponentSelector = withModule
                    rule.withVersionSelector = ModuleVersionSelectorParsers.parser()
                        .parseNotation(rule.withComponentSelector.displayName)
                }
                rule
            }.partition { it.substitutedModule is ModuleComponentSelector }

            this.unversionedRules = unversionedRules
            versionedRulesById =
                versionedRules.groupBy { (it.substitutedModule as ModuleComponentSelector).moduleIdentifier }
        }

        if (versionedRulesById.isNotEmpty()) {
            // We use eachDependency because dependencySubstitutions.all causes configuration task dependencies to resolve at configuration time
            resolutionStrategy.eachDependency { details ->
                val requested = details.requested
                val requestedString = requested.toString()
                val rules = versionedRulesById[requested.module] ?: return@eachDependency
                val requestedSelectorVersion = requested.version
                rules.forEach { rule ->
                    if (rule.versionSelector.accept(requestedSelectorVersion)
                        && !requestedString.contains(".+")
                        && !requestedString.contains("latest")
                    ) {
                        // Note on `useTarget`:
                        // Forcing modules via ResolutionStrategy.force(Object...) uses this capability.
                        // from https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/DependencyResolveDetails.html
                        details.useTarget(rule.withVersionSelector) // We can't pass a ModuleComponentSelector here so we take the conversion hit
                        details.because("substituted ${rule.substitutedModule} with ${rule.withComponentSelector} because '${rule.reason}' by rule ${rule.ruleSet}")
                        return@eachDependency
                    }
                }
            }
        }

        unversionedRules.forEach { rule ->
            val substitutedModule = rule.substitutedModule
            val withComponentSelector = rule.withComponentSelector
            var message = "substituted $withComponentSelector because '${rule.reason}' by rule ${rule.ruleSet}"

            val selectorNameSections = substitutedModule.displayName.split(":")
            if (selectorNameSections.size > 2) {
                val selectorGroupAndArtifact = "${selectorNameSections[0]}:${selectorNameSections[1]}"
                message = "substituted $selectorGroupAndArtifact with $withComponentSelector because '${rule.reason}' by rule ${rule.ruleSet}"
            }

            resolutionStrategy.dependencySubstitution {
                it.substitute(substitutedModule)
                    .because(message)
                    .with(withComponentSelector)
            }
        }
    }
}

data class RejectRule(
    override val module: String,
    override var ruleSet: String?,
    override val reason: String,
    override val author: String,
    override val date: String
) : ModuleRule {
    val moduleIdentifier: ModuleIdentifier
    lateinit var versionSelector: VersionSelector

    init {
        val parts = module.split(":")
        moduleIdentifier = DefaultModuleIdentifier.newId(parts[0], parts[1])
        if (parts.size == 3) {
            versionSelector = VersionWithSelector(parts[2]).asSelector()
        }
    }

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
    private val ruleByModuleIdentifier = rules.groupBy { it.moduleIdentifier }

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
    private val moduleId: ModuleIdentifier
    private lateinit var version: String

    init {
        val parts = module.split(":")
        moduleId = DefaultModuleIdentifier.newId(parts[0], parts[1])
        if (parts.size == 3) {
            version = parts[2]
        }
    }

    override fun apply(
        project: Project,
        configuration: Configuration,
        resolutionStrategy: ResolutionStrategy,
        extension: NebulaResolutionRulesExtension
    ) {
        val match = configuration.allDependencies.find {
            it is ExternalModuleDependency && it.group == moduleId.group && it.name == moduleId.name
        }
        if (match != null && (!this::version.isInitialized || match.version == version)) {
            resolutionStrategy.componentSelection.withModule(moduleId) { selection ->
                val message = "denied by rule $ruleSet because '$reason'"
                selection.reject(message)
            }
            throw DependencyDeniedException(module, this)
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
    private val logger: Logger = Logging.getLogger(ExcludeRule::class.java)
    private val moduleId: ModuleIdentifier

    init {
        val parts = module.split(":")
        moduleId = DefaultModuleIdentifier.newId(parts[0], parts[1])
    }

    @Override
    override fun apply(
        project: Project,
        configuration: Configuration,
        resolutionStrategy: ResolutionStrategy,
        extension: NebulaResolutionRulesExtension
    ) {
        val message =
            "excluded $moduleId and transitive dependencies for all dependencies of this configuration by rule $ruleSet"
        logger.debug(message)
        // TODO: would like a core Gradle feature that accepts a reason
        configuration.exclude(moduleId.group, moduleId.name)
        resolutionStrategy.componentSelection.withModule(moduleId.toString()) { selection ->
            selection.reject(message)
        }
    }
}

class DependencyDeniedException(notation: String, rule: DenyRule) :
    Exception("Dependency $notation denied by rule ${rule.ruleSet}")

class SubstituteRuleMissingVersionException(moduleId: String, rule: SubstituteRule) :
    Exception("The dependency to be substituted ($moduleId) must have a version. Rule ${rule.ruleSet} is invalid")

fun Configuration.exclude(group: String, module: String) {
    exclude(mapOf("group" to group, "module" to module))
}