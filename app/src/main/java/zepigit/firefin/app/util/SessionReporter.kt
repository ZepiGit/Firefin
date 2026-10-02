package zepigit.firefin.app.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONObject
import zepigit.firefin.app.data.ServerResponseException
import zepigit.firefin.app.data.ServerTransport

/**
 * Ordered, terminal session reporter for one playback:
 * Playing < (conflated latest Progress)? < Stopped < (ActiveEncodings|LiveStreams cleanup, once).
 * It owns an immutable [ServerTransport] snapshot, so account switching can
 * never send a previous playback's credentials to the next account. The
 * terminal stop is delivered even when the player activity is destroyed, and
 * cleanup is best-effort: HTTP 404 counts as already cleaned, other failures
 * are swallowed once without retry loops. Nothing here claims a server-side
 * effect — only the client-side send order is guaranteed.
 */
class SessionReporter(
    private val transport: ServerTransport,
    private val itemId: String,
    private val sessionId: String,
    private val mediaSourceId: String,
    private val playMethod: String,
    private val liveStreamId: String = "",
) {
    private sealed interface Event {
        data object Playing : Event
        data class Progress(val ticks: Long, val paused: Boolean) : Event
        data class Stopped(val ticks: Long) : Event
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val lock = Any()
    private var latestProgress: Event.Progress? = null
    private var terminal: Event.Stopped? = null
    private var closed = false

    private val worker = scope.launch {
        send(Event.Playing)
        while (true) {
            wake.receive()
            val nextProgress: Event.Progress?
            val stop: Event.Stopped?
            synchronized(lock) {
                nextProgress = latestProgress
                latestProgress = null
                stop = terminal
            }
            if (nextProgress != null) send(nextProgress)
            if (stop != null) {
                send(stop)
                cleanupTranscode()
                break
            }
        }
        scope.cancel()
    }

    /** Playing is sent by the worker before any progress; this is kept for call-site symmetry. */
    fun playing() = Unit

    fun progress(ticks: Long, paused: Boolean) {
        synchronized(lock) {
            if (closed) return
            latestProgress = Event.Progress(ticks, paused)
        }
        wake.trySend(Unit)
    }

    fun stopped(ticks: Long) {
        synchronized(lock) {
            if (closed) return
            closed = true
            terminal = Event.Stopped(ticks)
        }
        wake.trySend(Unit)
    }

    fun cancel() {
        synchronized(lock) { closed = true }
        wake.close()
        scope.cancel()
        worker.cancel()
    }

    /** Test/teardown helper: waits until the terminal stop (and cleanup) has been sent. */
    suspend fun awaitTerminal(timeoutMs: Long = 30_000) = kotlinx.coroutines.withTimeout(timeoutMs) { worker.join() }

    private suspend fun send(event: Event) {
        try {
            when (event) {
                Event.Playing -> transport.json(
                    "Sessions/Playing", "POST",
                    sessionEventBody().put("MediaSourceId", mediaSourceId)
                        .put("PlayMethod", playMethod).put("CanSeek", true).toString(),
                )
                is Event.Progress -> transport.json(
                    "Sessions/Playing/Progress", "POST",
                    sessionEventBody().put("PositionTicks", event.ticks).put("IsPaused", event.paused)
                        .put("MediaSourceId", mediaSourceId).put("CanSeek", true).toString(),
                )
                is Event.Stopped -> transport.json(
                    "Sessions/Playing/Stopped", "POST",
                    sessionEventBody().put("PositionTicks", event.ticks)
                        .put("MediaSourceId", mediaSourceId).toString(),
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Session reporting is best effort and never retries a failed event.
        }
    }

    /** One-shot, idempotent transcode teardown bound to this playback session only. */
    private suspend fun cleanupTranscode() {
        try {
            val query = "?DeviceId=" + urlEncode(transport.deviceId) + "&PlaySessionId=" + urlEncode(sessionId)
            transport.json("Videos/ActiveEncodings$query", "DELETE")
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: ServerResponseException) { if (e.status != 404) Unit } catch (_: Exception) { Unit }
        if (liveStreamId.isNotBlank()) {
            try {
                transport.json("LiveStreams/Close", "POST", JSONObject().put("LiveStreamId", liveStreamId).toString())
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: ServerResponseException) { if (e.status != 404) Unit } catch (_: Exception) { Unit }
        }
    }

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun sessionEventBody(): JSONObject =
        JSONObject().put("ItemId", itemId).put("PlaySessionId", sessionId)
}
