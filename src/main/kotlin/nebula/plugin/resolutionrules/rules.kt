/*
 * Copyright 2015 Netflix, Inc.
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

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.ComponentModuleMetadataDetails
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.joda.time.DateTime

class Rules(val replace: List<ReplaceRule>,
            val substitute: List<SubstituteRule>,
            val reject: List<RejectRule>,
            val deny: List<DenyRule>) {
    fun projectRules(): List<ProjectRule> {
        return replace
    }

    fun configurationRules(): List<ConfigurationRule> {
        return listOf(substitute, reject, deny).flatten()
    }
}

fun Iterable<Rules>.flattenRules(): Rules {
    val relocate = this.flatMap { it.replace }
    val substitute = this.flatMap { it.substitute }
    val reject = this.flatMap { it.reject }
    val deny = this.flatMap { it.deny }
    return Rules(relocate, substitute, reject, deny)
}

interface ProjectRule {
    fun apply(project: Project)
}

interface ConfigurationRule {
    fun apply(configuration: Configuration)
}

data class ReplaceRule(
        val module: String,
        val with: String,
        val reason: String,
        val author: String,
        val date: DateTime
) : ProjectRule {
    override fun apply(project: Project) {
        val moduleId = module.toModuleIdentifier()
        val withModuleId = with.toModuleIdentifier()
        project.dependencies.modules.module(moduleId.coordinates) {
            val details = it as ComponentModuleMetadataDetails
            details.replacedBy(withModuleId.coordinates)
        }
    }
}

data class SubstituteRule(
        val module: String,
        val with: String,
        val reason: String,
        val author: String,
        val date: DateTime
) : ConfigurationRule {
    override fun apply(configuration: Configuration) {
        val resolutionStrategy = configuration.resolutionStrategy
        val substitution = resolutionStrategy.dependencySubstitution
        val selector = substitution.module(module.toModuleIdentifier().coordinates)
        val withModuleId = with.toModuleVersionIdentifier()
        if (!withModuleId.hasVersion()) {
            throw SubstituteRuleMissingVersionException(withModuleId, this)
        }
        val withSelector = substitution.module(withModuleId.coordinates)
        resolutionStrategy.dependencySubstitution {
            it.substitute(selector).with(withSelector)
        }
    }
}

data class RejectRule(
        val module: String,
        val reason: String,
        val author: String,
        val date: DateTime
) : ConfigurationRule {
    override fun apply(configuration: Configuration) {
        val moduleId = module.toModuleVersionIdentifier()
        configuration.resolutionStrategy.componentSelection.all(Action { selection ->
            val candidate = selection.candidate
            if (candidate.group == moduleId.organization && candidate.module == moduleId.name) {
                if (!moduleId.hasVersion() || candidate.version == moduleId.version) {
                    selection.reject(reason)
                }
            }
        })
    }
}

data class DenyRule(
        val module: String,
        val reason: String,
        val author: String,
        val date: DateTime
) : ConfigurationRule {
    override fun apply(configuration: Configuration) {
        val moduleId = module.toModuleVersionIdentifier()
        val match = configuration.dependencies
                .find { it is ExternalModuleDependency && it.group == moduleId.organization && it.name == moduleId.name }
        if (match != null && (!moduleId.hasVersion() || match.version == moduleId.version)) {
            throw DependencyDeniedException(moduleId, this)
        }
    }
}

public class DependencyDeniedException(val moduleId: ModuleVersionIdentifier, val rule: DenyRule) :
        Exception("Dependency ${moduleId.coordinates} denied by dependency rule: ${rule.reason}")

public class SubstituteRuleMissingVersionException(moduleId: ModuleVersionIdentifier, rule: SubstituteRule) :
        Exception("The dependency to be substituted ($moduleId) must have a version. Invalid rule: $rule")
