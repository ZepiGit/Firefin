import 'package:dio/dio.dart';
import 'package:server_core/server_core.dart';

class JellyfinUserViewsApi implements UserViewsApi {
  final Dio _dio;

  JellyfinUserViewsApi(this._dio);

  @override
  Future<Map<String, dynamic>> getUserViews() async {
    final response = await _dio.get('/UserViews');
    return response.data as Map<String, dynamic>;
  }
}
