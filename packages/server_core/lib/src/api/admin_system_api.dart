import '../models/admin_models.dart';

abstract class AdminSystemApi {
  Future<Map<String, dynamic>> getServerConfiguration();
  Future<void> updateServerConfiguration(Map<String, dynamic> config);
  Future<Map<String, dynamic>> getNamedConfiguration(String key);
  Future<void> updateNamedConfiguration(String key, Map<String, dynamic> config);
  Future<StorageInfo> getStorageInfo();
  Future<void> restartServer();
  Future<void> shutdownServer();
  Future<List<LogFileInfo>> getLogFiles();
  Future<String> getLogFileContent(String name);
  Future<ActivityLogResult> getActivityLog({
    int? startIndex,
    int? limit,
    bool? hasUserId,
    DateTime? minDate,
  });
}
