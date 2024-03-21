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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File
import java.io.Serializable
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject

const val RESOLUTION_RULES_CONFIG_NAME = "resolutionRules"

class ResolutionRulesPlugin : Plugin<Project> {
    private lateinit var project: Project
    private lateinit var configurations: ConfigurationContainer
    private lateinit var extension: NebulaResolutionRulesExtension
    private val ignoredConfigurationPrefixes = listOf(
        RESOLUTION_RULES_CONFIG_NAME,
        SPRING_VERSION_MANAGEMENT_CONFIG_NAME,
        NEBULA_RECOMMENDER_BOM_CONFIG_NAME,
        SCALA_INCREMENTAL_ANALYSIS_CONFIGURATION_PREFIX,
        KTLINT_CONFIGURATION_PREFIX,
        REPOSITORY_CONTENT_DESCRIPTOR_CONFIGURATION_PREFIX,
        BOOT_ARCHIVES_CONFIGURATION_NAME,
        ARCHIVES_CONFIGURATION_NAME,
    )
    private val ignoredConfigurationSuffixes = listOf(PMD_CONFIGURATION_SUFFIX)

    companion object {
        val Logger: Logger = Logging.getLogger(ResolutionRulesPlugin::class.java)

        const val NEBULA_RECOMMENDER_BOM_CONFIG_NAME: String = "nebulaRecommenderBom"
        const val SPRING_VERSION_MANAGEMENT_CONFIG_NAME = "versionManagement"
        const val KTLINT_CONFIGURATION_PREFIX = "ktlint"
        const val PMD_CONFIGURATION_SUFFIX = "PmdAuxClasspath"
        const val SCALA_INCREMENTAL_ANALYSIS_CONFIGURATION_PREFIX = "incrementalScalaAnalysis"
        const val REPOSITORY_CONTENT_DESCRIPTOR_CONFIGURATION_PREFIX = "repositoryContentDescriptor"
        const val BOOT_ARCHIVES_CONFIGURATION_NAME = "bootArchives"
        const val ARCHIVES_CONFIGURATION_NAME = "archives"
        const val JSON_EXT = ".json"
        const val JAR_EXT = ".jar"
        const val ZIP_EXT = ".zip"
        const val OPTIONAL_PREFIX = "optional-"
    }

    override fun apply(project: Project) {
        this.project = project
        configurations = project.configurations
        extension =
            project.extensions.create("nebulaResolutionRules", NebulaResolutionRulesExtension::class.java, project)

        val rootProject = project.rootProject
        val configuration = project.configurations.maybeCreate(RESOLUTION_RULES_CONFIG_NAME)
        if (project != rootProject) {
            configuration.isCanBeConsumed = false
            val rootProjectDependency = project.dependencies.project(
                mapOf("path" to rootProject.path, "configuration" to RESOLUTION_RULES_CONFIG_NAME)
            )
            configuration.withDependencies { dependencies ->
                dependencies.add(rootProjectDependency)
            }
        }
        if (rootProject.extensions.findByType(NebulaResolutionRulesExtension::class.java) == null) {
            rootProject.extensions.create(
                "nebulaResolutionRules",
                NebulaResolutionRulesExtension::class.java,
                rootProject
            )
        }

        project.configurations.all { config ->
            project.onExecute {
                val ruleSet = extension.ruleSet()
                applyRuleSet(ruleSet, config)
            }
        }
    }

    fun applyRuleSet(ruleNames: ArrayList<String>, config: Configuration) {
        val ruleSet = extension.ruleSetByNames(ruleNames)
        applyRuleSet(ruleSet, config)
    }
    private fun applyRuleSet(ruleSet: RuleSet, config: Configuration) {
        if (ignoredConfigurationPrefixes.any { config.name.startsWith(it) }) {
            return
        }

        if (ignoredConfigurationSuffixes.any { config.name.endsWith(it) }) {
            return
        }
        when {
            config.state != Configuration.State.UNRESOLVED || config.getObservedState() != Configuration.State.UNRESOLVED -> Logger.warn(
                "Dependency resolution rules will not be applied to $config, it was resolved before the project was executed"
            )
            else -> {
                ruleSet.dependencyRulesPartOne().forEach { rule ->
                    rule.apply(project, config, config.resolutionStrategy, extension)
                }

                ruleSet.dependencyRulesPartTwo().forEach { rule ->
                    rule.apply(project, config, config.resolutionStrategy, extension)
                }
            }
        }
    }

}

