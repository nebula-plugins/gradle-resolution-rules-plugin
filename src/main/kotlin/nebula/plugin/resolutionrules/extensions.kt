package nebula.plugin.resolutionrules

import org.gradle.api.Project

inline fun <T, R> Collection<T>.mapToSet(transform: (T) -> R): Set<R> {
    return mapTo(LinkedHashSet<R>(size), transform)
}

/**
 * Finds a Gradle property by name using the modern Provider API.
 * Returns the property value as a String, or null if not present.
 */
fun Project.findStringProperty(name: String): String? =
    providers.gradleProperty(name).orNull

fun parseRuleNames(ruleNames: String): Set<String> =
    ruleNames.split(",").map { it.trim() }.filter { it.isNotEmpty()} .toSet()