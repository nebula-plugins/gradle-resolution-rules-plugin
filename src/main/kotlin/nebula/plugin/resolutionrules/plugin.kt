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
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File
import java.io.Serializable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.zip.ZipFile
import javax.inject.Inject

const val RESOLUTION_RULES_CONFIG_NAME = "resolutionRules"

private const val ISOLATED_STATE_PROPERTY = "nebulaResolutionRules.isolatedState"
private const val PROJECT_LOCAL_RULES_PROPERTY = "nebulaResolutionRules.projectLocal"
private const val SELECTED_RULES_MODE = "nebulaResolutionRules.internal.selectedProjectLocalMode"

private fun Project.isolatedStateOverride(): Boolean? =
    (findStringProperty(ISOLATED_STATE_PROPERTY) ?: findStringProperty(PROJECT_LOCAL_RULES_PROPERTY))?.toBoolean()

private fun Project.usesIsolatedRulesState(): Boolean {
    val state = extensions.extraProperties
    if (!state.has(SELECTED_RULES_MODE)) {
        state.set(SELECTED_RULES_MODE, isolatedStateOverride() ?: false)
    }
    return state.get(SELECTED_RULES_MODE) as Boolean
}

class ResolutionRulesPlugin : Plugin<Project> {
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
        const val OPTIONAL_RULES_PROJECT_PROPERTY = "nebulaResolutionRules.optional"
        const val INCLUDE_RULES_PROJECT_PROPERTY = "nebulaResolutionRules.include"
        const val EXCLUDE_RULES_PROJECT_PROPERTY = "nebulaResolutionRules.exclude"

        /**
         * Applies this plugin with project-owned rule state unless the build explicitly sets
         * `nebulaResolutionRules.isolatedState=false`. Intended for convention plugins such as Nebula.
         * The earlier `nebulaResolutionRules.projectLocal` property is a fallback alias.
         *
         * Call before this project's first plugin application or public service registration. Repeated calls
         * are safe when the selected mode agrees; an already-selected legacy mode cannot be switched in place.
         * This configures only [project], including when [project] is the root of a multi-project build.
         */
        @JvmStatic
        fun applyWithIsolatedState(project: Project) {
            val isolatedState = project.isolatedStateOverride() ?: true
            val state = project.extensions.extraProperties
            check(!state.has(SELECTED_RULES_MODE) || state.get(SELECTED_RULES_MODE) == isolatedState) {
                "Resolution rules mode is already selected for ${project.path}. " +
                    "Call ResolutionRulesPlugin.applyWithIsolatedState(project) before applying " +
                    "ResolutionRulesPlugin or registering its service."
            }
            state.set(SELECTED_RULES_MODE, isolatedState)
            project.pluginManager.apply(ResolutionRulesPlugin::class.java)
        }
    }

    override fun apply(project: Project) {
        val projectLocal = project.usesIsolatedRulesState()
        configurations = project.configurations
        extension =
            project.extensions.create("nebulaResolutionRules", NebulaResolutionRulesExtension::class.java, project)
        addRulesFromProjectProperties(project, extension)
        val rootProject = project.rootProject
        val configuration = project.configurations.create(RESOLUTION_RULES_CONFIG_NAME) {
            it.attributes {
                it.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME)
                )
                it.attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    project.objects.named(LibraryElements::class.java, LibraryElements.JAR)
                )
                it.attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    project.objects.named(Category::class.java, Category.LIBRARY)
                )
            }
        }
        if (project != rootProject) {
            configuration.isCanBeConsumed = false
            val rootProjectDependency = project.dependencies.project(
                mapOf("path" to rootProject.path, "configuration" to RESOLUTION_RULES_CONFIG_NAME)
            )
            configuration.withDependencies { dependencies ->
                dependencies.add(rootProjectDependency)
            }
        }
        // Preserve the legacy root-extension side effect unless the build explicitly opts into local state.
        // Short-circuit before accessing another project's extensions on the isolated path.
        if (!projectLocal &&
            rootProject.extensions.findByType(NebulaResolutionRulesExtension::class.java) == null
        ) {
            rootProject.extensions.create(
                "nebulaResolutionRules",
                NebulaResolutionRulesExtension::class.java,
                rootProject
            )
        }

        project.configurations.configureEach { config ->
            if (ignoredConfigurationPrefixes.any { config.name.startsWith(it) }) {
                return@configureEach
            }

            if (ignoredConfigurationSuffixes.any { config.name.endsWith(it) }) {
                return@configureEach
            }

            project.onExecute {
                val service = NebulaResolutionRulesService.registerService(project).get()
                val rules = service.rulesFor(project.path, extension.include, extension.optional, extension.exclude)
                when {
                    config.state != Configuration.State.UNRESOLVED || config.getObservedState() != Configuration.State.UNRESOLVED -> Logger.warn(
                        "Dependency resolution rules will not be applied to $config, it was resolved before the project was executed"
                    )

                    else -> {
                        rules.partOne.forEach { rule ->
                            if (rules.shouldApply(rule)) {
                                rule.apply(project, config, config.resolutionStrategy, extension)
                            }
                        }

                        rules.partTwo.forEach { rule ->
                            rule.apply(project, config, config.resolutionStrategy, extension)
                        }
                    }
                }
            }
        }
    }

    /**
     * Search for optional, include and exclude rules in project properties
     * Add them to the extension if found
     */
    private fun addRulesFromProjectProperties(
        project: Project,
        extension: NebulaResolutionRulesExtension
    ) {
        val optionalRules = project.findStringProperty(OPTIONAL_RULES_PROJECT_PROPERTY)
        optionalRules?.let { rules -> parseRuleNames(rules).forEach { extension.optional.add(it) } }
        val includeRules = project.findStringProperty(INCLUDE_RULES_PROJECT_PROPERTY)
        includeRules?.let { rules -> parseRuleNames(rules).forEach { extension.include.add(it) } }
        val excludeRules = project.findStringProperty(EXCLUDE_RULES_PROJECT_PROPERTY)
        excludeRules?.let { rules -> parseRuleNames(rules).forEach { extension.exclude.add(it) } }
    }

}

