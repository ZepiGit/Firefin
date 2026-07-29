import 'dart:async';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/painting.dart';
import 'package:server_core/server_core.dart';

/// Lightweight network image provider for the frozen Fire OS 5 TLS stack.
///
/// It deliberately bypasses flutter_cache_manager on the legacy TV build.
/// Flutter's bounded in-memory [ImageCache] still prevents duplicate downloads
/// while the app is running, and callers request server-scaled image sizes.
@immutable
class LegacyFireTvNetworkImage extends ImageProvider<LegacyFireTvNetworkImage> {
  const LegacyFireTvNetworkImage(this.url, {this.scale = 1});

  final String url;
  final double scale;

  static final Dio _dio = _createDio();

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
      // Skia's proven Android 5 path and is bounded by the server-scaled image
      // dimensions supplied by callers.
      return await ui.instantiateImageCodec(bytes).timeout(
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
        other.scale == scale;
  }

  @override
  int get hashCode => Object.hash(url, scale);

  @override
  String toString() => 'LegacyFireTvNetworkImage("$url", scale: $scale)';
}
