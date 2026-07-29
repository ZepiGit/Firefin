abstract class AdminBackupApi {
  Future<List<Map<String, dynamic>>> getBackups();
  Future<Map<String, dynamic>> createBackup();
  Future<void> restoreBackup(String backupPath);
  Future<Map<String, dynamic>> getBackupManifest(String backupPath);
}
