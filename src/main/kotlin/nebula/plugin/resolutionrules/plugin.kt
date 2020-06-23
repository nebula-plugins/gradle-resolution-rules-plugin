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
import javax.inject.Inject

const val RESOLUTION_RULES_CONFIG_NAME = "resolutionRules"

class ResolutionRulesPlugin : Plugin<Project> {
    private val logger: Logger = Logging.getLogger(ResolutionRulesPlugin::class.java)
    private val NEBULA_RECOMMENDER_BOM_CONFIG_NAME: String = "nebulaRecommenderBom"
    private lateinit var project: Project
    private lateinit var configurations: ConfigurationContainer
    private lateinit var extension: NebulaResolutionRulesExtension
    private val ignoredConfigurationPrefixes = listOf(RESOLUTION_RULES_CONFIG_NAME, SPRING_VERSION_MANAGEMENT_CONFIG_NAME,
            NEBULA_RECOMMENDER_BOM_CONFIG_NAME, SCALA_INCREMENTAL_ANALYSIS_CONFIGURATION_PREFIX, KTLINT_CONFIGURATION_PREFIX, REPOSITORY_CONTENT_DESCRIPTOR_CONFIGURATION_PREFIX)

    companion object Constants {
        fun isCoreAlignmentEnabled() = java.lang.Boolean.getBoolean("nebula.features.coreAlignmentSupport")
        const val SPRING_VERSION_MANAGEMENT_CONFIG_NAME = "versionManagement"
        const val KTLINT_CONFIGURATION_PREFIX = "ktlint"
        const val SCALA_INCREMENTAL_ANALYSIS_CONFIGURATION_PREFIX = "incrementalScalaAnalysis"
        const val REPOSITORY_CONTENT_DESCRIPTOR_CONFIGURATION_PREFIX = "repositoryContentDescriptor"
        const val JSON_EXT = ".json"
        const val JAR_EXT = ".jar"
        const val ZIP_EXT = ".zip"
        const val OPTIONAL_PREFIX = "optional-"
    }

    override fun apply(project: Project) {
        this.project = project
        configurations = project.configurations
        extension = project.extensions.create("nebulaResolutionRules", NebulaResolutionRulesExtension::class.java, project)

        if (isCoreAlignmentEnabled()) {
            logger.warn("${project.name}: coreAlignmentSupport feature enabled")
        }

        val rootProject = project.rootProject
        rootProject.configurations.maybeCreate(RESOLUTION_RULES_CONFIG_NAME)
        if (rootProject.extensions.findByType(NebulaResolutionRulesExtension::class.java) == null) {
            rootProject.extensions.create("nebulaResolutionRules", NebulaResolutionRulesExtension::class.java, rootProject)
        }

        project.configurations.all { config ->
            if (ignoredConfigurationPrefixes.any { config.name.startsWith(it) }) {
                return@all
            }

            var dependencyRulesApplied = false
            project.onExecute {
                val ruleSet = extension.ruleSet()
                when {
                    config.state != Configuration.State.UNRESOLVED || config.getObservedState() != Configuration.State.UNRESOLVED -> logger.warn("Dependency resolution rules will not be applied to $config, it was resolved before the project was executed")
                    else -> {
                        ruleSet.dependencyRulesPartOne().forEach { rule ->
                            rule.apply(project, config, config.resolutionStrategy, extension)
                        }

                        ruleSet.dependencyRulesPartTwo().forEach { rule ->
                            rule.apply(project, config, config.resolutionStrategy, extension)
                        }
                        dependencyRulesApplied = true
                    }
                }
            }

            config.onResolve {
                if (!dependencyRulesApplied) {
                    logger.debug("Skipping resolve rules for $config - dependency rules have not been applied")
                } else {
                    val ruleSet = extension.ruleSet()
                    ruleSet.resolveRules().forEach { rule ->
                        rule.apply(project, config, config.resolutionStrategy, extension)
                    }
                }
            }
        }
    }
}

open class NebulaResolutionRulesExtension @Inject constructor(private val project: Project) {
    companion object {
        private val logger: Logger = Logging.getLogger(ResolutionRulesPlugin::class.java)
        private val mapper = objectMapper()
    }

    var include = ArrayList<String>()
    var optional = ArrayList<String>()
    var exclude = ArrayList<String>()

    private val rulesByFile by lazy {
        check(project == project.rootProject) { "This should only be called on the root project extension" }
        val configuration = project.configurations.getByName(RESOLUTION_RULES_CONFIG_NAME)
        val files = project.copyConfiguration(configuration).resolve()
        val rules = LinkedHashMap<String, RuleSet>()
        for (file in files) {
            val filename = file.name
            logger.debug("nebula.resolution-rules uses: $filename")
            if (filename.endsWith(ResolutionRulesPlugin.JSON_EXT)) {
                rules.putRules(mapper.parseJsonFile(file))
            } else if (filename.endsWith(ResolutionRulesPlugin.JAR_EXT) || filename.endsWith(ResolutionRulesPlugin.ZIP_EXT)) {
                logger.info("nebula.resolution-rules is using ruleset: $filename")
                ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.name.endsWith(ResolutionRulesPlugin.JSON_EXT)) {
                            rules.putRules(mapper.parseJsonStream(zip, entry))
                        }
                    }
                }
            } else {
                logger.debug("Unsupported rules file extension for $file")
            }
        }
        rules
    }

    fun ruleSet(): RuleSet {
        val extension = project.rootProject.extensions.getByType(NebulaResolutionRulesExtension::class.java)
        return extension.rulesByFile.filterKeys { ruleSet ->
            when {
                ruleSet.startsWith(ResolutionRulesPlugin.OPTIONAL_PREFIX) -> {
                    val ruleSetWithoutPrefix = ruleSet.substring(ResolutionRulesPlugin.OPTIONAL_PREFIX.length)
                    optional.contains(ruleSetWithoutPrefix)
                }
                include.isNotEmpty() -> include.contains(ruleSet)
                else -> !exclude.contains(ruleSet)
            }
        }.values.flatten()
    }

    private fun MutableMap<String, RuleSet>.putRules(ruleSet: RuleSet) {
        if (put(ruleSet.name!!, ruleSet) != null) {
            logger.info("Found rules with the same name. Overriding existing ruleset ${ruleSet.name}")
        }
    }

    private fun ruleSetName(filename: String) = filename.substring(0, filename.lastIndexOf(ResolutionRulesPlugin.JSON_EXT))

    private fun ObjectMapper.parseJsonFile(file: File): RuleSet {
        val ruleSetName = ruleSetName(file.name)
        logger.debug("Using $ruleSetName (${file.name}) a dependency rules source")
        return readValue<RuleSet>(file).withName(ruleSetName)
    }

    private fun ObjectMapper.parseJsonStream(zip: ZipFile, entry: ZipEntry): RuleSet {
        val ruleSetName = ruleSetName(File(entry.name).name)
        logger.debug("Using $ruleSetName (${zip.name}) a dependency rules source")
        return readValue<RuleSet>(zip.getInputStream(entry)).withName(ruleSetName)
    }
}
