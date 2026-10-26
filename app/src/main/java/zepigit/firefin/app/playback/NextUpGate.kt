package zepigit.firefin.app.playback

/** Main-thread state shared by the ended event, countdown and remote input. */
class NextUpGate {
    var autoAdvances = 0
        private set
    private var ended = false

    fun beginAttempt() { ended = false }
    fun endOnce(): Boolean {
        if (ended) return false
        ended = true
        return true
    }
    fun input() { autoAdvances = 0 }
    fun advancedAutomatically() { autoAdvances++ }
    fun needsConfirmation() = autoAdvances >= 3
    fun countdownEnabled(autoPlay: Boolean) = autoPlay && !needsConfirmation()
}