/**
 * Caches the preparation needed by project configuration callbacks.
 *
 * [Params] carries the parsed rule data. This service instance owns the filtered rule sets, derived rule lists and
 * replacement application tracking, keeping that runtime state off the extension and the serialized parameters.
 * Gradle creates fresh runtime caches whenever it creates a new service instance.
 *
 * By default, the build-wide registration retains its legacy first-registering-project inputs and shared rule data.
 * With project-local adoption through the property or programmatic API, each project registers its own service
 * and parses separate rules, including their mutable selectors and alignment matchers. State is keyed by project
 * path within the build.
 * Cache entries retain rule data and selection snapshots; they do not retain projects, configurations or extensions.
 * Cache refresh is synchronized per project, and replacement tracking uses a synchronized identity set.
 */
abstract class NebulaResolutionRulesService : BuildService<NebulaResolutionRulesService.Params> {
    private val projectStates = ConcurrentHashMap<String, ProjectRuleState>()

    /**
     * Returns the prepared rules for [projectPath], reusing the previous result while its selections are unchanged.
     *
     * Copies the extension's mutable lists into a snapshot so later DSL edits can be detected by value. A changed
     * snapshot replaces that project's cached selection and derived lists. The returned lists are still applied to
     * each eligible configuration; this cache avoids repeated preparation rather than caching resolution results.
     *
     * @param projectPath the project's path within the build that registered this service
     */
    internal fun rulesFor(
        projectPath: String,
        include: List<String>,
        optional: List<String>,
        exclude: List<String>
    ): PreparedRules {
        val selection = RuleSelection(include.toList(), optional.toList(), exclude.toList())
        val state = projectStates.computeIfAbsent(projectPath) { ProjectRuleState() }
        return state.rulesFor(parameters.getResolutionRules().get().byFile, selection)
    }

    /**
     * A snapshot used both as the cache key and to select rule files.
     *
     * Optional files require explicit opt-in. For other files, a nonempty include list takes precedence over excludes;
     * otherwise every file outside the exclude list is selected. These are the extension's existing selection rules.
     */
    private data class RuleSelection(
        val include: List<String>,
        val optional: List<String>,
        val exclude: List<String>
    ) {
        fun includes(ruleSet: String): Boolean = when {
            ruleSet.startsWith(ResolutionRulesPlugin.OPTIONAL_PREFIX) ->
                optional.contains(ruleSet.substring(ResolutionRulesPlugin.OPTIONAL_PREFIX.length))
            include.isNotEmpty() -> include.contains(ruleSet)
            else -> !exclude.contains(ruleSet)
        }
    }

    /**
     * Holds one project's latest prepared selection and its replacement history for this service instance.
     * Keeping one entry per project allows selection changes without accumulating a cache entry for every snapshot.
     */
    private class ProjectRuleState {
        private var selection: RuleSelection? = null
        private var preparedRules: PreparedRules? = null

        /**
         * Tracks individual parsed replacement declarations by identity, preserving the once-per-project policy.
         * Distinct declarations remain distinct even when their values compare equal. This history survives selection
         * changes, so an already-applied replacement is not replayed to overwrite an intervening declaration.
         */
        private val appliedReplaceRules = Collections.synchronizedSet(
            Collections.newSetFromMap(IdentityHashMap<ReplaceRule, Boolean>())
        )

