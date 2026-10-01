package zepigit.firefin.app.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import zepigit.firefin.app.data.JellyfinClient

/**
 * Fire-and-forget session reporting that must outlive the player activity
 * (stop reports are sent after release without blocking the UI thread).
 */
object SessionReporter {
    fun reportStopped(client: JellyfinClient, itemId: String, playSessionId: String, positionTicks: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { client.reportStopped(itemId, playSessionId, positionTicks) }
        }
    }
}
