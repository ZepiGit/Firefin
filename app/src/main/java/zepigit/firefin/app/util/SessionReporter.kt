package zepigit.firefin.app.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import zepigit.firefin.app.data.JellyfinClient

/** Ordered, terminal session reporter: Playing < Progress* < Stopped. */
class SessionReporter(
    private val client: JellyfinClient,
    private val itemId: String,
    private val sessionId: String,
    private val mediaSourceId: String,
    private val playMethod: String,
) {
    private sealed interface Event {
        data object Playing : Event
        data class Progress(val ticks: Long, val paused: Boolean) : Event
        data class Stopped(val ticks: Long) : Event
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val worker = scope.launch {
        for (event in events) {
            runCatching {
                when (event) {
                    Event.Playing -> client.reportPlaying(itemId, sessionId, mediaSourceId, playMethod)
                    is Event.Progress -> client.reportProgress(itemId, sessionId, event.ticks, event.paused, mediaSourceId)
                    is Event.Stopped -> client.reportStopped(itemId, sessionId, event.ticks, mediaSourceId)
                }
            }
            if (event is Event.Stopped) break
        }
        scope.cancel()
    }

    fun playing() { events.trySend(Event.Playing) }
    fun progress(ticks: Long, paused: Boolean) { events.trySend(Event.Progress(ticks, paused)) }
    fun stopped(ticks: Long) { events.trySend(Event.Stopped(ticks)); events.close() }
    fun cancel() { events.close(); scope.cancel(); worker.cancel() }
}
