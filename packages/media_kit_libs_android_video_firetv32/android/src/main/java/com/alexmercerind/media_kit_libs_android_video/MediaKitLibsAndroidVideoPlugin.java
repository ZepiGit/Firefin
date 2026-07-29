package com.alexmercerind.media_kit_libs_android_video;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;

import com.alexmercerind.mediakitandroidhelper.MediaKitAndroidHelper;

public class MediaKitLibsAndroidVideoPlugin implements FlutterPlugin {
    static {
        try {
            System.loadLibrary("mpv");
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        try {
            MediaKitAndroidHelper.setApplicationContextJava(flutterPluginBinding.getApplicationContext());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    }
}
