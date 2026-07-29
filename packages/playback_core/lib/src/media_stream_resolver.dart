import 'stream_resolution_result.dart';

abstract class MediaStreamResolver {
  static String extractItemId(dynamic mediaItem) {
    if (mediaItem is Map) return mediaItem['Id'] as String;
    return mediaItem.id as String;
  }

  static String applyStreamIndices(
    String url,
    int? audioStreamIndex,
    int? subtitleStreamIndex,
  ) {
    var result = url;

    if (audioStreamIndex != null) {
      final audioRegex = RegExp(r'AudioStreamIndex=\d+');
      if (audioRegex.hasMatch(result)) {
        result = result.replaceFirst(
          audioRegex,
          'AudioStreamIndex=$audioStreamIndex',
        );
      } else {
        result = '$result&AudioStreamIndex=$audioStreamIndex';
      }
    }

    if (subtitleStreamIndex != null && subtitleStreamIndex >= 0) {
      final subRegex = RegExp(r'SubtitleStreamIndex=\d+');
      if (subRegex.hasMatch(result)) {
        result = result.replaceFirst(
          subRegex,
          'SubtitleStreamIndex=$subtitleStreamIndex',
        );
      } else {
        result = '$result&SubtitleStreamIndex=$subtitleStreamIndex';
      }
    } else if (subtitleStreamIndex == -1) {
      result = result.replaceAll(RegExp(r'[&?]SubtitleStreamIndex=\d+'), '');
    }

    return result;
  }

  /// Applies the final compatibility ceiling for the dedicated API 22 Fire
  /// TV build. This is shared by both server resolvers because installations
  /// upgraded from older Moonfin versions may retain either server type.
  static String applyLegacyFireTvTranscodeLimits(
    String url,
    Map<String, dynamic>? deviceProfile,
  ) {
    final isLegacyFireTv =
        deviceProfile?['MoonfinLegacyFireTv'] == true ||
        deviceProfile?['Name'] == 'Moonfin for Fire TV (32-bit)';
    if (!isLegacyFireTv) {
      return url;
    }

    final uri = Uri.parse(url);
    final parameters = Map<String, String>.from(uri.queryParameters);
    final maxStreamingBitrate = deviceProfile?['MaxStreamingBitrate'] as int?;

    _setLowerInt(parameters, 'MaxWidth', 1280);
    _setLowerInt(parameters, 'MaxHeight', 720);

    if (maxStreamingBitrate != null) {
      final audioBitrate =
          int.tryParse(_valueIgnoreCase(parameters, 'AudioBitrate') ?? '') ??
          224000;
      final availableVideoBitrate = (maxStreamingBitrate - audioBitrate).clamp(
        250000,
        maxStreamingBitrate,
      );
      _setLowerInt(parameters, 'VideoBitrate', availableVideoBitrate);
    }

    return uri.replace(queryParameters: parameters).toString();
  }

  static String? _keyIgnoreCase(Map<String, String> parameters, String name) {
    final lowerName = name.toLowerCase();
    for (final key in parameters.keys) {
      if (key.toLowerCase() == lowerName) return key;
    }
    return null;
  }

  static String? _valueIgnoreCase(Map<String, String> parameters, String name) {
    final key = _keyIgnoreCase(parameters, name);
    return key == null ? null : parameters[key];
  }

  static void _setLowerInt(
    Map<String, String> parameters,
    String canonicalName,
    int ceiling,
  ) {
    final existingKey = _keyIgnoreCase(parameters, canonicalName);
    final current = int.tryParse(
      existingKey == null ? '' : parameters[existingKey] ?? '',
    );
    final value = current == null || current > ceiling ? ceiling : current;
    parameters[existingKey ?? canonicalName] = value.toString();
  }

  static List<ExternalSubtitle> extractExternalSubtitles(
    List<Map<String, dynamic>> mediaStreams,
    String baseUrl,
  ) {
    final subs = <ExternalSubtitle>[];
    for (final stream in mediaStreams) {
      if (stream['Type'] != 'Subtitle') continue;
      final deliveryUrl = stream['DeliveryUrl'] as String?;
      if (deliveryUrl == null || deliveryUrl.isEmpty) continue;
      final isExternal = stream['IsExternal'] == true;
      final supportsExternal = stream['SupportsExternalStream'] == true;
      if (!isExternal && !supportsExternal) continue;
      subs.add(
        ExternalSubtitle(
          deliveryUrl: '$baseUrl$deliveryUrl',
          title:
              stream['DisplayTitle'] as String? ?? stream['Title'] as String?,
          language: stream['Language'] as String?,
          codec: (stream['Codec'] as String?) ?? 'srt',
          isDefault: stream['IsDefault'] as bool? ?? false,
          isForced: stream['IsForced'] as bool? ?? false,
          streamIndex: stream['Index'] as int?,
        ),
      );
    }
    return subs;
  }

  Future<StreamResolutionResult> resolve(
    dynamic mediaItem, {
    Map<String, dynamic>? deviceProfile,
    int? maxStreamingBitrate,
    int? audioStreamIndex,
    int? subtitleStreamIndex,
    int? startTimeTicks,
    String? mediaSourceId,
    bool enableDirectPlay = true,
    bool enableDirectStream = true,
  });
}
