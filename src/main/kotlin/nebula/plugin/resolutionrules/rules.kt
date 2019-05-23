/*
 * Copyright 2016 Netflix, Inc.
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

import com.netflix.nebula.interop.VersionWithSelector
import com.netflix.nebula.interop.action
import netflix.nebula.dependency.recommender.DependencyRecommendationsPlugin
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import java.io.Serializable
import javax.inject.Inject
import org.gradle.api.artifacts.ModuleVersionIdentifier as GradleModuleVersionIdentifier

interface Rule {
    fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, reasons: MutableSet<String>)
}

interface BasicRule : Rule {
    var ruleSet: String?
    val reason: String
    val author: String
    val date: String
}

interface ModuleRule : BasicRule {
    val module: String
}

data class RuleSet(
        var name: String?,
        val replace: List<ReplaceRule> = emptyList(),
        val substitute: List<SubstituteRule> = emptyList(),
        val reject: List<RejectRule> = emptyList(),
        val deny: List<DenyRule> = emptyList(),
        val exclude: List<ExcludeRule> = emptyList(),
        val align: List<AlignRule> = emptyList()) {

    fun dependencyRulesPartOne() =
            listOf(replace, substitute, reject, deny, exclude).flatten()

    fun dependencyRulesPartTwo() =
            if (ResolutionRulesPlugin.isCoreAlignmentEnabled())
                listOf(align).flatten()
            else
                emptyList()

    fun resolveRules() =
            if (ResolutionRulesPlugin.isCoreAlignmentEnabled())
                emptyList()
            else
                listOf(AlignRules(align))

    fun generateAlignmentBelongsToName() {
        align.forEachIndexed { index, alignRule ->
            alignRule.belongsToName = "$name-$index"
        }
    }
}

class AlignedPlatformRule(alignRule: AlignRule, substituteRules: MutableList<SubstituteRule>) : Serializable {
    var substituteRules: List<SubstituteRule> = substituteRules
    var alignRule: AlignRule = alignRule

    fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, reasons: MutableSet<String>) {
        substituteRules.forEach {
            val substitution = resolutionStrategy.dependencySubstitution
            val selector = substitution.module(it.module)
            val substitutedModule = selector as? ModuleComponentSelector

            val withModuleId = ModuleVersionIdentifier.valueOf(it.with)
            if (!withModuleId.hasVersion()) {
                throw SubstituteRuleMissingVersionException(withModuleId, it, reasons)
            }
            val withSelector = substitution.module(withModuleId.toString()) as ModuleComponentSelector

            if (substitutedModule != null && shouldApplyRule(configuration, substitutedModule)) {
                firstLevelDependenciesRejectTheSubstitutedVersions(project, configuration, substitutedModule, withSelector, resolutionStrategy)
                transitiveDependenciesRejectTheSubstitutedVersions(project, substitutedModule, withSelector)
                it.applyForAlignedGroup(project, configuration, configuration.resolutionStrategy, extension, reasons, alignRule)
            }
        }
    }

    private fun shouldApplyRule(configuration: Configuration, substitutedModule: ModuleComponentSelector): Boolean {
        var shouldApplyRule = false
        var substitutedDependencyIsInDependencyGraph = false

        val incomingDependencies = configuration.incoming.dependencies

        incomingDependencies.forEach { dep ->
            if (dep.group == substitutedModule.group && dep.name == substitutedModule.module) {
                substitutedDependencyIsInDependencyGraph = true
            }
        }
        if (substitutedDependencyIsInDependencyGraph) {
            incomingDependencies.forEach { dep ->
                if (alignRule.ruleMatches(dep.group ?: "", dep.name)) {
                    shouldApplyRule = true
                }
            }
        }
        return shouldApplyRule
    }

    private fun transitiveDependenciesRejectTheSubstitutedVersions(project: Project, substitutedModule: ModuleComponentSelector, withSelector: ModuleComponentSelector) {
        project.dependencies.components.all(TransitiveDependenciesSubstitutionMetadataRule::class.java) {
            it.params(alignRule, substitutedModule.group, substitutedModule.module, substitutedModule.version, withSelector.version)
        }
    }

    private fun firstLevelDependenciesRejectTheSubstitutedVersions(project: Project, configuration: Configuration, substitutedModule: ModuleComponentSelector,
                                                                   withSelector: ModuleComponentSelector, resolutionStrategy: ResolutionStrategy) {
        val logger: Logger = Logging.getLogger(AlignedPlatformRule::class.java)
        configuration.incoming.beforeResolve { resolvableDependencies ->
            resolvableDependencies.dependencies.forEach { dep ->
                if (dep is ExternalModuleDependency) {
                    if (alignRule.ruleMatches(dep.group ?: "", dep.name)) {
                        val usingDependencyRecommendation = dep.version.isNullOrEmpty()

                        if (usingDependencyRecommendation) {
                            applyConstraintsToDependencyIfRecommendedVersionMatches(project, dep, substitutedModule, withSelector, logger, configuration)
                        } else {
                            applyConstraintsToDependency(dep, substitutedModule, logger, configuration)
                        }
                    }
                }
            }
        }
    }

    private fun applyConstraintsToDependencyIfRecommendedVersionMatches(project: Project, dep: ExternalModuleDependency, substitutedModule: ModuleComponentSelector, withSelector: ModuleComponentSelector, logger: Logger, configuration: Configuration) {
        val recommendedVersion = DependencyRecommendationsPlugin().getRecommendedVersionRecursive(project, dep)
        if (recommendedVersion != null) {
            val substitutedModuleVersionWithSelector = VersionWithSelector(substitutedModule.version)

            if (substitutedModuleVersionWithSelector.asSelector().accept(recommendedVersion)) {
                applyConstraintsToDependencyWithRecommendation(dep, withSelector, logger, configuration, substitutedModule)
            }
        }
    }

    private fun applyConstraintsToDependency(dep: ExternalModuleDependency, substitutedModule: ModuleComponentSelector, logger: Logger, configuration: Configuration) {
        dep.version {
            it.reject(substitutedModule.version)
            logger.debug("Rejecting version(s) '${substitutedModule.version}' for incoming dependency '${dep.group}:${dep.name}' " +
                    "in $configuration before resolution, " +
                    "based on alignment group '${substitutedModule.group}' " +
                    "via constraint '$it'")
        }
    }

    private fun applyConstraintsToDependencyWithRecommendation(dep: ExternalModuleDependency, withSelector: ModuleComponentSelector, logger: Logger, configuration: Configuration, substitutedModule: ModuleComponentSelector) {
        dep.version {
            it.require(withSelector.version) // Define before any "rejects". When defined, overrides previous strictly declaration and clears previous reject.
            logger.debug("Requiring version ${withSelector.version} for incoming dependency '${dep.group}:${dep.name}' " +
                    "in $configuration, " +
                    "based on alignment group '${substitutedModule.group}' " +
                    "via constraint '$it' because recommended version matched a substitution rule version."
            )
            it.reject(substitutedModule.version)
            logger.debug("Rejecting version(s) '${substitutedModule.version}' for incoming dependency '${dep.group}:${dep.name}' " +
                    "in $configuration, " +
                    "based on alignment group '${substitutedModule.group}' " +
                    "via constraint '$it' because recommended version matched a substitution rule version.")
        }
    }
}

fun RuleSet.withName(ruleSetName: String): RuleSet {
    name = ruleSetName
    listOf(replace, substitute, reject, deny, exclude, align).flatten().forEach { it.ruleSet = ruleSetName }
    generateAlignmentBelongsToName()
    return this
}

fun Collection<RuleSet>.flatten() = RuleSet(
        "flattened",
        flatMap { it.replace },
        flatMap { it.substitute },
        flatMap { it.reject },
        flatMap { it.deny },
        flatMap { it.exclude },
        flatMap { it.align })

data class ReplaceRule(override val module: String, val with: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : ModuleRule {
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, reasons: MutableSet<String>) {
        val moduleId = ModuleIdentifier.valueOf(module)
        val withModuleId = ModuleIdentifier.valueOf(with)
        project.dependencies.modules.module(moduleId.toString()) {
            val details = it as ComponentModuleMetadataDetails
            val message = "replacement ${moduleId.organization}:${moduleId.name} -> ${withModuleId.organization}:${withModuleId.name}\n" +
                    "\twith reasons: ${reasons.joinToString()}"
            details.replacedBy(withModuleId.toString(), message)
        }
    }
}

data class SubstituteRule(val module: String, val with: String, override var ruleSet: String?,
                          override val reason: String, override val author: String, override val date: String) : BasicRule, Serializable {
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, reasons: MutableSet<String>) {
        val substitution = resolutionStrategy.dependencySubstitution
        val selector = substitution.module(module)
        val withModuleId = ModuleVersionIdentifier.valueOf(with)
        if (!withModuleId.hasVersion()) {
            throw SubstituteRuleMissingVersionException(withModuleId, this, reasons)
        }
        val withSelector = substitution.module(withModuleId.toString()) as ModuleComponentSelector

        if (selector is ModuleComponentSelector) {
            resolutionStrategy.dependencySubstitution.all(action {
                if (requested is ModuleComponentSelector) {
                    val requestedSelector = requested as ModuleComponentSelector
                    if (requestedSelector.group == selector.group && requestedSelector.module == selector.module) {
                        val versionSelector = VersionWithSelector(selector.version).asSelector()
                        if (versionSelector.accept(requestedSelector.version)) {
                            val message = "substitution from '$selector' to '$withSelector' because $reason \n" +
                                    "\twith reasons: ${reasons.joinToString()}"
                            useTarget(withSelector, message)
                        }
                    }
                }
            })
        } else {
            var message = "substitution to '$withSelector' because $reason \n" +
                    "\twith reasons: ${reasons.joinToString()}"

            val selectorNameSections = selector.displayName.split(":")
            if (selectorNameSections.size > 2) {
                val selectorGroupAndArtifact = "${selectorNameSections[0]}:${selectorNameSections[1]}"
                message = "substitution from '$selectorGroupAndArtifact' to '$withSelector' because $reason \n" +
                        "\twith reasons: ${reasons.joinToString()}"
            }

            resolutionStrategy.dependencySubstitution {
                it.substitute(selector)
                        .because(message)
                        .with(withSelector)
            }
        }
    }

    fun applyForAlignedGroup(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy,
                             extension: NebulaResolutionRulesExtension, reasons: MutableSet<String>, alignRule: AlignRule) {
        val substitution = resolutionStrategy.dependencySubstitution
        val selector = substitution.module(module)
        val withModuleId = ModuleVersionIdentifier.valueOf(with)
        if (!withModuleId.hasVersion()) {
            throw SubstituteRuleMissingVersionException(withModuleId, this, reasons)
        }
        val withSelector = substitution.module(withModuleId.toString()) as ModuleComponentSelector

        if (selector is ModuleComponentSelector) {
            resolutionStrategy.dependencySubstitution.all(action {
                if (requested is ModuleComponentSelector) {
                    val requestedSelector = requested as ModuleComponentSelector
                    val requestedWithSubstitutedVersionFromAlignedModule = ModuleVersionIdentifier.valueOf("${requestedSelector.group}:${requestedSelector.module}:${withSelector.version}")

                    val matches = alignRule.ruleMatches(requestedSelector.group ?: "", requestedSelector.module)
                    val notTheOriginatingDependency = requestedSelector.module != selector.module
                    if (matches && notTheOriginatingDependency) {
                        val versionSelector = VersionWithSelector(selector.version).asSelector()
                        if (versionSelector.accept(requestedSelector.version)) {
                            val message = "substitution from aligned dependency '$selector' to '$withSelector' because '$reason'\n" +
                                    "\twith reasons: ${reasons.joinToString()}"
                            val updatedRequestedModuleComponentSelector = substitution.module(requestedWithSubstitutedVersionFromAlignedModule.toString()) as ModuleComponentSelector
                            useTarget(updatedRequestedModuleComponentSelector, message)
                        }
                    }
                }
            })
        } else {
            // do nothing if there is no version
        }
    }
}

class TransitiveDependenciesSubstitutionMetadataRule : ComponentMetadataRule, Serializable {
    private val logger: Logger = Logging.getLogger(TransitiveDependenciesSubstitutionMetadataRule::class.java)
    val alignRule: AlignRule
    val substitutionGroup: String
    val substitutionModuleName: String
    val substitutionVersion: String
    val withSelectorVersion: String

    @Inject
    constructor(alignRule: AlignRule, substitutionGroup: String, substitutionModuleName: String, substitutionVersion: String, withSelectorVersion: String) {
        this.alignRule = alignRule
        this.substitutionGroup = substitutionGroup
        this.substitutionModuleName = substitutionModuleName
        this.substitutionVersion = substitutionVersion
        this.withSelectorVersion = withSelectorVersion
    }

    override fun execute(componentMetadataContext: ComponentMetadataContext?) {
        modifyDetails(componentMetadataContext!!.details)
    }

    private fun modifyDetails(details: ComponentMetadataDetails) {
        // TODO: This happens frequently, as it's applied to all variants. Can this be reduced?
        details.allVariants {
            it.withDependencies { deps ->
                deps.forEach { dep ->
                    if (alignRule.ruleMatches(dep.group ?: "", dep.name)) {
                        dep.version {
                            it.reject(substitutionVersion)
                            logger.debug("Rejection of transitive dependency ${dep.group}:${dep.name} version(s) '$substitutionVersion' from aligned dependency '$substitutionGroup:$substitutionModuleName'")
                        }
                    }
                }
            }
        }
    }
}

data class RejectRule(override val module: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : ModuleRule {
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, reasons: MutableSet<String>) {
        val moduleId = ModuleVersionIdentifier.valueOf(module)
        resolutionStrategy.componentSelection.all(Action<ComponentSelection> { selection ->
            val candidate = selection.candidate
            if (candidate.group == moduleId.organization && candidate.module == moduleId.name) {
                val versionSelector = VersionWithSelector(moduleId.version).asSelector()
                if (!moduleId.hasVersion() || versionSelector.accept(candidate.version)) {
                    val message = "Rejected by resolution rule $ruleSet - $reason\n" +
                            "\twith reasons: ${reasons.joinToString()}"
                    selection.reject(message)
                }
            }
        })
    }
}

data class DenyRule(override val module: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : ModuleRule {
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, reasons: MutableSet<String>) {
        val moduleId = ModuleVersionIdentifier.valueOf(module)
        val match = configuration.allDependencies.find {
            it is ExternalModuleDependency && it.group == moduleId.organization && it.name == moduleId.name
        }
        if (match != null && (!moduleId.hasVersion() || match.version == moduleId.version)) {
            resolutionStrategy.componentSelection.withModule("${moduleId.organization}:${moduleId.name}", Action<ComponentSelection> { selection ->
                val message = "Dependency $moduleId denied by dependency rule: $reason\n" +
                        "\twith reasons: ${reasons.joinToString()}"
                selection.reject(message)
            })
            throw DependencyDeniedException(moduleId, this, reasons)
        }
    }
}

data class ExcludeRule(override val module: String, override var ruleSet: String?, override val reason: String, override val author: String, override val date: String) : ModuleRule {
    val logger: Logger = Logging.getLogger(ExcludeRule::class.java)

    @Override
    override fun apply(project: Project, configuration: Configuration, resolutionStrategy: ResolutionStrategy, extension: NebulaResolutionRulesExtension, reasons: MutableSet<String>) {
        val moduleId = ModuleIdentifier.valueOf(module)
        val message = "Resolution rule $this excluding ${moduleId.organization}:${moduleId.name} and transitive dependencies for all dependencies of this configuration\n" +
                "\twith reasons: ${reasons.joinToString()}"
        logger.debug(message)
        // TODO: would like a core Gradle feature that accepts a reason
        configuration.exclude(moduleId.organization, moduleId.name)

        resolutionStrategy.componentSelection.withModule("${moduleId.organization}:${moduleId.name}", Action<ComponentSelection> { selection ->
            selection.reject(message)
        })
    }
}

class DependencyDeniedException(moduleId: ModuleVersionIdentifier, rule: DenyRule, reasons: MutableSet<String>) : Exception("Dependency $moduleId denied by dependency rule: ${rule.reason}\n" +
        "\twith reasons: ${reasons.joinToString()}")

class SubstituteRuleMissingVersionException(moduleId: ModuleVersionIdentifier, rule: SubstituteRule, reasons: MutableSet<String>) : Exception("The dependency to be substituted ($moduleId) must have a version. Invalid rule: $rule\n" +
        "\twith reasons: ${reasons.joinToString()}")

fun Configuration.exclude(group: String, module: String) {
    exclude(mapOf("group" to group, "module" to module))
}
