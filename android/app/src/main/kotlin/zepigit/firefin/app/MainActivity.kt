package zepigit.firefin.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private var platformChannel: MethodChannel? = null
    private var pipChannel: MethodChannel? = null
    private var castChannel: MethodChannel? = null
    private var castEventsChannel: EventChannel? = null
    private var dlnaChannel: MethodChannel? = null
    private var dlnaEventsChannel: EventChannel? = null
    private var dlnaController: DlnaController? = null

    companion object {
        private const val PLATFORM_CHANNEL = "zepigit.firefin.app/platform"
        private const val PIP_CHANNEL = "zepigit.firefin.app/pip"
        private const val CAST_CHANNEL = "zepigit.firefin.app/native_cast"
        private const val CAST_EVENTS_CHANNEL = "zepigit.firefin.app/native_cast_events"
        private const val DLNA_CHANNEL = "zepigit.firefin.app/native_dlna"
        private const val DLNA_EVENTS_CHANNEL = "zepigit.firefin.app/native_dlna_events"
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF ->
                    pipChannel?.invokeMethod("onScreenLock", true)
                Intent.ACTION_SCREEN_ON ->
                    pipChannel?.invokeMethod("onScreenLock", false)
            }
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger

        platformChannel = MethodChannel(messenger, PLATFORM_CHANNEL).apply {
            setMethodCallHandler { call, result ->
                if (call.method == "isTvDevice") {
                    // Dedicated Fire TV APK. Fire OS 5 does not consistently
                    // expose Android's Leanback feature flags.
                    result.success(true)
                } else {
                    result.notImplemented()
                }
            }
        }

        pipChannel = MethodChannel(messenger, PIP_CHANNEL).apply {
            setMethodCallHandler { call, result ->
                when (call.method) {
                    "enableAutoPiP", "updatePiPActions" -> result.success(false)
                    else -> result.notImplemented()
                }
            }
        }

        // Fire OS does not ship Google Play Services. Keep the Dart channel
        // contract intact while cleanly disabling Google Cast in this build.
        castChannel = MethodChannel(messenger, CAST_CHANNEL).apply {
            setMethodCallHandler { call, result ->
                when (call.method) {
                    "discoverGoogleCastTargets" ->
                        result.success(emptyList<Map<String, Any>>())
                    "showAirPlayRoutePicker" ->
                        result.error("UNSUPPORTED", "AirPlay is only available on iOS.", null)
                    else ->
                        result.error(
                            "UNSUPPORTED",
                            "Google Cast is unavailable on Fire TV.",
                            null,
                        )
                }
            }
        }

        castEventsChannel = EventChannel(messenger, CAST_EVENTS_CHANNEL).apply {
            setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    events?.success(
                        mapOf(
                            "kind" to "googleCast",
                            "state" to "disconnected",
                        ),
                    )
                }

                override fun onCancel(arguments: Any?) = Unit
            })
        }

        dlnaController = DlnaController(this)
        dlnaChannel = MethodChannel(messenger, DLNA_CHANNEL).apply {
            setMethodCallHandler { call, result ->
                val controller = dlnaController
                if (controller == null) {
                    result.error("DLNA_UNAVAILABLE", "DLNA controller not initialized", null)
                    return@setMethodCallHandler
                }

                when (call.method) {
                    "discoverDlnaTargets" -> controller.discoverTargets(result)
                    "playToDlnaDevice" -> {
                        val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                        controller.playToDevice(args, result)
                    }
                    "pauseDlna" -> controller.pause(result)
                    "playDlna" -> controller.play(result)
                    "seekDlna" -> {
                        val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                        controller.seek(args, result)
                    }
                    "stopDlna" -> controller.stop(result)
                    "getDlnaVolume" -> controller.getVolume(result)
                    "setDlnaVolume" -> {
                        val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                        controller.setVolume(args, result)
                    }
                    else -> result.notImplemented()
                }
            }
        }

        dlnaEventsChannel = EventChannel(messenger, DLNA_EVENTS_CHANNEL).apply {
            setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    dlnaController?.setEventSink(events)
                }

                override fun onCancel(arguments: Any?) {
                    dlnaController?.setEventSink(null)
                }
            })
        }

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, screenFilter)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        platformChannel?.setMethodCallHandler(null)
        pipChannel?.setMethodCallHandler(null)
        castChannel?.setMethodCallHandler(null)
        castEventsChannel?.setStreamHandler(null)
        dlnaController?.onDestroy()
        dlnaChannel?.setMethodCallHandler(null)
        dlnaEventsChannel?.setStreamHandler(null)
        super.onDestroy()
    }
}
