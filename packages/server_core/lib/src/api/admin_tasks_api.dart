abstract class AdminTasksApi {
  Future<List<Map<String, dynamic>>> getTasks({bool? isHidden});
  Future<Map<String, dynamic>> getTask(String taskId);
  Future<void> startTask(String taskId);
  Future<void> stopTask(String taskId);
  Future<void> updateTaskTriggers(
      String taskId, List<Map<String, dynamic>> triggers);
}
