import 'package:flutter_test/flutter_test.dart';
import 'package:playback_jellyfin/playback_jellyfin.dart';

void main() {
  const legacyProfile = <String, dynamic>{
    'Name': 'Moonfin for Fire TV (32-bit)',
    'MaxStreamingBitrate': 4000000,
  };

  test('caps legacy Fire TV transcode URL at 720p and 4 Mbps total', () {
    final result = applyLegacyFireTvTranscodeLimits(
      'https://example.test/video.m3u8?VideoBitrate=8554687&AudioBitrate=224000&MaxWidth=1920&MaxHeight=1080&PlaySessionId=abc',
      legacyProfile,
    );
    final parameters = Uri.parse(result).queryParameters;

    expect(parameters['MaxWidth'], '1280');
    expect(parameters['MaxHeight'], '720');
    expect(parameters['VideoBitrate'], '3776000');
    expect(parameters['AudioBitrate'], '224000');
    expect(parameters['PlaySessionId'], 'abc');
    expect(parameters.containsKey('videoBitrate'), isFalse);
  });

  test('does not increase an already lower transcode setting', () {
    final result = applyLegacyFireTvTranscodeLimits(
      'https://example.test/video.m3u8?VideoBitrate=1750000&AudioBitrate=128000&MaxWidth=960&MaxHeight=540',
      legacyProfile,
    );
    final parameters = Uri.parse(result).queryParameters;

    expect(parameters['MaxWidth'], '960');
    expect(parameters['MaxHeight'], '540');
    expect(parameters['VideoBitrate'], '1750000');
  });

  test('leaves other device profiles unchanged', () {
    const url = 'https://example.test/video.m3u8?videoBitrate=12000000';
    expect(
      applyLegacyFireTvTranscodeLimits(url, const {'Name': 'Moonfin'}),
      url,
    );
  });
}
