package zepigit.firefin.app.data

/** Monotonic guard for debounced UI work; only the newest query may publish results. */
class SearchQueryGate {
    private var generation = 0

    @Synchronized
    fun next(): Int = ++generation

    @Synchronized
    fun isCurrent(candidate: Int): Boolean = candidate == generation
}
