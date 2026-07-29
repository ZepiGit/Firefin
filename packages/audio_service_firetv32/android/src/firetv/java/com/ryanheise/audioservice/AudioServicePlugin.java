package com.ryanheise.audioservice;

import io.flutter.embedding.engine.plugins.FlutterPlugin;

/**
 * Fire TV registration shim.
 *
 * <p>Moonfin's dedicated TV mode does not initialize audio_service; foreground
 * playback and remote-key handling are provided by media_kit and Moonfin.
 */
public final class AudioServicePlugin implements FlutterPlugin {
  @Override
  public void onAttachedToEngine(FlutterPluginBinding binding) {}

  @Override
  public void onDetachedFromEngine(FlutterPluginBinding binding) {}
}
