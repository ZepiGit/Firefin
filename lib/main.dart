import 'dart:io';

import 'package:audio_session/audio_session.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get_it/get_it.dart';
import 'package:media_kit/media_kit.dart';
import 'package:playback_core/playback_core.dart';
import 'package:window_manager/window_manager.dart';

import 'app.dart';
import 'data/services/cast/airplay_command_bridge.dart';
import 'data/services/download_notification_service.dart';
import 'data/services/media_server_client_factory.dart';
import 'di/injection.dart';
import 'playback/audio_handler.dart';
import 'playback/playback_lifecycle_handler.dart';
import 'util/legacy_firetv_certificate_support.dart';
import 'util/platform_detection.dart';

void _configureImageCache() {
  final imageCache = PaintingBinding.instance.imageCache;
  if (PlatformDetection.isTV) {
    // Keep memory pressure low on 1 GB Fire TV hardware (r21: tighter).
    imageCache.maximumSize = 40;
    imageCache.maximumSizeBytes = 32 << 20;
    return;
  }
  if (PlatformDetection.isMobile) {
    imageCache.maximumSize = 100;
    imageCache.maximumSizeBytes = 120 << 20;
    return;
  }

  imageCache.maximumSize = 200;
  imageCache.maximumSizeBytes = 256 << 20;
}

void _enableDedicatedTvMode() {
  if (!PlatformDetection.isAndroid) return;
  // This APK is a dedicated Fire TV distribution. Some older Fire OS
  // versions do not expose Android's Leanback feature flags consistently.
  PlatformDetection.setTvMode(true);
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  _enableDedicatedTvMode();
  installLegacyFireTvCertificateSupport();

  if (PlatformDetection.isDesktop) {
    await windowManager.ensureInitialized();
  }

  _configureImageCache();
  MediaKit.ensureInitialized();

  // On Linux the GTK font pipeline loads fonts asynchronously. The first frame
  // can render before MaterialIcons and other fonts are ready, causing icons to
  // appear blank. Pumping a warm-up frame gives the font loader time to finish.
  // The issue is intermittent and goes away on re-run once the OS font cache
  // is warm, which confirms the timing root cause.
  if (Platform.isLinux) {
    WidgetsBinding.instance.scheduleWarmUpFrame();
  }

  if (PlatformDetection.isTV) {
    await SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    await SystemChrome.setPreferredOrientations(const [
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
  } else if (PlatformDetection.isMobile) {
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    SystemChrome.setSystemUIOverlayStyle(
      const SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        systemNavigationBarColor: Colors.transparent,
      ),
    );
  }

  await configureDependencies();

  final notificationService = GetIt.instance<DownloadNotificationService>();
  try {
    await notificationService.initialize();
  } catch (_) {}

  if (PlatformDetection.isMobile) {
    try {
      await initAudioService(
        manager: GetIt.instance<PlaybackManager>(),
        clientFactory: GetIt.instance<MediaServerClientFactory>(),
      );
    } catch (_) {}
  }

  try {
    final session = await AudioSession.instance;
    final iosCategoryOptions =
        AVAudioSessionCategoryOptions.allowAirPlay |
        AVAudioSessionCategoryOptions.allowBluetooth |
        AVAudioSessionCategoryOptions.allowBluetoothA2dp;
    await session.configure(
      AudioSessionConfiguration(
        avAudioSessionCategory: AVAudioSessionCategory.playback,
        avAudioSessionCategoryOptions: iosCategoryOptions,
        androidAudioAttributes: AndroidAudioAttributes(
          contentType: AndroidAudioContentType.music,
          usage: AndroidAudioUsage.media,
        ),
      ),
    );
    await session.setActive(true);
  } catch (_) {}

  if (!GetIt.instance.isRegistered<PlaybackLifecycleHandler>()) {
    GetIt.instance.registerSingleton<PlaybackLifecycleHandler>(
      PlaybackLifecycleHandler(GetIt.instance<PlaybackManager>()),
    );
  }

  try {
    GetIt.instance<AirPlayCommandBridge>().start();
  } catch (_) {}

  runApp(const MoonfinApp());
}
