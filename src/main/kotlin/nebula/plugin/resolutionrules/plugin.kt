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
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap

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
            if (ignoredConfigurationPrefixes.any { config.name.startsWith(it) }) {
                return@all
            }

            if (ignoredConfigurationSuffixes.any { config.name.endsWith(it) }) {
                return@all
            }

            var dependencyRulesApplied = false
            project.onExecute {
                val ruleSet = extension.ruleSet()
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
                        dependencyRulesApplied = true
                    }
                }
            }
        }
    }

}

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
            configuration.resolve().stream().use { stream ->
                return stream.flatMap { file ->
                    when (file.extension) {
                        "json" -> {
                            Logger.debug("nebula.resolution-rules uses: {}", file.name)
                            Stream.of(file.absolutePath to file.readBytes())
                        }
                        "jar", "zip" -> {
                            Logger.info("nebula.resolution-rules is using ruleset: {}", file.name)
                            val zipFile = ZipFile(file)
                            Collections.list(zipFile.entries()).stream()
                                .onClose(zipFile::close)
                                .flatMap { entry ->
                                    val entryFile = File(entry.name)
                                    if (entryFile.extension == "json") {
                                        Stream.of("${file.absolutePath}!${entry.name}" to zipFile.getInputStream(entry).readBytes())
                                    } else Stream.empty()
                                }
                        }
                        else -> {
                            Logger.debug("Unsupported rules file extension for {}", file)
                            Stream.empty()
                        }
                    }
                }.parallel()
                    .map { (path, bytes) ->
                        val filePath = if (path.contains("!")) path.substringAfter("!") else path
                        val file = File(filePath)
                        val ruleSetName = file.nameWithoutExtension
                        Logger.debug("Using {} ({}) a dependency rules source", ruleSetName, path)
                        Mapper.readValue<RuleSet>(bytes).withName(ruleSetName)
                    }.collect(
                        Collectors.toMap(
                            { it.name },
                            { it },
                            { r1, r2 ->
                                Logger.info("Found rules with the same name. Overriding existing ruleset {}", r1.name)
                                r2
                            },
                            { LinkedHashMap() })
                    )
            }
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
        val service = NebulaResolutionRulesService.registerService(project).get()
        val rulesByFile = service.parameters
            .getResolutionRules()
            .get()
            .byFile
        return rulesByFile.filterKeys { ruleSet ->
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
}
