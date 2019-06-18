package nebula.plugin.resolutionrules

inline fun <T, R> Collection<T>.mapToSet(transform: (T) -> R): Set<R> {
    return mapTo(LinkedHashSet<R>(size), transform)
}
