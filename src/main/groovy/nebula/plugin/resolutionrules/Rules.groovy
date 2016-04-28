/*
 * Copyright 2015-2016 Netflix, Inc.
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
 */
package nebula.plugin.resolutionrules

import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.specs.Specs
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

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
        return deny
    }

    public List<ResolutionRule> resolutionRules() {
        return [substitute, reject].flatten()
    }

    public List<ProjectConfigurationRule> projectConfigurationRules() {
        return [new AlignRules(aligns: align)]
    }
}

interface ProjectRule {
    public void apply(Project project)
}

interface ResolutionRule {
    public void apply(ResolutionStrategy rs)
}

interface ConfigurationRule {
    public void apply(Configuration configuration)
}

interface ProjectConfigurationRule {
    public void apply(Project project, ResolutionStrategy rs, Configuration configuration, NebulaResolutionRulesExtension extension)
}

abstract class BaseRule {
    String reason
    String author
    DateTime date

    BaseRule(Map map) {
        reason = map.reason
        author = map.author
        date = new DateTime(map.date).toDateTime(DateTimeZone.UTC)
    }
}

class ReplaceRule extends BaseRule implements ProjectRule {
    String module
    String with

    ReplaceRule(Map map) {
        super(map)
        module = map.module
        with = map.with
    }

    public void apply(Project project) {
        ModuleIdentifier moduleId = ModuleIdentifier.valueOf(module)
        ModuleIdentifier withModuleId = ModuleIdentifier.valueOf(with)
        project.dependencies.modules.module(moduleId.toString()) {
            ComponentModuleMetadataDetails details = it as ComponentModuleMetadataDetails
            details.replacedBy(withModuleId.toString())
        }
    }
}

class SubstituteRule extends BaseRule implements ResolutionRule {
    String module
    String with

    SubstituteRule(Map map) {
        super(map)
        module = map.module
        with = map.with
    }

    @Override
    public void apply(ResolutionStrategy resolutionStrategy) {
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

class RejectRule extends BaseRule implements ResolutionRule {
    String module

    RejectRule(Map map) {
        super(map)
        module = map.module
    }

    @Override
    public void apply(ResolutionStrategy resolutionStrategy) {
        ModuleVersionIdentifier moduleId = ModuleVersionIdentifier.valueOf(module)
        resolutionStrategy.componentSelection.all({ selection ->
            ModuleComponentIdentifier candidate = selection.candidate
            if (candidate.group == moduleId.organization && candidate.module == moduleId.name) {
                if (!moduleId.hasVersion() || candidate.version == moduleId.version) {
                    selection.reject(reason)
                }
            }
        })
    }
}

class DenyRule extends BaseRule implements ConfigurationRule {
    String module

    DenyRule(Map map) {
        super(map)
        module = map.module
    }

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

class AlignRule extends BaseRule {
    String name
    String group
    Collection<String> includes
    Collection<String> excludes

    AlignRule(Map map) {
        super(map)
        name = map.name
        group = map.group
        includes = map.includes ?: []
        excludes = map.excludes ?: []
    }

    boolean resolvedMatches(ResolvedModuleVersion dep) {
        ruleMatches(dep.id.group, dep.id.name)
    }

    boolean dependencyMatches(DependencyResolveDetails details) {
        ruleMatches(details.requested.group, details.requested.name)
    }

    boolean ruleMatches(String inputGroup, String inputName) {
        def matchedIncludes = includes.findAll { inputName.matches(it) }
        def matchedExcludes = excludes.findAll { inputName.matches(it) }
        inputGroup.matches(group) &&
                (includes.isEmpty() || !matchedIncludes.isEmpty()) &&
                (excludes.isEmpty() || matchedExcludes.isEmpty())
    }

    boolean shouldNotBeSkipped(NebulaResolutionRulesExtension extension) {
        !extension.skipAlignRules.contains(name)
    }
}

class AlignRules implements ProjectConfigurationRule {
    List<AlignRule> aligns

    @Override
    void apply(Project project, ResolutionStrategy resolutionStrategy, Configuration configuration, NebulaResolutionRulesExtension extension) {
        if (aligns.size() == 0) { // don't do extra resolves if there are no align rules
            return
        }

        def copy = configuration.copyRecursive()
        copy.exclude group: project.group, module: project.name
        def artifacts
        def resolvedConfiguration = copy.resolvedConfiguration
        if (resolvedConfiguration.hasError()) {
            def lenientConfiguration = resolvedConfiguration.lenientConfiguration
            project.logger.info("Cannot resolve all dependencies to align, configuration '${configuration.name}' should also fail to resolve")
            artifacts = lenientConfiguration.getArtifacts(Specs.SATISFIES_ALL)
        } else {
            artifacts = resolvedConfiguration.resolvedArtifacts
        }

        def moduleVersions = artifacts.collect { it.moduleVersion }
        def selectedVersion = [:]
        aligns.each { AlignRule align ->
            if (align.shouldNotBeSkipped(extension)) {
                def matches = moduleVersions.findAll { ResolvedModuleVersion dep -> align.resolvedMatches(dep) }
                def versions = matches.collect { ResolvedModuleVersion dep -> dep.id.version }.toUnique()
                def comparator = new DefaultVersionComparator().asStringComparator()
                def alignedVersion = versions.max { a, b -> comparator.compare(a, b) }
                if (alignedVersion) {
                    selectedVersion[align] = alignedVersion
                }
            }
        }

        resolutionStrategy.eachDependency { DependencyResolveDetails details ->
            def foundMatch = selectedVersion.find { AlignRule rule, String version -> rule.dependencyMatches(details) }
            if (foundMatch) {
                details.useTarget group: details.requested.group, name: details.requested.name, version: foundMatch.value
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
