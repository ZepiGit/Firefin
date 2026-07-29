import 'package:flutter_test/flutter_test.dart';
import 'package:server_core/server_core.dart';
import 'package:server_emby/server_emby.dart';
import 'package:server_jellyfin/src/api/jellyfin_image_api.dart';
import 'package:server_jellyfin/server_jellyfin.dart';

void main() {
  const deviceInfo = DeviceInfo(
    id: 'test-device',
    name: 'Test Device',
    appName: 'Moonfin Test',
    appVersion: '1.0',
  );

  group('image API base URL normalization', () {
    test('Jellyfin removes trailing slashes without losing a subpath', () {
      final api = JellyfinImageApi('https://example.test/jellyfin///');

      final uri = Uri.parse(api.getPrimaryImageUrl('item-id'));

      expect(uri.path, '/jellyfin/Items/item-id/Images/Primary');
      expect(uri.path, isNot(contains('//')));
    });

    test('Emby removes trailing slashes from a dynamic base URL', () {
      final api = EmbyImageApi(
        () => 'https://example.test/emby/',
        () => 'token',
      );

      final uri = Uri.parse(api.getBackdropImageUrl('item-id'));

      expect(uri.path, '/emby/Items/item-id/Images/Backdrop/0');
      expect(uri.path, isNot(contains('//')));
      expect(uri.queryParameters['api_key'], 'token');
    });
  });

  group('media server client base URL normalization', () {
    test('Jellyfin normalizes constructor and setter values', () {
      final client = JellyfinMediaServerClient(
        baseUrl: 'https://example.test/jellyfin/',
        deviceInfo: deviceInfo,
      );
      addTearDown(client.dispose);

      expect(client.baseUrl, 'https://example.test/jellyfin');
      client.baseUrl = 'https://other.test///';
      expect(client.baseUrl, 'https://other.test');
    });

    test('Emby normalizes constructor and setter values', () {
      final client = EmbyMediaServerClient(
        baseUrl: 'https://example.test/emby/',
        deviceInfo: deviceInfo,
      );
      addTearDown(client.dispose);

      expect(client.baseUrl, 'https://example.test/emby');
      client.baseUrl = 'https://other.test///';
      expect(client.baseUrl, 'https://other.test');
    });
  });
}
