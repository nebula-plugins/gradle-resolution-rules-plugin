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

import com.netflix.nebula.dependencybase.DependencyManagement
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.artifacts.ModuleVersionIdentifier as GradleModuleVersionIdentifier

val versionComparator = DefaultVersionComparator()
val versionScheme = DefaultVersionSelectorScheme(versionComparator)

interface Rule {
    fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, insight: DependencyManagement)
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

    fun dependencyRules() = listOf(replace, substitute, reject, deny, exclude).flatten()

    fun beforeResolveRules() = listOf(AlignRules(align))
}

fun RuleSet.withName(ruleSetName: String): RuleSet {
    name = ruleSetName
    listOf(replace, substitute, reject, deny, exclude, align).flatten().forEach { it.ruleSet = ruleSetName }
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
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, insight: DependencyManagement) {
        val moduleId = ModuleIdentifier.valueOf(module)
        val withModuleId = ModuleIdentifier.valueOf(with)
        project.dependencies.modules.module(moduleId.toString()) {
            val details = it as ComponentModuleMetadataDetails
            details.replacedBy(withModuleId.toString())
            insight.addReason(configuration.name, "${withModuleId.organization}:${withModuleId.name}", "replacement ${details.id.group}:${details.id.name} -> ${withModuleId.organization}:${withModuleId.name}", "nebula.resolution-rules")
        }
    }
}

data class SubstituteRule(val module: String, val with: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : BasicRule {
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, insight: DependencyManagement) {
        val substitution = resolutionStrategy.dependencySubstitution
        val selector = substitution.module(module)
        val withModuleId = ModuleVersionIdentifier.valueOf(with)
        if (!withModuleId.hasVersion()) {
            throw SubstituteRuleMissingVersionException(withModuleId, this)
        }
        val withSelector = substitution.module(withModuleId.toString()) as ModuleComponentSelector

        if (selector is ModuleComponentSelector) {
            resolutionStrategy.dependencySubstitution.all(action {
                if (requested is ModuleComponentSelector) {
                    val requestedSelector = requested as ModuleComponentSelector
                    if (requestedSelector.group == selector.group && requestedSelector.module == selector.module) {
                        if (versionScheme.parseSelector(selector.version).accept(requestedSelector.version)) {
                            insight.addReason(configuration.name, "${withSelector.group}:${withSelector.module}", "substitution because $reason", "nebula.resolution-rules")
                            useTarget(withSelector)
                        }
                    }
                }
            })
        } else {
            resolutionStrategy.dependencySubstitution {
                insight.addReason(configuration.name, "${withSelector.group}:${withSelector.module}", "substitution because $reason", "nebula.resolution-rules")
                it.substitute(selector).with(withSelector)
            }
        }
    }
}

data class RejectRule(override val module: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : ModuleRule {
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, insight: DependencyManagement) {
        val moduleId = ModuleVersionIdentifier.valueOf(module)
        resolutionStrategy.componentSelection.all(Action<ComponentSelection> { selection ->
            val candidate = selection.candidate
            if (candidate.group == moduleId.organization && candidate.module == moduleId.name) {
                if (!moduleId.hasVersion() || versionScheme.parseSelector(moduleId.version).accept(candidate.version)) {
                    selection.reject("Rejected by resolution rule $ruleSet - $reason")
                }
            }
        })
    }
}

data class DenyRule(override val module: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : ModuleRule {
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, insight: DependencyManagement) {
        val moduleId = ModuleVersionIdentifier.valueOf(module)
        val match = configuration.allDependencies.find {
            it is ExternalModuleDependency && it.group == moduleId.organization && it.name == moduleId.name
        }
        if (match != null && (!moduleId.hasVersion() || match.version == moduleId.version)) {
            throw DependencyDeniedException(moduleId, this)
        }
    }
}

data class ExcludeRule(override val module: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : ModuleRule {
    val logger: Logger = Logging.getLogger(ExcludeRule::class.java)

    @Override
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, insight: DependencyManagement) {
        val moduleId = ModuleIdentifier.valueOf(module)
        logger.debug("Resolution rule $this excluding ${moduleId.organization}:${moduleId.name}")
        configuration.exclude(moduleId.organization, moduleId.name)
    }
}

class DependencyDeniedException(moduleId: ModuleVersionIdentifier, rule: DenyRule) : Exception("Dependency $moduleId denied by dependency rule: ${rule.reason}")

class SubstituteRuleMissingVersionException(moduleId: ModuleVersionIdentifier, rule: SubstituteRule) : Exception("The dependency to be substituted ($moduleId) must have a version. Invalid rule: $rule")

fun Configuration.exclude(group: String, module: String) {
    exclude(mapOf("group" to group, "module" to module))
}
