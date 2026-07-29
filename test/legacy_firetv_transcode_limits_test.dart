import 'package:flutter_test/flutter_test.dart';
import 'package:playback_jellyfin/playback_jellyfin.dart';

void main() {
  const legacyProfile = <String, dynamic>{
    'Name': 'Moonfin for Fire TV (32-bit)',
    'MaxStreamingBitrate': 4000000,
  };

  test('caps legacy Fire TV transcode URL at 720p and 4 Mbps total', () {
    final result = applyLegacyFireTvTranscodeLimits(
      'https://example.test/video.m3u8?videoBitrate=8554687&audioBitrate=224000&maxWidth=1920&maxHeight=1080&playSessionId=abc',
      legacyProfile,
    );
    final parameters = Uri.parse(result).queryParameters;

    expect(parameters['maxWidth'], '1280');
    expect(parameters['maxHeight'], '720');
    expect(parameters['videoBitrate'], '3776000');
    expect(parameters['audioBitrate'], '224000');
    expect(parameters['playSessionId'], 'abc');
  });

  test('does not increase an already lower transcode setting', () {
    final result = applyLegacyFireTvTranscodeLimits(
      'https://example.test/video.m3u8?videoBitrate=1750000&audioBitrate=128000&maxWidth=960&maxHeight=540',
      legacyProfile,
    );
    final parameters = Uri.parse(result).queryParameters;

    expect(parameters['maxWidth'], '960');
    expect(parameters['maxHeight'], '540');
    expect(parameters['videoBitrate'], '1750000');
  });

  test('leaves other device profiles unchanged', () {
    const url = 'https://example.test/video.m3u8?videoBitrate=12000000';
    expect(
      applyLegacyFireTvTranscodeLimits(url, const {'Name': 'Moonfin'}),
      url,
    );
  });
}
