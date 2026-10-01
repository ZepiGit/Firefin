import 'dart:async';
import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:dio/dio.dart';
import 'package:dio/io.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/painting.dart';
import 'package:server_core/server_core.dart';

/// Lightweight network image provider for the frozen Fire OS 5 TLS stack.
///
/// It deliberately bypasses flutter_cache_manager on the legacy TV build.
/// Flutter's bounded in-memory [ImageCache] still prevents duplicate downloads
/// while the app is running, and callers request server-scaled image sizes.
///
/// Optional [cacheWidth] / [cacheHeight] are **device pixels** (logical size ×
/// [MediaQuery.devicePixelRatio]), matching Flutter's [ResizeImage] /
/// `instantiateImageCodec(targetWidth/Height:)` convention so Skia downsamples
/// on decode instead of retaining a full-resolution bitmap in RAM.
@immutable
class LegacyFireTvNetworkImage extends ImageProvider<LegacyFireTvNetworkImage> {
  const LegacyFireTvNetworkImage(
    this.url, {
    this.scale = 1,
    this.cacheWidth,
    this.cacheHeight,
  });

  final String url;
  final double scale;

  /// Decode target width in device pixels; null keeps the source width.
  final int? cacheWidth;

  /// Decode target height in device pixels; null keeps the source height.
  final int? cacheHeight;

  static final Dio _dio = _createDio();

  /// Shared by tests: Fire TV image host concurrency (decode-storm cap).
  @visibleForTesting
  static const int maxConnectionsPerHost = 2;

  static Dio _createDio() {
    final dio = Dio(
      BaseOptions(
        connectTimeout: const Duration(seconds: 15),
        receiveTimeout: const Duration(seconds: 20),
        sendTimeout: const Duration(seconds: 15),
        responseType: ResponseType.bytes,
      ),
    );
    // Reuse the same Fire OS 5-compatible transport as the working Jellyfin
    // metadata client. A separate raw HttpClient could remain stuck during the
    // TLS handshake on AFTT devices after app updates.
    configureServerDio(dio);
    // Override the server client's higher connection budget: poster grids on
    // 1 GB AFTT decode-storm when many hosts open in parallel during D-pad
    // scrolling. Cap the image Dio only (API Dio stays unchanged).
    dio.httpClientAdapter = IOHttpClientAdapter(
      createHttpClient: () {
        final client = HttpClient();
        client.badCertificateCallback = (_, __, ___) => true;
        client.connectionTimeout = const Duration(seconds: 15);
        client.idleTimeout = const Duration(seconds: 60);
        client.maxConnectionsPerHost = maxConnectionsPerHost;
        return client;
      },
    );
    return dio;
  }

  @override
  Future<LegacyFireTvNetworkImage> obtainKey(ImageConfiguration configuration) {
    return SynchronousFuture<LegacyFireTvNetworkImage>(this);
  }

  @override
  ImageStreamCompleter loadImage(
    LegacyFireTvNetworkImage key,
    ImageDecoderCallback decode,
  ) {
    final chunkEvents = StreamController<ImageChunkEvent>();
    return MultiFrameImageStreamCompleter(
      codec: _loadAsync(key, chunkEvents, decode),
      chunkEvents: chunkEvents.stream,
      scale: key.scale,
      informationCollector: () => <DiagnosticsNode>[
        DiagnosticsProperty<ImageProvider>('Image provider', this),
        DiagnosticsProperty<String>('Image URL', url),
      ],
    );
  }

  Future<ui.Codec> _loadAsync(
    LegacyFireTvNetworkImage key,
    StreamController<ImageChunkEvent> chunkEvents,
    ImageDecoderCallback _,
  ) async {
    try {
      final response = await _dio
          .get<List<int>>(
            key.url,
            options: Options(responseType: ResponseType.bytes),
            onReceiveProgress: (received, total) {
              chunkEvents.add(
                ImageChunkEvent(
                  cumulativeBytesLoaded: received,
                  expectedTotalBytes: total > 0 ? total : null,
                ),
              );
            },
          )
          .timeout(
            const Duration(seconds: 12),
            onTimeout: () => throw TimeoutException('HTTP image request'),
          );
      if (response.statusCode != 200) {
        throw HttpException(
          'Image request failed with status ${response.statusCode}',
          uri: Uri.parse(key.url),
        );
      }

      final bytes = Uint8List.fromList(response.data ?? const <int>[]);
      if (bytes.isEmpty) {
        throw StateError('Image response was empty');
      }

      // Fire OS 5 can stall indefinitely in Flutter's newer
      // ImmutableBuffer/ImageDescriptor path. The legacy byte decoder uses
      // Skia's proven Android 5 path. targetWidth/Height downsample on decode
      // so 1 GB sticks do not retain full server bitmaps for tiny posters.
      return await ui
          .instantiateImageCodec(
            bytes,
            targetWidth: key.cacheWidth,
            targetHeight: key.cacheHeight,
          )
          .timeout(
            const Duration(seconds: 12),
            onTimeout: () => throw TimeoutException('Image decode'),
          );
    } catch (_) {
      scheduleMicrotask(() {
        PaintingBinding.instance.imageCache.evict(key);
      });
      rethrow;
    } finally {
      await chunkEvents.close();
    }
  }

  @override
  bool operator ==(Object other) {
    return other is LegacyFireTvNetworkImage &&
        other.url == url &&
        other.scale == scale &&
        other.cacheWidth == cacheWidth &&
        other.cacheHeight == cacheHeight;
  }

  @override
  int get hashCode => Object.hash(url, scale, cacheWidth, cacheHeight);

  @override
  String toString() =>
      'LegacyFireTvNetworkImage("$url", scale: $scale, '
      'cacheWidth: $cacheWidth, cacheHeight: $cacheHeight)';
}
