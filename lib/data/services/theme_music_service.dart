import 'dart:async';
import 'dart:math';

import 'package:media_kit/media_kit.dart';
import 'package:server_core/server_core.dart';

import '../../preference/user_preferences.dart';
import '../models/aggregated_item.dart';

class ThemeMusicService {
  final MediaServerClient _client;
  final UserPreferences _prefs;

  Player? _player;
  String? _currentItemId;
  Timer? _fadeTimer;
  double _targetVolume = 0.0;

  static const _fadeDurationMs = 2000;
  static const _fadeStepMs = 50;
  static const _validTypes = {'Series', 'Movie', 'Season', 'Episode'};

  ThemeMusicService(this._client, this._prefs);

  bool get isPlaying => _player?.state.playing ?? false;

  Future<void> playForItem(AggregatedItem item) async {
    if (!_prefs.get(UserPreferences.themeMusicEnabled)) return;
    if (!_validTypes.contains(item.type)) return;
    if (_currentItemId == item.id && isPlaying) return;

    stop();
    _currentItemId = item.id;

    final themeItemId = (item.type == 'Episode' && item.seriesId != null)
        ? item.seriesId!
        : item.id;

    try {
      final data = await _client.itemsApi.getThemeMedia(themeItemId);
      final songsResult = data['ThemeSongsResult'] as Map<String, dynamic>?;
      final songs = (songsResult?['Items'] as List?)?.cast<Map<String, dynamic>>() ?? [];
      if (songs.isEmpty) return;
      if (_currentItemId != item.id) return;

      final song = songs[Random().nextInt(songs.length)];
      final songId = song['Id'] as String;
      final url = _buildAudioUrl(songId);

      _targetVolume = _prefs.get(UserPreferences.themeMusicVolume) / 100.0;
      _player = Player();
      await _player!.setVolume(0);
      await _player!.open(Media(url));
      await _player!.setPlaylistMode(PlaylistMode.loop);
      _fadeIn();
    } catch (_) {}
  }

  String _buildAudioUrl(String songId) {
    final token = _client.accessToken ?? '';
    return '${_client.baseUrl}/Audio/$songId/stream?static=true&audioCodec=mp3&audioBitrate=128000&api_key=$token';
  }

  void _fadeIn() {
    _fadeTimer?.cancel();
    final steps = _fadeDurationMs ~/ _fadeStepMs;
    var step = 0;
    _fadeTimer = Timer.periodic(Duration(milliseconds: _fadeStepMs), (timer) {
      step++;
      final volume = (step / steps) * _targetVolume * 100;
      _player?.setVolume(volume.clamp(0, 100));
      if (step >= steps) timer.cancel();
    });
  }

  void fadeOutAndStop() {
    final player = _player;
    if (player == null) return;

    _fadeTimer?.cancel();
    final currentVolume = player.state.volume;
    final steps = _fadeDurationMs ~/ _fadeStepMs;
    var step = 0;

    _fadeTimer = Timer.periodic(Duration(milliseconds: _fadeStepMs), (timer) {
      step++;
      final volume = currentVolume * (1.0 - step / steps);
      player.setVolume(volume.clamp(0, 100));
      if (step >= steps) {
        timer.cancel();
        stop();
      }
    });
  }

  void stop() {
    _fadeTimer?.cancel();
    _fadeTimer = null;
    _player?.dispose();
    _player = null;
    _currentItemId = null;
  }

  void dispose() {
    stop();
  }
}
