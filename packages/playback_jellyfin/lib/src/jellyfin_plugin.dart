import 'package:server_core/server_core.dart';
import 'jellyfin_media_stream_resolver.dart';
import 'play_session_service.dart';

class JellyfinPlugin {
  final MediaServerClient _client;

  JellyfinPlugin(this._client);

  JellyfinMediaStreamResolver createStreamResolver() =>
      JellyfinMediaStreamResolver(_client);

  PlaySessionService createPlaySessionService() =>
      PlaySessionService(_client);
}
