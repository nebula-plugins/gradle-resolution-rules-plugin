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
import com.netflix.nebula.interop.action
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.ComponentModuleMetadataDetails
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.Serializable

interface Rule {
    fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, reasons: MutableSet<String>)
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
        val align: List<AlignRule> = emptyList()) {

    fun dependencyRulesPartOne() =
            listOf(replace, substitute, reject, deny, exclude).flatten()

    fun dependencyRulesPartTwo() =
            if (ResolutionRulesPlugin.isCoreAlignmentEnabled())
                listOf(align).flatten()
            else
                emptyList()

    fun resolveRules() =
            if (ResolutionRulesPlugin.isCoreAlignmentEnabled())
                emptyList()
            else
                listOf(AlignRules(align))

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

data class ReplaceRule(override val module: String, val with: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : ModuleRule {
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, reasons: MutableSet<String>) {
        val moduleId = ModuleIdentifier.valueOf(module)
        val withModuleId = ModuleIdentifier.valueOf(with)
        project.dependencies.modules.module(moduleId.toString()) {
            val details = it as ComponentModuleMetadataDetails
            val message = "replacement ${moduleId.organization}:${moduleId.name} -> ${withModuleId.organization}:${withModuleId.name}\n" +
                    "\twith reasons: ${reasons.joinToString()}"
            details.replacedBy(withModuleId.toString(), message)
        }
    }
}

data class SubstituteRule(val module: String, val with: String, override var ruleSet: String?,
                          override val reason: String, override val author: String, override val date: String) : BasicRule, Serializable {
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, reasons: MutableSet<String>) {
        val substitution = resolutionStrategy.dependencySubstitution
        val substitutedModule = substitution.module(module)
        val withModuleId = ModuleVersionIdentifier.valueOf(with)
        if (!withModuleId.hasVersion()) {
            throw SubstituteRuleMissingVersionException(withModuleId, this, reasons)
        }
        val withSelector = substitution.module(withModuleId.toString()) as ModuleComponentSelector

        if (substitutedModule is ModuleComponentSelector) {
            resolutionStrategy.dependencySubstitution.all(action {
                if (requested is ModuleComponentSelector) {
                    val requestedSelector = requested as ModuleComponentSelector
                    if (requestedSelector.group == substitutedModule.group && requestedSelector.module == substitutedModule.module) {
                        val versionSelector = VersionWithSelector(substitutedModule.version).asSelector()
                        if (versionSelector.accept(requestedSelector.version)) {
                            val message = "substitution from '$substitutedModule' to '$withSelector' because $reason \n" +
                                    "\twith reasons: ${reasons.joinToString()}"
                            // Note on `useTarget`:
                            // Forcing modules via ResolutionStrategy.force(Object...) uses this capability.
                            // from https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/DependencyResolveDetails.html
                            useTarget(withSelector, message)
                        }
                    }
                }
            })
        } else {
            var message = "substitution to '$withSelector' because $reason \n" +
                    "\twith reasons: ${reasons.joinToString()}"

            val selectorNameSections = substitutedModule.displayName.split(":")
            if (selectorNameSections.size > 2) {
                val selectorGroupAndArtifact = "${selectorNameSections[0]}:${selectorNameSections[1]}"
                message = "substitution from '$selectorGroupAndArtifact' to '$withSelector' because $reason \n" +
                        "\twith reasons: ${reasons.joinToString()}"
            }

            resolutionStrategy.dependencySubstitution {
                it.substitute(substitutedModule)
                        .because(message)
                        .with(withSelector)
            }
        }
    }
}

data class RejectRule(override val module: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : ModuleRule {
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, reasons: MutableSet<String>) {
        val moduleId = ModuleVersionIdentifier.valueOf(module)
        resolutionStrategy.componentSelection.all(Action<ComponentSelection> { selection ->
            val candidate = selection.candidate
            if (candidate.group == moduleId.organization && candidate.module == moduleId.name) {
                val versionSelector = VersionWithSelector(moduleId.version).asSelector()
                if (!moduleId.hasVersion() || versionSelector.accept(candidate.version)) {
                    val message = "Rejected by resolution rule $ruleSet - $reason\n" +
                            "\twith reasons: ${reasons.joinToString()}"
                    selection.reject(message)
                }
            }
        })
    }
}

data class DenyRule(override val module: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : ModuleRule {
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, reasons: MutableSet<String>) {
        val moduleId = ModuleVersionIdentifier.valueOf(module)
        val match = configuration.allDependencies.find {
            it is ExternalModuleDependency && it.group == moduleId.organization && it.name == moduleId.name
        }
        if (match != null && (!moduleId.hasVersion() || match.version == moduleId.version)) {
            resolutionStrategy.componentSelection.withModule("${moduleId.organization}:${moduleId.name}", Action<ComponentSelection> { selection ->
                val message = "Dependency $moduleId denied by dependency rule: $reason\n" +
                        "\twith reasons: ${reasons.joinToString()}"
                selection.reject(message)
            })
            throw DependencyDeniedException(moduleId, this, reasons)
        }
    }
}

data class ExcludeRule(override val module: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : ModuleRule {
    val logger: Logger = Logging.getLogger(ExcludeRule::class.java)

    @Override
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, reasons: MutableSet<String>) {
        val moduleId = ModuleIdentifier.valueOf(module)
        val message = "Resolution rule $this excluding ${moduleId.organization}:${moduleId.name} and transitive dependencies for all dependencies of this configuration\n" +
                "\twith reasons: ${reasons.joinToString()}"
        logger.debug(message)
        // TODO: would like a core Gradle feature that accepts a reason
        configuration.exclude(moduleId.organization, moduleId.name)

        resolutionStrategy.componentSelection.withModule("${moduleId.organization}:${moduleId.name}", Action<ComponentSelection> { selection ->
            selection.reject(message)
        })
    }
}

class DependencyDeniedException(moduleId: ModuleVersionIdentifier, rule: DenyRule, reasons: MutableSet<String>) : Exception("Dependency $moduleId denied by dependency rule: ${rule.reason}\n" +
        "\twith reasons: ${reasons.joinToString()}")

class SubstituteRuleMissingVersionException(moduleId: ModuleVersionIdentifier, rule: SubstituteRule, reasons: MutableSet<String>) : Exception("The dependency to be substituted ($moduleId) must have a version. Invalid rule: $rule\n" +
        "\twith reasons: ${reasons.joinToString()}")

fun Configuration.exclude(group: String, module: String) {
    exclude(mapOf("group" to group, "module" to module))
}
