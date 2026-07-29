import 'dart:io';

import 'package:flutter/services.dart';

class IosStorage {
  static const _channel = MethodChannel('com.moonfin/ios_storage');

  static Future<void> excludeFromBackup(String path) async {
    if (!Platform.isIOS) return;
    try {
      await _channel.invokeMethod('excludeFromBackup', {'path': path});
    } catch (_) {}
  }
}
