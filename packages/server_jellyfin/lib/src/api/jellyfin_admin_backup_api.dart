import 'package:dio/dio.dart';
import 'package:server_core/server_core.dart';

class JellyfinAdminBackupApi implements AdminBackupApi {
  final Dio _dio;

  JellyfinAdminBackupApi(this._dio);

  @override
  Future<List<Map<String, dynamic>>> getBackups() async {
    final response = await _getWithFallback([
      '/Backup',
      '/System/Backups',
      '/Backups',
      '/Environment/Backups',
    ]);

    final data = response.data;
    if (data is List) {
      return data.whereType<Map>().map((e) => e.cast<String, dynamic>()).toList();
    }
    if (data is Map<String, dynamic>) {
      final items = data['Items'] ?? data['items'] ?? data['Backups'] ?? data['backups'];
      if (items is List) {
        return items.whereType<Map>().map((e) => e.cast<String, dynamic>()).toList();
      }
    }
    return const [];
  }

  @override
  Future<Map<String, dynamic>> createBackup() async {
    final response = await _postWithFallback([
      '/Backup/Create',
      '/System/Backups',
      '/System/Backups/Create',
      '/Backups',
      '/Environment/Backups/Create',
    ]);

    final data = response.data;
    if (data is Map<String, dynamic>) {
      return data;
    }
    return const {};
  }

  @override
  Future<void> restoreBackup(String backupPath) async {
    await _postWithFallback(
      [
        '/Backup/Restore',
        '/System/Backups/Restore',
        '/Backups/Restore',
        '/Environment/Backups/Restore',
      ],
      data: {'ArchiveFileName': backupPath},
    );
  }

  @override
  Future<Map<String, dynamic>> getBackupManifest(String backupPath) async {
    final response = await _getWithFallback(
      [
        '/Backup/Manifest',
        '/System/Backups/Manifest',
        '/Backups/Manifest',
        '/Environment/Backups/Manifest',
      ],
      queryParameters: {'path': backupPath},
    );

    final data = response.data;
    if (data is Map<String, dynamic>) {
      return data;
    }
    if (data is List) {
      return {'Items': data};
    }
    return const {};
  }

  Future<Response<dynamic>> _getWithFallback(
    List<String> paths, {
    Map<String, dynamic>? queryParameters,
  }) async {
    DioException? lastDioError;
    for (final path in paths) {
      try {
        return await _dio.get(path, queryParameters: queryParameters);
      } on DioException catch (e) {
        final status = e.response?.statusCode;
        if (status == 404 || status == 405 || status == 501) {
          lastDioError = e;
          continue;
        }
        rethrow;
      }
    }
    throw lastDioError ?? StateError('No backup endpoint responded');
  }

  Future<Response<dynamic>> _postWithFallback(
    List<String> paths, {
    Map<String, dynamic>? queryParameters,
    Object? data,
  }) async {
    DioException? lastDioError;
    for (final path in paths) {
      try {
        return await _dio.post(
          path,
          queryParameters: queryParameters,
          data: data,
        );
      } on DioException catch (e) {
        final status = e.response?.statusCode;
        if (status == 404 || status == 405 || status == 501) {
          lastDioError = e;
          continue;
        }
        rethrow;
      }
    }
    throw lastDioError ?? StateError('No backup endpoint responded');
  }
}
