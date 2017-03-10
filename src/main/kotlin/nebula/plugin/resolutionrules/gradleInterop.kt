package nebula.plugin.resolutionrules

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.collections.CollectionEventRegister
import org.gradle.util.Path
import java.lang.reflect.Field
import java.lang.reflect.Modifier

inline fun <T> T.groovyClosure(crossinline call: () -> Unit) = object : Closure<Unit>(this) {
    @Suppress("unused")
    fun doCall() {
        call()
    }
}

inline fun <U> Any.action(crossinline call: U.() -> Unit) = Action<U> { call(it) }


val ResolvedDependencyResult.selectedId: ComponentIdentifier
    get() = selected.id

val ResolvedDependencyResult.selectedModuleVersion: ModuleVersionIdentifier
    get() = selected.moduleVersion

val ResolvedDependencyResult.selectedModule: ModuleIdentifier
    get() = selected.moduleVersion.module

val ResolvedDependencyResult.selectedVersion: String
    get() = selected.moduleVersion.version

/**
 * Various reflection hackiness follows due to deficiencies in the Gradle configuration APIs:
 *
 * - We can't add the configuration to the configuration container to get the addAction handlers, because it causes ConcurrentModificationExceptions
 * - We can't set the configuration name on copyRecursive, which makes for confusing logging output when we're resolving our configurations
 */

class CopiedConfiguration(val source: Configuration, val project: Project, val baseName: String, copy: Configuration) : Configuration by copy

fun CopiedConfiguration.copyConfiguration(alignmentPhase: String) = project.copyConfiguration(this, baseName, alignmentPhase)

fun Project.copyConfiguration(configuration: Configuration, baseName: String, alignmentPhase: String): CopiedConfiguration {
    val copy = configuration.copyRecursive()
    val name = "$baseName$alignmentPhase${AlignRules.CONFIG_SUFFIX}"
    copy.setName(name)

    // Apply container register actions, without risking concurrently modifying the configuration container
    val container = configurations
    val eventRegister = container.javaClass.getDeclaredMethod("getEventRegister").invoke(container)
    @Suppress("UNCHECKED_CAST")
    val addAction = eventRegister.javaClass.getDeclaredMethod("getAddAction").invoke(eventRegister) as Action<Configuration>
    addAction.execute(configuration)

    // Hacky workaround to prevent Gradle from attempting to resolve a project dependency as an external dependency
    copy.exclude(this.group.toString(), this.name)

    return CopiedConfiguration(configuration, this, baseName, copy)
}

fun Configuration.setName(name: String) {
    setField("name", name)
    val internalConfig = this as ConfigurationInternal
    val parentPath = Path.path(internalConfig.path).parent
    try {
        val path = parentPath.child(name)
        setField("path", path)
        setField("identityPath", path)
    } catch (e: NoSuchMethodError) {
        setField("path", "$parentPath:$name".replace("::", ":"))
    }
}

fun Any.setField(name: String, value: Any) {
    val field = javaClass.findDeclaredField(name)
    field.isAccessible = true

    val modifiersField = Field::class.java.getDeclaredField("modifiers")
    modifiersField.isAccessible = true
    modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())

    field.set(this, value)
}

tailrec fun <T> Class<T>.findDeclaredField(name: String): Field {
    val field = declaredFields
            .filter { it.name == name }
            .singleOrNull()
    if (field != null) {
        return field
    } else if (superclass != null) {
        return superclass.findDeclaredField(name)
    }
    throw IllegalArgumentException("Could not find field $name")
}

