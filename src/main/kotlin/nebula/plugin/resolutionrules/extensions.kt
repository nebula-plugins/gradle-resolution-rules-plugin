package nebula.plugin.resolutionrules

import java.util.regex.Matcher

inline fun <T, R> Collection<T>.mapToSet(transform: (T) -> R): Set<R> {
    return mapTo(LinkedHashSet<R>(size), transform)
}

fun Matcher.matches(input: CharSequence) =
        reset(input).matches()
