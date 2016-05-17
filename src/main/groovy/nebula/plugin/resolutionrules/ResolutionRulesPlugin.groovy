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
 *
 */
package nebula.plugin.resolutionrules

import groovy.json.JsonSlurper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolutionStrategy
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ResolutionRulesPlugin implements Plugin<Project> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResolutionRulesPlugin)
    private static final String CONFIGURATION_NAME = "resolutionRules"
    private static final String JSON_EXT = ".json"
    private static final String OPTIONAL_PREFIX = "optional-"

    private Project project
    private Rules rules
    private Configuration configuration
    private NebulaResolutionRulesExtension extension

    public void apply(Project project) {
        this.project = project
        configuration = project.configurations.create(CONFIGURATION_NAME)
        extension = project.extensions.create('nebulaResolutionRules', NebulaResolutionRulesExtension)

        project.configurations.all ( { Configuration config ->
            if (config.name == CONFIGURATION_NAME || config.name == 'versionManagement') {
                return
            }
            if (config.state != Configuration.State.UNRESOLVED) {
                LOGGER.warn("Configuration '{}' has been resolved. Dependency resolution rules will not be applied", config.name)
                return
            }

            config.incoming.beforeResolve {
                config.resolutionStrategy { ResolutionStrategy rs ->
                    def resolutionRules = getRules()
                    resolutionRules.configurationRules().each { ConfigurationRule rule ->
                        rule.apply(config)
                    }
                    resolutionRules.resolutionRules().each { ResolutionRule rule ->
                        rule.apply(rs)
                    }
                    resolutionRules.projectConfigurationRules().each { ProjectConfigurationRule rule ->
                        rule.apply(project, rs, config, extension)
                    }
                }
            }
        } )
    }

    Rules getRules() {
        if (rules == null) {
            rules = rulesFromConfiguration(configuration, extension)
            rules.projectRules().each { ProjectRule rule -> rule.apply(project) }
        }

        return rules
    }

    private static Rules rulesFromConfiguration(Configuration configuration, NebulaResolutionRulesExtension extension) {
        List<Rules> rules = new ArrayList<Rules>();
        Set<File> files = configuration.resolve()
        if (files.isEmpty()) {
            LOGGER.warn("No resolution rules have been added to the '{}' configuration", configuration.name)
        }
        for (file in files) {
            if (isIncludedRuleFile(file.name, extension)) {
                ResolutionJsonValidator.validateJsonFile(file)
                LOGGER.info("Using $file as a dependency rules source")
                rules.add(parseJsonFile(file))
            } else if (file.name.endsWith(".jar") || file.name.endsWith(".zip")) {
                LOGGER.info("Using $file as a dependency rules source")
                ZipFile jar = new ZipFile(file)
                try {
                    Enumeration<? extends ZipEntry> entries = jar.entries()
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement()
                        if (isIncludedRuleFile(entry.name, extension)) {
                            ResolutionJsonValidator.validateJsonStream(jar.getInputStream(entry))
                            rules.add(parseJsonStream(jar.getInputStream(entry)))
                        }
                    }
                } finally {
                    jar.close()
                }
            } else {
                LOGGER.debug("Unsupported rules file extension for $file")
            }
        }
        return flattenRules(rules)
    }

    private static boolean isIncludedRuleFile(String filename, NebulaResolutionRulesExtension extension) {
        if (filename.endsWith(JSON_EXT)) {
            String nameWithoutExtension = filename.substring(0, filename.lastIndexOf(JSON_EXT))
            if (nameWithoutExtension.startsWith(OPTIONAL_PREFIX)) {
                String nameWithoutPrefix = nameWithoutExtension.substring(OPTIONAL_PREFIX.length())
                return extension.optional.contains(nameWithoutPrefix)
            } else if (!extension.include.isEmpty()) {
                return extension.include.contains(nameWithoutExtension)
            } else {
                return !extension.exclude.contains(nameWithoutExtension)
            }
        }
        return false
    }

    static Rules parseJsonFile(File file) {
        rulesFromJson(new JsonSlurper().parse(file) as Map)
    }

    static Rules parseJsonText(String json) {
        rulesFromJson(new JsonSlurper().parseText(json) as Map)
    }

    static Rules parseJsonStream(InputStream stream) {
        rulesFromJson(new JsonSlurper().parse(stream) as Map)
    }

    private static Rules rulesFromJson(Map json) {
        Rules rules = new Rules()
        rules.replace = json.replace.collect { new ReplaceRule(it) }
        rules.substitute = json.substitute.collect { new SubstituteRule(it) }
        rules.reject = json.reject.collect { new RejectRule(it) }
        rules.deny = json.deny.collect { new DenyRule(it) }
        rules.align = json.align.collect { new AlignRule(it) }
        rules.exclude = json.exclude.collect { new ExcludeRule(it) }

        rules
    }

    private static Rules flattenRules(Iterable<Rules> rules) {
        List<ReplaceRule> replace = rules.collectMany { it.replace }.flatten() as List<ReplaceRule>
        List<SubstituteRule> substitute = rules.collectMany { it.substitute }.flatten() as List<SubstituteRule>
        List<RejectRule> reject = rules.collectMany { it.reject }.flatten() as List<RejectRule>
        List<DenyRule> deny = rules.collectMany { it.deny }.flatten() as List<DenyRule>
        List<AlignRule> align = rules.collectMany { it.align }.flatten() as List<AlignRule>
        List<ExcludeRule> exclude = rules.collectMany { it.exclude }.flatten() as List<ExcludeRule>

        new Rules(replace: replace, substitute: substitute, reject: reject, deny: deny, align: align, exclude: exclude)
    }
}
