import 'dart:async';

import 'package:playback_core/playback_core.dart';

import 'native_airplay_channel.dart';

class AirPlayCommandBridge {
  final NativeAirPlayChannel _native;
  final PlaybackManager _manager;

  StreamSubscription<Map<String, dynamic>>? _sub;
  bool _started = false;

  AirPlayCommandBridge(this._native, this._manager);

  void start() {
    if (_started) return;
    _started = true;
    _sub = _native.airPlayEventStream().listen(
      _handleEvent,
      onError: (_) {},
    );
  }

  Future<void> dispose() async {
    await _sub?.cancel();
    _sub = null;
    _started = false;
  }

  void _handleEvent(Map<String, dynamic> event) {
    final kind = event['kind'] as String?;
    if (kind != 'airPlay') return;

    final state = event['state'] as String?;
    if (state != 'command') return;

    switch (event['command'] as String?) {
      case 'play':
        _manager.resume();
      case 'pause':
        _manager.pause();
      case 'seek':
        final ticks = event['positionTicks'] as int?;
        if (ticks != null) {
          _manager.seekTo(Duration(microseconds: ticks ~/ 10));
        }
    }
  }
}
