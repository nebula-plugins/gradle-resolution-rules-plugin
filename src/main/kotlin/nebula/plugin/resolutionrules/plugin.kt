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
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.File
import java.lang.reflect.Field
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

const val RESOLUTION_RULES_CONFIG_NAME = "resolutionRules"

class ResolutionRulesPlugin : Plugin<Project> {
    private val logger: Logger = Logging.getLogger(ResolutionRulesPlugin::class.java)
    private val ruleSet: RuleSet by lazy {
        rulesFromConfiguration(project, extension)
    }
    private val NEBULA_RECOMMENDER_BOM_CONFIG_NAME: String = "nebulaRecommenderBom"
    private lateinit var project: Project
    private lateinit var configurations: ConfigurationContainer
    private lateinit var extension: NebulaResolutionRulesExtension
    private lateinit var mapper: ObjectMapper
    private var reasons: MutableSet<String> = mutableSetOf()
    private val ignoredConfigurationPrefixes = listOf(RESOLUTION_RULES_CONFIG_NAME, SPRING_VERSION_MANAGEMENT_CONFIG_NAME,
            NEBULA_RECOMMENDER_BOM_CONFIG_NAME, SCALA_INCREMENTAL_ANALYSIS_CONFIGURATION_PREFIX, KTLINT_CONFIGURATION_PREFIX)

    companion object Constants {
        fun isCoreAlignmentEnabled() = java.lang.Boolean.getBoolean("nebula.features.coreAlignmentSupport")
        const val SPRING_VERSION_MANAGEMENT_CONFIG_NAME = "versionManagement"
        const val KTLINT_CONFIGURATION_PREFIX = "ktlint"
        const val SCALA_INCREMENTAL_ANALYSIS_CONFIGURATION_PREFIX = "incrementalScalaAnalysis"
        const val JSON_EXT = ".json"
        const val JAR_EXT = ".jar"
        const val ZIP_EXT = ".zip"
        const val OPTIONAL_PREFIX = "optional-"
        const val IGNORED_CONFIGURATIONS_PROPERTY_NAME = "resolutionRulesIgnoredConfigurations"
    }

    override fun apply(project: Project) {
        this.project = project
        configurations = project.configurations
        extension = project.extensions.create("nebulaResolutionRules", NebulaResolutionRulesExtension::class.java)
        mapper = objectMapper()

        if (isCoreAlignmentEnabled()) {
            logger.warn("${project.name}: coreAlignmentSupport feature enabled")
        }

        val rootProject = project.rootProject
        rootProject.configurations.maybeCreate(RESOLUTION_RULES_CONFIG_NAME)
        if (rootProject.extensions.findByType(NebulaResolutionRulesExtension::class.java) == null) {
            rootProject.extensions.create("nebulaResolutionRules", NebulaResolutionRulesExtension::class.java)
        }

        val extraIgnoredConfigurations = mutableListOf<String>()
        if(project.hasProperty(IGNORED_CONFIGURATIONS_PROPERTY_NAME)) {
            val configurationsToIgnore = project.property(IGNORED_CONFIGURATIONS_PROPERTY_NAME).toString().split(',')
            extraIgnoredConfigurations.addAll(configurationsToIgnore)
        }

        project.configurations.all { config ->
            if (ignoredConfigurationPrefixes.any { config.name.startsWith(it) } || extraIgnoredConfigurations.contains(config.name)) {
                return@all
            }

            var dependencyRulesApplied = false
            project.onExecute {
                when {
                    config.state != Configuration.State.UNRESOLVED || config.getObservedState() != Configuration.State.UNRESOLVED -> logger.warn("Dependency resolution rules will not be applied to $config, it was resolved before the project was executed")
                    else -> {
                        ruleSet.dependencyRulesPartOne().forEach { rule ->
                            rule.apply(project, config, config.resolutionStrategy, extension, reasons)
                        }

                        ruleSet.dependencyRulesPartTwo().forEach { rule ->
                            rule.apply(project, config, config.resolutionStrategy, extension, reasons)
                        }
                        dependencyRulesApplied = true
                    }
                }
            }

            config.onResolve {
                if (!dependencyRulesApplied) {
                    logger.debug("Skipping resolve rules for $config - dependency rules have not been applied")
                } else {
                    ruleSet.resolveRules().forEach { rule ->
                        rule.apply(project, config, config.resolutionStrategy, extension, reasons)
                    }
                }
            }
        }
    }

    private fun rulesFromConfiguration(project: Project, extension: NebulaResolutionRulesExtension): RuleSet {
        val rules = LinkedHashMap<String, RuleSet>()
        val files = extension.ruleFiles(project)
        for (file in files) {
            val message = "nebula.resolution-rules uses: ${file.name}" // TODO: reformat
            reasons.add(message)
            if (isIncludedRuleFile(file.name, extension)) {
                rules.putRules(parseJsonFile(file))
            } else if (file.name.endsWith(JAR_EXT) || file.name.endsWith(ZIP_EXT)) {
                logger.info("nebula.resolution-rules is using ruleset: ${file.name}")
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
