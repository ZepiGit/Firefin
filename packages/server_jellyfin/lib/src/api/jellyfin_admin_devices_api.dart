import 'package:dio/dio.dart';
import 'package:server_core/server_core.dart';

class JellyfinAdminDevicesApi implements AdminDevicesApi {
  final Dio _dio;

  JellyfinAdminDevicesApi(this._dio);

  @override
  Future<List<DeviceInfoDto>> getDevices({String? userId}) async {
    final response = await _dio.get(
      '/Devices',
      queryParameters: {
        if (userId != null) 'userId': userId,
      },
    );
    final items = (response.data as Map<String, dynamic>)['Items'] as List<dynamic>;
    return items
        .map((e) => DeviceInfoDto.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  @override
  Future<DeviceInfoDto> getDeviceInfo(String id) async {
    final response = await _dio.get(
      '/Devices/Info',
      queryParameters: {'id': id},
    );
    return DeviceInfoDto.fromJson(response.data as Map<String, dynamic>);
  }

  @override
  Future<DeviceOptionsDto> getDeviceOptions(String id) async {
    final response = await _dio.get(
      '/Devices/Options',
      queryParameters: {'id': id},
    );
    return DeviceOptionsDto.fromJson(response.data as Map<String, dynamic>);
  }

  @override
  Future<void> updateDeviceOptions(String id, DeviceOptionsDto options) async {
    await _dio.post(
      '/Devices/Options',
      queryParameters: {'id': id},
      data: options.toJson(),
    );
  }

  @override
  Future<void> deleteDevice(String id) async {
    await _dio.delete(
      '/Devices',
      queryParameters: {'id': id},
    );
  }
}
