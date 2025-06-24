package nebula.plugin.resolutionrules

import org.gradle.api.artifacts.Configuration
import java.lang.reflect.Field
import java.lang.reflect.Modifier


/**
 * Various reflection hackiness follows due to deficiencies in the Gradle configuration APIs:
 *
 * - We can't add the configuration to the configuration container to get the addAction handlers, because it causes ConcurrentModificationExceptions
 * - We can't set the configuration name on copyRecursive, which makes for confusing logging output when we're resolving our configurations
 */
fun Any.setField(name: String, value: Any) {
    val field = javaClass.findDeclaredField(name)
    field.isAccessible = true

    lateinit var modifiersField: Field
    try {
        modifiersField = Field::class.java.getDeclaredField("modifiers")
    } catch (e: NoSuchFieldException) {
        try {
            val getDeclaredFields0 = Class::class.java.getDeclaredMethod("getDeclaredFields0", Boolean::class.javaPrimitiveType)
            val accessibleBeforeSet: Boolean = getDeclaredFields0.isAccessible
            getDeclaredFields0.isAccessible = true
            @Suppress("UNCHECKED_CAST") val declaredFields = getDeclaredFields0.invoke(Field::class.java, false) as Array<Field>
            getDeclaredFields0.isAccessible = accessibleBeforeSet
            for (declaredField in declaredFields) {
                if ("modifiers" == declaredField.name) {
                    modifiersField = declaredField
                    break
                }
            }
        } catch (ex: Exception) {
            e.addSuppressed(ex)
            throw e
        }
    }
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

fun Configuration.getObservedState(): Configuration.State {
    return try {
        // Try to access state through public API first
        this.state
    } catch (e: Exception) {
        // Fallback to reflection-based approach for older Gradle versions
        try {
            val f: Field = this::class.java.findDeclaredField("observedState")
            f.isAccessible = true
            val resolvedState = f.get(this)
            if (resolvedState.toString() == "UNRESOLVED") Configuration.State.UNRESOLVED else Configuration.State.RESOLVED
        } catch (reflectionException: Exception) {
            // If all else fails, assume unresolved
            Configuration.State.UNRESOLVED
        }
    }
}

