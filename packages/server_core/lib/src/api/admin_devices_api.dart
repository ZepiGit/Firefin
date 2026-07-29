import '../models/device_models.dart';

abstract class AdminDevicesApi {
  Future<List<DeviceInfoDto>> getDevices({String? userId});
  Future<DeviceInfoDto> getDeviceInfo(String id);
  Future<DeviceOptionsDto> getDeviceOptions(String id);
  Future<void> updateDeviceOptions(String id, DeviceOptionsDto options);
  Future<void> deleteDevice(String id);
}
