abstract class AuthApi {
  Future<Map<String, dynamic>> authenticateByName(
    String username,
    String password,
  );

  Future<Map<String, dynamic>> initiateQuickConnect();

  Future<Map<String, dynamic>> checkQuickConnect(String secret);

  Future<bool> authorizeQuickConnect(String code, {String? userId});

  Future<Map<String, dynamic>> authenticateWithQuickConnect(String secret);

  Future<void> logout();
}
