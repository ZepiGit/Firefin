import 'dart:io';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter_cache_manager/flutter_cache_manager.dart';
import 'package:http/io_client.dart';

import 'platform_detection.dart';

/// Keeps HTTPS image loading usable on the frozen Android 5.1 trust store.
///
/// Fire OS 5.2.9.5 no longer receives CA updates and rejects current public
/// chains such as GTS Root R4 before the app can inspect them. This override is
/// intentionally limited to the dedicated legacy Fire TV distribution.
void installLegacyFireTvCertificateSupport() {
  if (!PlatformDetection.isAndroid || !PlatformDetection.isTV) return;

  // CachedNetworkImage's default singleton may be initialized before main()
  // configures the Fire TV downloader. Replace it with a narrowly scoped
  // client and use a fresh cache key so failed TLS downloads are not reused.
  final imageHttpClient = HttpClient();
  imageHttpClient.badCertificateCallback = (_, _, _) => true;
  imageHttpClient.connectionTimeout = const Duration(seconds: 20);
  imageHttpClient.idleTimeout = const Duration(seconds: 60);
  imageHttpClient.maxConnectionsPerHost = 2;
  CachedNetworkImageProvider.defaultCacheManager =
      _LegacyFireTvImageCacheManager(imageHttpClient);
}

class _LegacyFireTvImageCacheManager extends CacheManager
    with ImageCacheManager {
  _LegacyFireTvImageCacheManager(HttpClient httpClient)
    : super(
        Config(
          'moonfinLegacyFireTvImagesV2',
          stalePeriod: const Duration(days: 14),
          maxNrOfCacheObjects: 180,
          fileService: HttpFileService(httpClient: IOClient(httpClient)),
        ),
      );
}
