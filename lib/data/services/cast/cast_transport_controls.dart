import 'cast_target.dart';

abstract class CastTransportControls {
  Set<CastTargetKind> get controllableKinds;

  Future<void> play(CastTargetKind kind);

  Future<void> pause(CastTargetKind kind);

  Future<void> seek(CastTargetKind kind, {required int positionTicks});

  Future<void> stop(CastTargetKind kind);

  Future<double?> getVolume(CastTargetKind kind);

  Future<void> setVolume(CastTargetKind kind, {required double volume});
}