        /** Publishes a complete prepared selection under this project's lock, independently of other projects. */
        @Synchronized
        fun rulesFor(rulesByFile: Map<String, RuleSet>, selection: RuleSelection): PreparedRules {
            val cached = preparedRules
            if (cached != null && this.selection == selection) {
                return cached
            }
            val ruleSet = rulesByFile.filterKeys(selection::includes).values.flatten()
            return PreparedRules(ruleSet, appliedReplaceRules).also {
                preparedRules = it
                this.selection = selection
            }
        }
    }

    /**
     * Prepared registration data for one project and selection snapshot.
     *
     * Retaining the derived lists lets [SubstituteRules] and [RejectRules] reuse their indexes across configurations.
     * Replacement history is shared with the owning [ProjectRuleState], including after a selection refresh.
     */
    internal class PreparedRules(
        val ruleSet: RuleSet,
        private val appliedReplaceRules: MutableSet<ReplaceRule>
    ) {
        /** Ordered registrations performed before alignment, including the reusable indexed rule wrappers. */
        val partOne: List<Rule> = ruleSet.dependencyRulesPartOne()

        /** Alignment rules kept on the existing second-pass registration path. */
        val partTwo: List<AlignRule> = ruleSet.dependencyRulesPartTwo()

        /**
         * Checks whether to apply [rule], recording the first encounter with a project-scoped replacement.
         * First-pass rules other than replacements are evaluated for every eligible configuration.
         *
         * Call once immediately before applying the rule: checking a replacement also updates its application history.
         */
        fun shouldApply(rule: Rule): Boolean = rule !is ReplaceRule || appliedReplaceRules.add(rule)
    }

    companion object {
        private val Logger: Logger = Logging.getLogger(NebulaResolutionRulesService::class.java)
        private val Mapper = objectMapper

        private const val JSON_EXTENSION = "json"
        private const val JAR_EXTENSION = "jar"
        private const val ZIP_EXTENSION = "zip"

        fun registerService(project: Project): Provider<NebulaResolutionRulesService> {
            // Keep the legacy service and its first-registering-project inputs by default. The opt-in namespace
            // separates every project (including the root) from legacy registrations in mixed-mode builds.
            val serviceName = if (project.usesIsolatedRulesState()) {
                "nebulaResolutionRules.projectLocal${project.path}"
            } else {
                "nebulaResolutionRules"
            }
            return project.gradle.sharedServices.registerIfAbsent(
                serviceName,
                NebulaResolutionRulesService::class.java
            ) { spec ->
                val resolutionRules = resolveResolutionRules(project)
                spec.parameters.getResolutionRules().set(ResolutionRules(resolutionRules))
            }
        }

        private fun resolveResolutionRules(project: Project): Map<String, RuleSet> {
            val configuration = project.configurations.getByName(RESOLUTION_RULES_CONFIG_NAME)
            val ruleFiles = configuration.incoming.files.files.stream()
                .filter { file -> file.exists() }
                .flatMap { file ->
                    val contents = project.providers.fileContents(project.layout.file(project.providers.provider { file }))
                    when (file.extension) {
                        JSON_EXTENSION -> {
                            Logger.debug("nebula.resolution-rules uses: {}", file.name)
                            Stream.of(file.absolutePath to contents.asBytes.get())
                        }

                        ZIP_EXTENSION, JAR_EXTENSION -> {
                            // ZipFile reads through native code, which is not instrumented as a configuration
                            // input. Track the complete archive so cached preparation is invalidated on edits.
                            contents.asBytes.get()
                            processJarOrZip(file)
                        }

                        else -> {
                            Logger.debug("Unsupported rules file extension for {}", file)
                            Stream.empty()
                        }
                    }
                }
            return ruleFiles.parallel()
                .map { (path, bytes) ->
                    val filePath = path.substringAfterLast('!', path)
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

        private fun processJarOrZip(file: File): Stream<Pair<String, ByteArray>> {
            Logger.info("nebula.resolution-rules is using ruleset: {}", file.name)
            val zipFile = ZipFile(file)
            return Collections.list(zipFile.entries()).stream()
                .onClose(zipFile::close)
                .flatMap { entry ->
                    val entryFile = File(entry.name)
                    if (entryFile.extension == JSON_EXTENSION) {
                        Stream.of(
                            "${file.absolutePath}!${entry.name}" to zipFile.getInputStream(entry).readBytes()
                        )
                    } else Stream.empty()
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

    /**
     * Returns the rules selected by the current [include], [optional] and [exclude] values.
     * The extension supplies DSL configuration; the build service owns selection snapshots and prepared-rule caches.
     */
    fun ruleSet(): RuleSet {
        val service = NebulaResolutionRulesService.registerService(project).get()
        return service.rulesFor(project.path, include, optional, exclude).ruleSet
    }
}
