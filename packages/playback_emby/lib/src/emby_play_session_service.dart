import 'package:playback_core/playback_core.dart';
import 'package:server_core/server_core.dart';

class EmbyPlaySessionService implements PlayerService {
  final MediaServerClient _client;

  EmbyPlaySessionService(this._client);

  @override
  Future<void> onPlaybackStart(
    dynamic mediaItem,
    StreamResolutionResult resolution, {
    int? positionTicks,
    int? audioStreamIndex,
    int? subtitleStreamIndex,
  }) async {
    final report = PlaybackStartReport(
      itemId: MediaStreamResolver.extractItemId(mediaItem),
      mediaSourceId: resolution.mediaSourceId,
      playSessionId: resolution.playSessionId,
      playMethod: _toPlayMethod(resolution.playMethod),
      positionTicks: positionTicks,
      audioStreamIndex: audioStreamIndex,
      subtitleStreamIndex: subtitleStreamIndex,
    );
    await _client.playbackApi.reportPlaybackStart(report.toJson());
  }

  @override
  Future<void> onPlaybackProgress(
    dynamic mediaItem,
    StreamResolutionResult resolution,
    Duration position, {
    bool isPaused = false,
    int? audioStreamIndex,
    int? subtitleStreamIndex,
  }) async {
    final report = PlaybackProgressReport(
      itemId: MediaStreamResolver.extractItemId(mediaItem),
      mediaSourceId: resolution.mediaSourceId,
      playSessionId: resolution.playSessionId,
      positionTicks: position.inMicroseconds * 10,
      isPaused: isPaused,
      audioStreamIndex: audioStreamIndex,
      subtitleStreamIndex: subtitleStreamIndex,
    );
    await _client.playbackApi.reportPlaybackProgress(report.toJson());
  }

  @override
  Future<void> onPlaybackStop(
    dynamic mediaItem,
    StreamResolutionResult resolution,
    Duration position,
  ) async {
    final report = PlaybackStopReport(
      itemId: MediaStreamResolver.extractItemId(mediaItem),
      mediaSourceId: resolution.mediaSourceId,
      playSessionId: resolution.playSessionId,
      positionTicks: position.inMicroseconds * 10,
    );
    await _client.playbackApi.reportPlaybackStopped(report.toJson());
  }

  static PlayMethod _toPlayMethod(StreamPlayMethod method) => switch (method) {
    StreamPlayMethod.directPlay => PlayMethod.directPlay,
    StreamPlayMethod.directStream => PlayMethod.directStream,
    StreamPlayMethod.transcode => PlayMethod.transcode,
  };

  @override
  void dispose() {}
}
