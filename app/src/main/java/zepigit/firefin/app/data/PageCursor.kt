package zepigit.firefin.app.data

/**
 * Paging state for one remote list (main thread only): the server total, the
 * next start index and at most one page in flight. A failed page leaves the
 * loaded pages untouched and can be requested again.
 */
class PageCursor(private val prefetch: Int = 8) {
    var total = -1
        private set
    var loaded = 0
        private set
    private var inFlight = false

    val hasMore: Boolean get() = total < 0 || loaded < total

    /** Start index of the next page, or null while a page is in flight or nothing is left. */
    fun next(): Int? {
        if (inFlight || !hasMore) return null
        inFlight = true
        return loaded
    }

    /** Records a received page; an empty page ends the list even if the server total says otherwise. */
    fun received(count: Int, serverTotal: Int) {
        inFlight = false
        loaded += count
        total = if (count == 0) loaded else maxOf(serverTotal, loaded)
    }

    fun failed() {
        inFlight = false
    }

    /** True when [position] is close enough to the loaded end to fetch the next page. */
    fun nearEnd(position: Int): Boolean = hasMore && position >= loaded - prefetch
}
