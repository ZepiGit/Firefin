import 'package:dio/dio.dart';
import 'package:server_core/server_core.dart';

class JellyfinAdminTasksApi implements AdminTasksApi {
  final Dio _dio;

  JellyfinAdminTasksApi(this._dio);

  @override
  Future<List<Map<String, dynamic>>> getTasks({bool? isHidden}) async {
    final response = await _dio.get(
      '/ScheduledTasks',
      queryParameters: {
        if (isHidden != null) 'isHidden': isHidden,
      },
    );
    return (response.data as List<dynamic>).cast<Map<String, dynamic>>();
  }

  @override
  Future<Map<String, dynamic>> getTask(String taskId) async {
    final response = await _dio.get('/ScheduledTasks/$taskId');
    return response.data as Map<String, dynamic>;
  }

  @override
  Future<void> startTask(String taskId) async {
    await _dio.post('/ScheduledTasks/Running/$taskId');
  }

  @override
  Future<void> stopTask(String taskId) async {
    await _dio.delete('/ScheduledTasks/Running/$taskId');
  }

  @override
  Future<void> updateTaskTriggers(
      String taskId, List<Map<String, dynamic>> triggers) async {
    await _dio.post('/ScheduledTasks/$taskId/Triggers', data: triggers);
  }
}
