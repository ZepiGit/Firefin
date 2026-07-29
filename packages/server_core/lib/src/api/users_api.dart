import '../models/server_models.dart';
import '../models/system_models.dart';

abstract class UsersApi {
  Future<ServerUser> getCurrentUser();
  Future<UserConfiguration> getUserConfiguration();
  Future<void> updateUserConfiguration(UserConfiguration config);
}
