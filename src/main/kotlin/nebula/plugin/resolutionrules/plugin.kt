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
import com.netflix.nebula.interop.onExecute
import com.netflix.nebula.interop.onResolve
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.File
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

const val RESOLUTION_RULES_CONFIG_NAME = "resolutionRules"

class ResolutionRulesPlugin : Plugin<Project> {
    private val logger: Logger = Logging.getLogger(ResolutionRulesPlugin::class.java)
    private val ruleSet: RuleSet by lazy {
        rulesFromConfiguration(project, extension)
    }

    private lateinit var project: Project
    private lateinit var configurations: ConfigurationContainer
    private lateinit var extension: NebulaResolutionRulesExtension
    private lateinit var mapper: ObjectMapper
    private lateinit var insight: DependencyManagement

    companion object Constants {
        const val SPRING_VERSION_MANAGEMENT_CONFIG_NAME = "versionManagement"
        const val JSON_EXT = ".json"
        const val JAR_EXT = ".jar"
        const val ZIP_EXT = ".zip"
        const val OPTIONAL_PREFIX = "optional-"
    }

    override fun apply(project: Project) {
        this.project = project
        project.plugins.apply(DependencyBasePlugin::class.java)
        configurations = project.configurations
        insight = project.extensions.extraProperties.get("nebulaDependencyBase") as DependencyManagement
        extension = project.extensions.create("nebulaResolutionRules", NebulaResolutionRulesExtension::class.java)
        mapper = objectMapper()

        val rootProject = project.rootProject
        rootProject.configurations.maybeCreate(RESOLUTION_RULES_CONFIG_NAME)
        if (rootProject.extensions.findByType(NebulaResolutionRulesExtension::class.java) == null) {
            rootProject.extensions.create("nebulaResolutionRules", NebulaResolutionRulesExtension::class.java)
        }

        project.configurations.all { config ->
            if (config.name == RESOLUTION_RULES_CONFIG_NAME || config.name == SPRING_VERSION_MANAGEMENT_CONFIG_NAME) {
                return@all
            }

            var dependencyRulesApplied = false
            project.onExecute {
                dependencyRulesApplied = applyRulesToDependencies(config, project)
            }

            // 2nd pass after taskGraph has been run to support Android manifest merge tasks
            project.gradle.taskGraph.afterTask {
                if (!dependencyRulesApplied) {
                    dependencyRulesApplied = applyRulesToDependencies(config, project)
                }
            }

            config.onResolve {
                if (config.allDependencies.isEmpty()) {
                    logger.debug("Skipping resolve rules for $config - No dependencies are configured")
                } else if (!dependencyRulesApplied) {
                    logger.debug("Skipping resolve rules for $config - dependency rules have not been applied")
                } else {
                    ruleSet.resolveRules().forEach { rule ->
                        rule.apply(project, config, config.resolutionStrategy, extension, insight)
                    }
                }
           }
        }
    }

    private fun applyRulesToDependencies(config: Configuration, project: Project): Boolean {
        var dependencyRulesApplied = false;

        when {
            config.state != Configuration.State.UNRESOLVED -> logger.warn("Dependency resolution rules will not be applied to $config, it was resolved before the project was executed")
            config.allDependencies.isEmpty() -> logger.debug("Skipping dependency rules for $config - No dependencies are configured")
            else -> {
                ruleSet.dependencyRules().forEach { rule ->
                    rule.apply(project, config, config.resolutionStrategy, extension, insight)
                }
                dependencyRulesApplied = true
            }
        }

        return dependencyRulesApplied;
    }

    private fun rulesFromConfiguration(project: Project, extension: NebulaResolutionRulesExtension): RuleSet {
        val rules = LinkedHashMap<String, RuleSet>()
        val files = extension.ruleFiles(project)
        for (file in files) {
            insight.addPluginMessage("nebula.resolution-rules uses: ${file.name}")
            if (isIncludedRuleFile(file.name, extension)) {
                rules.putRules(parseJsonFile(file))
            } else if (file.name.endsWith(JAR_EXT) || file.name.endsWith(ZIP_EXT)) {
                ZipFile(file).use { zip ->
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
        return rules.values.flatten()
    }

    private fun MutableMap<String, RuleSet>.putRules(ruleSet: RuleSet) {
        if (put(ruleSet.name!!, ruleSet) != null) {
            logger.info("Found rules with the same name. Overriding existing ruleset ${ruleSet.name}")
        }
    }

    private fun isIncludedRuleFile(filename: String, extension: NebulaResolutionRulesExtension): Boolean {
        if (filename.endsWith(JSON_EXT)) {
            val ruleSet = ruleSetName(filename)
            return if (ruleSet.startsWith(OPTIONAL_PREFIX)) {
                val ruleSetWithoutPrefix = ruleSet.substring(OPTIONAL_PREFIX.length)
                extension.optional.contains(ruleSetWithoutPrefix)
            } else if (!extension.include.isEmpty()) {
                extension.include.contains(ruleSet)
            } else {
                !extension.exclude.contains(ruleSet)
            }
        }
        return false
    }

    private fun ruleSetName(filename: String) = filename.substring(0, filename.lastIndexOf(JSON_EXT))

    private fun parseJsonFile(file: File): RuleSet {
        val ruleSetName = ruleSetName(file.name)
        logger.debug("Using $ruleSetName (${file.name}) a dependency rules source")
        return mapper.readValue<RuleSet>(file).withName(ruleSetName)
    }

    private fun parseJsonStream(zip: ZipFile, entry: ZipEntry): RuleSet {
        val ruleSetName = ruleSetName(File(entry.name).name)
        logger.debug("Using $ruleSetName (${zip.name}) a dependency rules source")
        return mapper.readValue<RuleSet>(zip.getInputStream(entry)).withName(ruleSetName)
    }
}

open class NebulaResolutionRulesExtension {
    var include = ArrayList<String>()
    var optional = ArrayList<String>()
    var exclude = ArrayList<String>()

    private lateinit var rootProject: Project
    private val ruleFiles by lazy {
        val configuration = rootProject.configurations.getByName(RESOLUTION_RULES_CONFIG_NAME)
        rootProject.copyConfiguration(configuration).resolve()
    }

    fun ruleFiles(project: Project): Set<File> {
        return if (project == project.rootProject) {
            rootProject = project
            ruleFiles
        } else {
            project.rootProject.extensions.getByType(NebulaResolutionRulesExtension::class.java).ruleFiles(project.rootProject)
        }
    }
}
