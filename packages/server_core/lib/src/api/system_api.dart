abstract class SystemApi {
  /// No auth required.
  Future<Map<String, dynamic>> getPublicSystemInfo();

  /// Requires auth.
  Future<Map<String, dynamic>> getSystemInfo();

  Future<bool> ping();
}
