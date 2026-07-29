import 'dart:io';
import 'package:flutter/foundation.dart';

class PlatformDetection {
  const PlatformDetection._();

  static Map<String, String> get _environment => Platform.environment;

  static bool get isAndroid => !kIsWeb && Platform.isAndroid;
  static bool get isIOS => !kIsWeb && Platform.isIOS;
  static bool get isMacOS => !kIsWeb && Platform.isMacOS;
  static bool get isWindows => !kIsWeb && Platform.isWindows;
  static bool get isLinux => !kIsWeb && Platform.isLinux;
  static bool get isWeb => kIsWeb;

  static String get linuxSessionType {
    if (!isLinux) {
      return '';
    }

    final sessionType = _environment['XDG_SESSION_TYPE']?.toLowerCase();
    if (sessionType == 'wayland' || sessionType == 'x11') {
      return sessionType!;
    }
    if ((_environment['WAYLAND_DISPLAY']?.isNotEmpty ?? false)) {
      return 'wayland';
    }
    if ((_environment['DISPLAY']?.isNotEmpty ?? false)) {
      return 'x11';
    }
    return '';
  }

  static bool get isLinuxWayland => linuxSessionType == 'wayland';
  static bool get isLinuxX11 => linuxSessionType == 'x11';

  static bool get isMobile => isAndroid || isIOS;
  static bool get isDesktop => isMacOS || isWindows || isLinux;

  static bool get isTV => _isTv;
  static bool _isTv = false;
  static void setTvMode(bool value) => _isTv = value;

  /// Whether to use a 10-foot (lean-back) UI optimized for remote control.
  static bool get useLeanbackUi => isTV;
  static bool get useDesktopUi => isDesktop && !isTV;
  static bool get useMobileUi => isMobile && !isTV;
}
