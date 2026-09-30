import 'dart:math' as math;

import 'platform_detection.dart';

/// Central Fire TV / Leanback artwork size policy (Welle B).
///
/// One policy, two image stacks (LegacyFireTvNetworkImage + CachedNetworkImage).
/// Format classes — not "320 everywhere":
/// - Poster 2:3 → server [posterServerMaxWidth]
/// - Landscape thumbs → [landscapeServerMaxWidth]
/// - Backdrop → [backdropServerMaxWidth]
///
/// Decode under BoxFit.cover: cover posters are **width-only**; landscape
/// thumbs use **height-only** so aspect is preserved (r21 dual-target fix).
class TvImageSizePolicy {
  const TvImageSizePolicy._();

  static bool get isLeanTv =>
      PlatformDetection.isAndroid && PlatformDetection.isTV;

  /// Server URL class: 2:3 cover posters.
  static const int posterServerMaxWidth = 320;

  /// Derived poster maxHeight for callers still on the height axis
  /// (2:3 → 320×480). Prefer [posterServerMaxWidth] for new URLs.
  static const int posterServerMaxHeight = 480;

  /// Server URL class: 16:9 / banner / thumb rows (between poster and backdrop).
  static const int landscapeServerMaxWidth = 640;

  /// Server / memCache class: full-screen backdrops.
  static const int backdropServerMaxWidth = 960;

  /// Fixed decode-width / decode-height buckets (device pixels). Round **up**.
  static const List<int> decodeBuckets = <int>[160, 240, 320, 480, 640, 960];

  static int posterMaxWidth({int? requested}) {
    if (!isLeanTv) {
      return requested ?? posterServerMaxWidth;
    }
    if (requested == null) {
      return posterServerMaxWidth;
    }
    return math.min(requested, posterServerMaxWidth);
  }

  static int posterMaxHeight({int? requested}) {
    if (!isLeanTv) {
      return requested ?? posterServerMaxHeight;
    }
    if (requested == null) {
      return posterServerMaxHeight;
    }
    return math.min(requested, posterServerMaxHeight);
  }

  static int landscapeMaxWidth({int? requested}) {
    if (!isLeanTv) {
      return requested ?? landscapeServerMaxWidth;
    }
    if (requested == null) {
      return landscapeServerMaxWidth;
    }
    return math.min(requested, landscapeServerMaxWidth);
  }

  static int backdropMaxWidth({int? requested}) {
    if (!isLeanTv) {
      return requested ?? 1920;
    }
    if (requested == null) {
      return backdropServerMaxWidth;
    }
    return math.min(requested, backdropServerMaxWidth);
  }

  /// Round up to the next fixed decode bucket (keeps size in cache key).
  static int bucketDecodeSize(int devicePixels) {
    final clamped = devicePixels.clamp(1, decodeBuckets.last);
    for (final bucket in decodeBuckets) {
      if (clamped <= bucket) {
        return bucket;
      }
    }
    return decodeBuckets.last;
  }

  /// Cover posters / square: width-only. Landscape (aspect > 1): height-only.
  static bool useWidthOnlyDecode(double aspectRatio) => aspectRatio <= 1.01;

  /// Device-pixel decode targets for BoxFit.cover cards.
  static ({int? cacheWidth, int? cacheHeight}) decodeTargets({
    required double logicalWidth,
    required double logicalHeight,
    required double devicePixelRatio,
    required double aspectRatio,
  }) {
    final rawW = (logicalWidth * devicePixelRatio).round().clamp(1, 4096);
    final rawH = (logicalHeight * devicePixelRatio).round().clamp(1, 4096);
    if (useWidthOnlyDecode(aspectRatio)) {
      return (cacheWidth: bucketDecodeSize(rawW), cacheHeight: null);
    }
    return (cacheWidth: null, cacheHeight: bucketDecodeSize(rawH));
  }
}
