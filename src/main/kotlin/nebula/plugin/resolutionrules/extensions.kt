package nebula.plugin.resolutionrules

import org.gradle.api.Project

inline fun <T, R> Collection<T>.mapToSet(transform: (T) -> R): Set<R> {
    return mapTo(LinkedHashSet<R>(size), transform)
}

fun Project.findStringProperty(name: String): String? = if (hasProperty(name)) property(name) as String? else null

fun parseRuleNames(ruleNames: String): Set<String> =
    ruleNames.split(",").map { it.trim() }.filter { it.isNotEmpty()} .toSet()