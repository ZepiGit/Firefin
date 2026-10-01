package zepigit.firefin.app.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import zepigit.firefin.app.data.JellyfinClient

/**
 * Fire-and-forget session reporting that must outlive the player activity
 * (stop reports are sent after release without blocking the UI thread).
 * Once a stop was sent for a session, later progress reports for that session
 * are dropped so Jellyfin does not resurrect the NowPlayingItem.
 */
object SessionReporter {
    private val stoppedSessions = mutableSetOf<String>()

    fun reportStopped(client: JellyfinClient, itemId: String, playSessionId: String, positionTicks: Long) {
        synchronized(stoppedSessions) { stoppedSessions.add(playSessionId) }
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { client.reportStopped(itemId, playSessionId, positionTicks) }
        }
    }

    fun reportProgress(
        client: JellyfinClient,
        itemId: String,
        playSessionId: String,
        positionTicks: Long,
        paused: Boolean,
    ): Boolean {
        synchronized(stoppedSessions) { if (playSessionId in stoppedSessions) return false }
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { client.reportProgress(itemId, playSessionId, positionTicks, paused) }
        }
        return true
    }
}