@Suppress("UnstableApiUsage")
abstract class NebulaResolutionRulesService : BuildService<NebulaResolutionRulesService.Params> {
    companion object {
        private val Logger: Logger = Logging.getLogger(NebulaResolutionRulesService::class.java)
        private val Mapper = objectMapper()

        fun registerService(project: Project): Provider<NebulaResolutionRulesService> {
            return project.gradle.sharedServices.registerIfAbsent(
                "nebulaResolutionRules",
                NebulaResolutionRulesService::class.java
            ) { spec ->
                val resolutionRules = resolveResolutionRules(project)
                spec.parameters.getResolutionRules().set(ResolutionRules(resolutionRules))
            }
        }

        private fun resolveResolutionRules(project: Project): Map<String, RuleSet> {
            val configuration = project.configurations.getByName(RESOLUTION_RULES_CONFIG_NAME)
            val files = configuration.resolve()
            val rules = LinkedHashMap<String, RuleSet>()
            for (file in files) {
                val filename = file.name
                Logger.debug("nebula.resolution-rules uses: $filename")
                if (filename.endsWith(ResolutionRulesPlugin.JSON_EXT)) {
                    rules.putRules(Mapper.parseJsonFile(file))
                } else if (filename.endsWith(ResolutionRulesPlugin.JAR_EXT) || filename.endsWith(ResolutionRulesPlugin.ZIP_EXT)) {
                    Logger.info("nebula.resolution-rules is using ruleset: $filename")
                    ZipFile(file).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (entry.name.endsWith(ResolutionRulesPlugin.JSON_EXT)) {
                                rules.putRules(Mapper.parseJsonStream(zip, entry))
                            }
                        }
                    }
                } else {
                    Logger.debug("Unsupported rules file extension for $file")
                }
            }
            return rules
        }

        private fun MutableMap<String, RuleSet>.putRules(ruleSet: RuleSet) {
            if (put(ruleSet.name!!, ruleSet) != null) {
                Logger.info("Found rules with the same name. Overriding existing ruleset ${ruleSet.name}")
            }
        }

        private fun ruleSetName(filename: String) =
            filename.substring(0, filename.lastIndexOf(ResolutionRulesPlugin.JSON_EXT))

        private fun ObjectMapper.parseJsonFile(file: File): RuleSet {
            val ruleSetName = ruleSetName(file.name)
            Logger.debug("Using $ruleSetName (${file.name}) a dependency rules source")
            return readValue<RuleSet>(file).withName(ruleSetName)
        }

        private fun ObjectMapper.parseJsonStream(zip: ZipFile, entry: ZipEntry): RuleSet {
            val ruleSetName = ruleSetName(File(entry.name).name)
            Logger.debug("Using $ruleSetName (${zip.name}) a dependency rules source")
            return readValue<RuleSet>(zip.getInputStream(entry)).withName(ruleSetName)
        }
    }

    interface Params : BuildServiceParameters {
        fun getResolutionRules(): Property<ResolutionRules>
    }

    class ResolutionRules(val byFile: Map<String, RuleSet>) : Serializable
}

open class NebulaResolutionRulesExtension @Inject constructor(private val project: Project) {
    var include = ArrayList<String>()
        set(value) {
            field.addAll(value)
        }

    var optional = ArrayList<String>()
        set(value) {
            field.addAll(value)
        }
    var exclude = ArrayList<String>()
        // Setter should add to the existing array rather than replacing all values
        set(value) {
            field.addAll(value)
        }

    fun ruleSet(): RuleSet {
        return getAllRuleSets().filterKeys { ruleSet ->
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

    fun ruleSetByNames(names: ArrayList<String>): RuleSet {
        return getAllRuleSets().filterKeys { ruleSet ->
            names.contains(ruleSet)
        }.values.flatten()
    }

    private fun getAllRuleSets(): Map<String, RuleSet> {
        val service = NebulaResolutionRulesService.registerService(project).get()
        return service.parameters
            .getResolutionRules()
            .get()
            .byFile
    }
}
