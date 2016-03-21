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

import nebula.plugin.resolutionrules.ModuleIdentifier
import nebula.plugin.resolutionrules.ModuleVersionIdentifier
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.VersionInfo
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.joda.time.DateTime

public class Rules {
    List<ReplaceRule> replace
    List<SubstituteRule> substitute
    List<RejectRule> reject
    List<DenyRule> deny
    List<AlignRule> align

    public List<ProjectRule> projectRules() {
        return replace
    }

    public List<ConfigurationRule> configurationRules() {
        return Arrays.asList(substitute, reject, deny).flatten()
    }

    public List<ProjectConfigurationRule> projectConfigurationRules() {
        return align
    }
}

interface ProjectRule {
    public void apply(Project project)
}

interface ConfigurationRule {
    public void apply(Configuration configuration)
}

interface ProjectConfigurationRule {
    public void apply(Project project, Configuration configuration)
}

class ReplaceRule implements ProjectRule {
    String module
    String with
    String reason
    String author
    DateTime date

    public void apply(Project project) {
        ModuleIdentifier moduleId = ModuleIdentifier.valueOf(module)
        ModuleIdentifier withModuleId = ModuleIdentifier.valueOf(with)
        project.dependencies.modules.module(moduleId.toString()) {
            ComponentModuleMetadataDetails details = it as ComponentModuleMetadataDetails
            details.replacedBy(withModuleId.toString())
        }
    }
}

class SubstituteRule implements ConfigurationRule {
    String module
    String with
    String reason
    String author
    DateTime date

    public void apply(Configuration configuration) {
        ResolutionStrategy resolutionStrategy = configuration.resolutionStrategy
        DependencySubstitutions substitution = resolutionStrategy.dependencySubstitution
        ComponentSelector selector = substitution.module(module)
        ModuleVersionIdentifier withModuleId = ModuleVersionIdentifier.valueOf(with)
        if (!withModuleId.hasVersion()) {
            throw new SubstituteRuleMissingVersionException(withModuleId, this)
        }
        ComponentSelector withSelector = substitution.module(withModuleId.toString())
        resolutionStrategy.dependencySubstitution {
            it.substitute(selector).with(withSelector)
        }
    }

    @Override
    String toString() {
        return "${this.class.simpleName}(module=$module, with=$with, reason=$reason, author=$author, date=$date)"
    }
}

class RejectRule implements ConfigurationRule {
    String module
    String reason
    String author
    DateTime date

    public void apply(Configuration configuration) {
        ModuleVersionIdentifier moduleId = ModuleVersionIdentifier.valueOf(module)
        configuration.resolutionStrategy.componentSelection.all({ selection ->
            ModuleComponentIdentifier candidate = selection.candidate
            if (candidate.group == moduleId.organization && candidate.module == moduleId.name) {
                if (!moduleId.hasVersion() || candidate.version == moduleId.version) {
                    selection.reject(reason)
                }
            }
        })
    }
}

class DenyRule implements ConfigurationRule {
    String module
    String reason
    String author
    DateTime date

    @Override
    public void apply(Configuration configuration) {
        ModuleVersionIdentifier moduleId = ModuleVersionIdentifier.valueOf(module)
        Dependency match = configuration.dependencies.find {
            it instanceof ExternalModuleDependency && it.group == moduleId.organization && it.name == moduleId.name
        }
        if (match != null && (!moduleId.hasVersion() || match.version == moduleId.version)) {
            throw new DependencyDeniedException(moduleId, this)
        }
    }
}

class AlignRule implements ProjectConfigurationRule {
    String group
    Collection<String> includes = []
    Collection<String> excludes = []
    String reason
    String author
    DateTime date

    boolean resolvedMatches(ResolvedDependency dep) {
        ruleMatches(dep.moduleGroup, dep.moduleName)
    }

    boolean dependencyMatches(DependencyResolveDetails details) {
        ruleMatches(details.requested.group, details.requested.name)
    }

    boolean ruleMatches(String inputGroup, String inputName) {
        inputGroup == group &&
                (includes.isEmpty() || includes.contains(inputName)) &&
                (excludes.isEmpty() || !excludes.contains(inputName))
    }

    @Override
    void apply(Project project, Configuration configuration) {
        def detached = project.configurations.detachedConfiguration(configuration.dependencies.toArray(new Dependency[configuration.dependencies.size()]))
        def matches = []
        def recurseDeps
        recurseDeps = { Collection<ResolvedDependency> col ->
            if (!col.isEmpty()) {
                col.each { ResolvedDependency dep ->
                    if (resolvedMatches(dep))
                    matches.add(dep)
                }
                recurseDeps(col*.children.flatten())
            }
        }

        recurseDeps(detached.resolvedConfiguration.firstLevelModuleDependencies)
        def versions = matches.collect { it.moduleVersion }.toUnique().collect { new VersionInfo(it) }
        def comparator = new DefaultVersionComparator()
        def alignedVersion = versions.max { a, b -> comparator.compare(a, b)}

        configuration.resolutionStrategy { ResolutionStrategy rs ->
            rs.eachDependency { DependencyResolveDetails details ->
                if (dependencyMatches(details)) {
                    details.useTarget group: details.requested.group, name: details.requested.name, version: alignedVersion.version
                }
            }
        }
    }
}

public class DependencyDeniedException extends Exception {
    public DependencyDeniedException(ModuleVersionIdentifier moduleId, DenyRule rule) {
        super("Dependency ${moduleId} denied by dependency rule: ${rule.reason}")
    }
}

public class SubstituteRuleMissingVersionException extends Exception {
    public SubstituteRuleMissingVersionException(ModuleVersionIdentifier moduleId, SubstituteRule rule) {
        super("The dependency to be substituted ($moduleId) must have a version. Invalid rule: $rule")
    }
}
