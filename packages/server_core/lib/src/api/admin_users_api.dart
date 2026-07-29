import '../models/server_models.dart';

abstract class AdminUsersApi {
  Future<List<ServerUser>> getUsers({bool? isDisabled, bool? isHidden});
  Future<ServerUser> getUserById(String userId);
  Future<ServerUser> createUser(String name, String? password);
  Future<void> deleteUser(String userId);
  Future<void> updateUser(String userId, Map<String, dynamic> userData);
  Future<void> updateUserPolicy(String userId, Map<String, dynamic> policy);
  Future<void> updateUserPassword(
    String userId, {
    String? newPassword,
    bool resetPassword = false,
  });
}
