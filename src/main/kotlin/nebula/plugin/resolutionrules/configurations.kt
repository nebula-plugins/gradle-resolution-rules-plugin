package nebula.plugin.resolutionrules

import com.netflix.nebula.dependencybase.DependencyManagement
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.util.Path
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Various reflection hackiness follows due to deficiencies in the Gradle configuration APIs:
 *
 * - We can't add the configuration to the configuration container to get the addAction handlers, because it causes ConcurrentModificationExceptions
 * - We can't set the configuration name on copyRecursive, which makes for confusing logging output when we're resolving our configurations
 */

const val COPY_DESCRIPTION_SUFFIX = " (Copy)"

class CopiedConfiguration(val source: Configuration, val project: Project, copy: Configuration) : Configuration by copy

fun CopiedConfiguration.copyConfiguration() = project.copyConfiguration(source)

fun Project.copyConfiguration(configuration: Configuration): CopiedConfiguration {
    val copy = configuration.copyRecursive()
    copy.setName(configuration.name)
    copy.description = copy.description + COPY_DESCRIPTION_SUFFIX

    // Prevent base plugin artifact handlers from adding artifacts from this configuration to the archives configuration
    val detachedConfiguration = configurations.detachedConfiguration() // Just a nice way of getting at constructed collections
    copy.setField("artifacts", detachedConfiguration.artifacts)
    copy.setField("allArtifacts", detachedConfiguration.allArtifacts)

    // Apply container register actions, without risking concurrently modifying the configuration container
    val eventRegister = configurations.javaClass.getDeclaredMethod("getEventRegister").invoke(configurations)
    @Suppress("UNCHECKED_CAST")
    val addAction = eventRegister.javaClass.getDeclaredMethod("getAddAction").invoke(eventRegister) as Action<Configuration>
    addAction.execute(copy)

    // Hacky workaround to prevent Gradle from attempting to resolve a project dependency as an external dependency
    copy.exclude(this.group.toString(), this.name)

    return CopiedConfiguration(configuration, this, copy)
}

val Configuration.isCopy: Boolean
    get() = description?.endsWith(COPY_DESCRIPTION_SUFFIX) ?: false

fun DependencyManagement.addReason(configuration: Configuration, coordinate: String, message: String) {
    if (!configuration.isCopy) {
        addReason(configuration.name, coordinate, message, "nebula.resolution-rules")
    }
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

