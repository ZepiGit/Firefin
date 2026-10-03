package zepigit.firefin.app.util

/**
 * Owns the [SessionReporter] of the current playback attempt (main thread only).
 *
 * Natural end, a fatal player error, a source switch and activity teardown all
 * finish the attempt the same way: one terminal stop, no progress afterwards.
 * A finished reporter is never reused; a retry or a new source starts a fresh one.
 */
class PlaybackSessionTracker {
    private var reporter: SessionReporter? = null

    val active: Boolean get() = reporter != null

    fun start(next: SessionReporter) {
        check(reporter == null) { "Finish the previous playback session first." }
        reporter = next
    }

    fun progress(ticks: Long, paused: Boolean) {
        reporter?.progress(ticks, paused)
    }

    /**
     * Sends the terminal stop at most once per attempt and returns the finished
     * reporter so a caller can await its cleanup; null when nothing was active.
     */
    fun finish(ticks: Long): SessionReporter? {
        val finished = reporter ?: return null
        reporter = null
        finished.stopped(ticks)
        return finished
    }
}
