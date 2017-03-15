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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.nebula.dependencybase.DependencyBasePlugin
import com.netflix.nebula.dependencybase.DependencyManagement
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.File
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ResolutionRulesPlugin : Plugin<Project> {
    val logger: Logger = Logging.getLogger(ResolutionRulesPlugin::class.java)
    lateinit var project: Project
    lateinit var rulesConfiguration: Configuration
    lateinit var extension: NebulaResolutionRulesExtension
    lateinit var mapper: ObjectMapper
    val ruleSet: RuleSet by lazy {
        rulesFromConfiguration(rulesConfiguration, extension)
    }

    companion object Constants {
        const val RESOLUTION_RULES_CONFIG_NAME = "resolutionRules"
        const val SPRING_VERSION_MANAGEMENT_CONFIG_NAME = "versionManagement"
        const val JSON_EXT = ".json"
        const val JAR_EXT = ".jar"
        const val ZIP_EXT = ".zip"
        const val OPTIONAL_PREFIX = "optional-"
        lateinit var insight: DependencyManagement
    }

    override fun apply(project: Project) {
        this.project = project
        this.project.plugins.apply(DependencyBasePlugin::class.java)
        ResolutionRulesPlugin.insight = this.project.extensions.extraProperties.get("nebulaDependencyBase") as DependencyManagement
        rulesConfiguration = project.configurations.create(RESOLUTION_RULES_CONFIG_NAME)
        extension = project.extensions.create("nebulaResolutionRules", NebulaResolutionRulesExtension::class.java)
        mapper = objectMapper()

        project.configurations.all { config ->
            if (config.name == RESOLUTION_RULES_CONFIG_NAME || config.name == SPRING_VERSION_MANAGEMENT_CONFIG_NAME) {
                return@all
            }

            var afterEvaluateRulesApplied = false
            project.afterExecute {
                if (config.state != Configuration.State.UNRESOLVED) {
                    logger.warn("Dependency resolution rules will not be applied to $config, it was resolved before the project was evaluated")
                } else if (config.allDependencies.isEmpty()) {
                    logger.debug("Skipping afterEvaluate rules for $config - No dependencies are configured")
                } else {
                    ruleSet.dependencyRules().forEach { rule ->
                        rule.apply(project, config, config.resolutionStrategy, extension)
                    }
                    afterEvaluateRulesApplied = true
                }
            }

            config.incoming.beforeResolve {
                if (config.allDependencies.isEmpty()) {
                    logger.debug("Skipping beforeResolve rules for $config - No dependencies are configured")
                } else if (!afterEvaluateRulesApplied) {
                    logger.debug("Skipping beforeResolve rules for $config - afterEvaluate rules have not been applied")
                } else {
                    ruleSet.beforeResolveRules().forEach { rule ->
                        rule.apply(project, config, config.resolutionStrategy, extension)
                    }
                }
            }
        }
    }

    fun Project.afterExecute(action: () -> Unit) {
        if (state.executed) {
            action()
        } else {
            afterEvaluate { action() }
        }
    }

    fun rulesFromConfiguration(configuration: Configuration, extension: NebulaResolutionRulesExtension): RuleSet {
        val rules = LinkedHashMap<String, RuleSet>()
        val files = configuration.resolve()
        if (files.isEmpty()) {
            logger.debug("No resolution rules have been added to the '{}' configuration", configuration.name)
        }
        for (file in files) {
            ResolutionRulesPlugin.insight.addPluginMessage("nebula.resolution-rules uses: ${file.name}")
            if (isIncludedRuleFile(file.name, extension)) {
                rules.putRules(parseJsonFile(file))
            } else if (file.name.endsWith(JAR_EXT) || file.name.endsWith(ZIP_EXT)) {
                val zip = ZipFile(file)
                zip.use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (isIncludedRuleFile(entry.name, extension)) {
                            rules.putRules(parseJsonStream(zip, entry))
                        }
                    }
                }
            } else {
                logger.debug("Unsupported rules file extension for $file")
            }
        }
        val flatRules = rules.values.flatten()
        return flatRules
    }

    fun MutableMap<String, RuleSet>.putRules(ruleSet: RuleSet) {
        if (put(ruleSet.name!!, ruleSet) != null) {
            logger.info("Found rules with the same name. Overriding existing ruleset ${ruleSet.name}")
        }
    }

    fun isIncludedRuleFile(filename: String, extension: NebulaResolutionRulesExtension): Boolean {
        if (filename.endsWith(JSON_EXT)) {
            val ruleSet = ruleSetName(filename)
            if (ruleSet.startsWith(OPTIONAL_PREFIX)) {
                val ruleSetWithoutPrefix = ruleSet.substring(OPTIONAL_PREFIX.length)
                return extension.optional.contains(ruleSetWithoutPrefix)
            } else if (!extension.include.isEmpty()) {
                return extension.include.contains(ruleSet)
            } else {
                return !extension.exclude.contains(ruleSet)
            }
        }
        return false
    }

    fun ruleSetName(filename: String) = filename.substring(0, filename.lastIndexOf(JSON_EXT))

    fun parseJsonFile(file: File): RuleSet {
        val ruleSetName = ruleSetName(file.name)
        logger.debug("Using $ruleSetName (${file.name}) a dependency rules source")
        return mapper.readValue<RuleSet>(file).withName(ruleSetName)
    }

    fun parseJsonStream(zip: ZipFile, entry: ZipEntry): RuleSet {
        val ruleSetName = ruleSetName(File(entry.name).name)
        logger.debug("Using $ruleSetName (${zip.name}) a dependency rules source")
        return mapper.readValue<RuleSet>(zip.getInputStream(entry)).withName(ruleSetName)
    }
}

open class NebulaResolutionRulesExtension {
    var include = ArrayList<String>()
    var optional = ArrayList<String>()
    var exclude = ArrayList<String>()
}
