package nebula.plugin.resolutionrules

import org.gradle.api.artifacts.Configuration
import java.lang.reflect.Field

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

