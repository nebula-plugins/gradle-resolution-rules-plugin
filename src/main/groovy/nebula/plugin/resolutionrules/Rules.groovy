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

import jdk.nashorn.internal.runtime.Version
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.specs.Specs
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

import java.util.regex.Pattern

public class Rules {
    List<ReplaceRule> replace
    List<SubstituteRule> substitute
    List<RejectRule> reject
    List<DenyRule> deny
    List<AlignRule> align
    List<ExcludeRule> exclude

    public List<ProjectRule> projectRules() {
        return replace
    }

    public List<ConfigurationRule> configurationRules() {
        return [deny, exclude].flatten()
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
    String ruleSet
    String reason
    String author
    DateTime date

    BaseRule(String ruleSet, Map map) {
        this.ruleSet = ruleSet
        reason = map.reason
        author = map.author
        date = new DateTime(map.date as String).toDateTime(DateTimeZone.UTC)
    }
}

class ReplaceRule extends BaseRule implements ProjectRule {
    String module
    String with

    ReplaceRule(String ruleSet, Map map) {
        super(ruleSet, map)
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

    SubstituteRule(String ruleSet, Map map) {
        super(ruleSet, map)
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

    RejectRule(String ruleSet, Map map) {
        super(ruleSet, map)
        module = map.module
    }

    @Override
    public void apply(ResolutionStrategy resolutionStrategy) {
        ModuleVersionIdentifier moduleId = ModuleVersionIdentifier.valueOf(module)
        resolutionStrategy.componentSelection.all({ selection ->
            ModuleComponentIdentifier candidate = selection.candidate
            if (candidate.group == moduleId.organization && candidate.module == moduleId.name) {
                if (!moduleId.hasVersion() || candidate.version == moduleId.version) {
                    selection.reject("Rejected by resolution rule $ruleSet - $reason")
                }
            }
        })
    }
}

class DenyRule extends BaseRule implements ConfigurationRule {
    String module

    DenyRule(String ruleSet, Map map) {
        super(ruleSet, map)
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
    String group
    Collection<String> includes
    Collection<String> excludes
    String match

    AlignRule(String ruleSet, Map map) {
        super(ruleSet, map)
        group = map.group
        includes = map.includes ?: []
        excludes = map.excludes ?: []
        match = map.match
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

    @Override
    String toString() {
        StringBuilder sb = new StringBuilder("[group: $group")
        if (!includes.isEmpty()) {
            sb.append ", includes: $includes]"
        }
        if (!excludes.isEmpty()) {
            sb.append ", excludes: $excludes"
        }
        if (match) {
            sb.append(", match: $match")
        }
        sb.append("]")
        return sb.toString()
    }
}

class AlignRules implements ProjectConfigurationRule {
    private static final Logger LOGGER = Logging.getLogger(AlignRules)

    List<AlignRule> aligns

    @Override
    void apply(Project project, ResolutionStrategy resolutionStrategy, Configuration configuration, NebulaResolutionRulesExtension extension) {
        if (aligns.size() == 0) { // don't do extra resolves if there are no align rules
            return
        }

        def copy = configuration.copyRecursive()
        // Hacky workaround to prevent Gradle from attempting to resolve a project dependency as an external dependency
        copy.exclude group: project.group.toString(), module: project.name
        Set<ResolvedArtifact> artifacts
        def resolvedConfiguration = copy.resolvedConfiguration
        if (resolvedConfiguration.hasError()) {
            def lenientConfiguration = resolvedConfiguration.lenientConfiguration
            project.logger.info("Resolution rules could not resolve all dependencies to align in configuration '${configuration.name}' should also fail to resolve")
            artifacts = lenientConfiguration.getArtifacts(Specs.SATISFIES_ALL)
        } else {
            artifacts = resolvedConfiguration.resolvedArtifacts
        }

        def moduleVersions = artifacts.findAll {
            // Exclude project artifacts from alignment
            !(it.id.componentIdentifier instanceof ProjectComponentIdentifier)
        }.collect { it.moduleVersion }


        def comparator = new DefaultVersionComparator()
        def scheme = new DefaultVersionSelectorScheme(comparator)
        Map<AlignRule, String> selectedVersion = [:]
        aligns.each { AlignRule align ->
            def matches = moduleVersions.findAll { ResolvedModuleVersion dep -> align.resolvedMatches(dep) }
            if (matches) {
                selectedVersion[align] = alignedVersion(align, matches, configuration, scheme, comparator.asStringComparator())
            }
        }

        resolutionStrategy.eachDependency(new Action<DependencyResolveDetails>() {
            @Override
            void execute(DependencyResolveDetails details) {
                def foundMatch = selectedVersion.find { AlignRule rule, String version -> rule.dependencyMatches(details) }
                if (foundMatch) {
                    def rule = foundMatch.key
                    def version = foundMatch.value
                    if (version != matchedVersion(rule, details.requested.version)) {
                        LOGGER.info("Resolution rules ruleset ${rule.ruleSet} rule $rule aligning ${details.requested.group}:${details.requested.name} to $version")
                        details.useVersion version
                    }
                }
            }
        })
    }

    private
    static String alignedVersion(AlignRule rule, List<ResolvedModuleVersion> moduleVersions, Configuration configuration,
                                 VersionSelectorScheme scheme, Comparator<String> comparator) {
        def versions = moduleVersions.collect { matchedVersion(rule, it.id.version) }.toUnique()
        def highestVersion = versions.max { String a, String b -> comparator.compare(a, b) }

        List<ModuleVersionSelector> forced = moduleVersions.findResults { moduleVersion ->
            configuration.resolutionStrategy.forcedModules.find {
                def id = moduleVersion.id
                it.group == id.group && it.name == id.name
            }
        }
        if (forced) {
            def forcedVersions = forced.collect { it.version }.toUnique()
            def (dynamicVersions, staticVersions) = forcedVersions.split { version ->
                def selector = scheme.parseSelector(version)
                selector.dynamic
            }
            if (!dynamicVersions.isEmpty()) {
                LOGGER.warn("Resolution rules ruleset ${rule.ruleSet} align rule $rule is unable to honor forced versions $dynamicVersions. For a force to take precedence on an align rule, it must use a static version")
            }
            if (!staticVersions.isEmpty()) {
                def forcedVersion = staticVersions.min { String a, String b -> comparator.compare(a, b) }
                LOGGER.info("Found force(s) $forced that supersede resolution ruleset ${rule.ruleSet} align rule $rule. Will use $forcedVersion instead of $highestVersion")
                return forcedVersion
            } else {
                LOGGER.warn("No static forces found for ruleset ${rule.ruleSet} align rule $rule. Falling back to default alignment logic")
            }
        }
        return highestVersion
    }

    private static String matchedVersion(AlignRule rule, String version) {
        def match = rule.match
        if (match) {
            def pattern = VersionMatcher.values().find {
                it.name() == match
            } ? VersionMatcher.valueOf(match).pattern() : match
            def matcher = version =~ pattern
            if (matcher) {
                return matcher.group()
            } else {
                LOGGER.warn("Resolution rules ruleset ${rule.ruleSet} align rule $rule is unable to honor match. $match does not match $version. Will use $version")
            }
        }
        return version
    }

    static enum VersionMatcher {
        EXCLUDE_SUFFIXES("^(\\d+\\.)?(\\d+\\.)?(\\*|\\d+)")

        private final pattern

        private VersionMatcher(String regex) {
            pattern = Pattern.compile(regex)
        }

        public Pattern pattern() {
            return pattern
        }
    }
}

class ExcludeRule extends BaseRule implements ConfigurationRule {
    private static final Logger LOGGER = Logging.getLogger(ExcludeRule)

    ModuleIdentifier moduleId

    ExcludeRule(String ruleSet, Map map) {
        super(ruleSet, map)
        moduleId = ModuleIdentifier.valueOf(map.module as String)
    }

    @Override
    public void apply(Configuration configuration) {
        LOGGER.info("Resolution rule ruleset $ruleSet excluding ${moduleId.organization}:${moduleId.name}")
        configuration.exclude(group: moduleId.organization, module: moduleId.name)
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
