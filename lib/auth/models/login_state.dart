import 'server.dart';

sealed class LoginState {
  const LoginState();
}

class Authenticating extends LoginState {
  const Authenticating();
}

class RequireSignIn extends LoginState {
  const RequireSignIn();
}

class ServerUnavailable extends LoginState {
  const ServerUnavailable();
}

class VersionNotSupported extends LoginState {
  final Server server;
  const VersionNotSupported({required this.server});
}

class ApiClientError extends LoginState {
  final String error;
  const ApiClientError({required this.error});
}

class Authenticated extends LoginState {
  final String userId;
  final String serverId;
  const Authenticated({required this.userId, required this.serverId});
}
