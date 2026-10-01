import 'package:flutter_test/flutter_test.dart';
import 'package:moonfin/util/legacy_firetv_network_image.dart';

void main() {
  group('LegacyFireTvNetworkImage decode-size keying', () {
    test('equality and hashCode include cacheWidth/cacheHeight', () {
      const a = LegacyFireTvNetworkImage(
        'https://example.test/a.jpg',
        cacheWidth: 160,
        cacheHeight: 240,
      );
      const b = LegacyFireTvNetworkImage(
        'https://example.test/a.jpg',
        cacheWidth: 160,
        cacheHeight: 240,
      );
      const differentSize = LegacyFireTvNetworkImage(
        'https://example.test/a.jpg',
        cacheWidth: 320,
        cacheHeight: 480,
      );
      const differentUrl = LegacyFireTvNetworkImage(
        'https://example.test/b.jpg',
        cacheWidth: 160,
        cacheHeight: 240,
      );

      expect(a, equals(b));
      expect(a.hashCode, equals(b.hashCode));
      expect(a, isNot(equals(differentSize)));
      expect(a, isNot(equals(differentUrl)));
    });

    test('null decode sizes differ from explicit sizes', () {
      const full = LegacyFireTvNetworkImage('https://example.test/a.jpg');
      const sized = LegacyFireTvNetworkImage(
        'https://example.test/a.jpg',
        cacheWidth: 160,
      );
      expect(full, isNot(equals(sized)));
    });

    test('Fire TV image Dio concurrency cap is 2–3', () {
      expect(
        LegacyFireTvNetworkImage.maxConnectionsPerHost,
        inInclusiveRange(2, 3),
      );
    });
  });

  group('Welle B width-only / height-only keying', () {
    test('width-only keys differ from dual-target keys', () {
      const widthOnly = LegacyFireTvNetworkImage(
        'https://example.test/a.jpg',
        cacheWidth: 240,
      );
      const dual = LegacyFireTvNetworkImage(
        'https://example.test/a.jpg',
        cacheWidth: 240,
        cacheHeight: 360,
      );
      expect(widthOnly, isNot(equals(dual)));
    });

    test('height-only landscape keys are stable', () {
      const a = LegacyFireTvNetworkImage(
        'https://example.test/a.jpg',
        cacheHeight: 270,
      );
      const b = LegacyFireTvNetworkImage(
        'https://example.test/a.jpg',
        cacheHeight: 270,
      );
      const other = LegacyFireTvNetworkImage(
        'https://example.test/a.jpg',
        cacheHeight: 320,
      );
      expect(a, equals(b));
      expect(a, isNot(equals(other)));
    });
  });
}
