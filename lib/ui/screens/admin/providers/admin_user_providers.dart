import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:get_it/get_it.dart';
import 'package:server_core/server_core.dart';

final adminUsersListProvider = FutureProvider<List<ServerUser>>((ref) async {
  final client = GetIt.instance<MediaServerClient>();
  return client.adminUsersApi.getUsers();
});

final adminUserProvider =
    FutureProvider.family<ServerUser, String>((ref, userId) async {
  final client = GetIt.instance<MediaServerClient>();
  return client.adminUsersApi.getUserById(userId);
});

final adminLibrariesProvider =
    FutureProvider<List<VirtualFolderInfo>>((ref) async {
  final client = GetIt.instance<MediaServerClient>();
  return client.adminLibraryApi.getVirtualFolders();
});

final adminTasksProvider = FutureProvider<List<TaskInfo>>((ref) async {
  final client = GetIt.instance<MediaServerClient>();
  final data = await client.adminTasksApi.getTasks();
  return data.map((json) => TaskInfo.fromJson(json)).toList();
});

final adminTaskProvider =
    FutureProvider.family<TaskInfo, String>((ref, taskId) async {
  final client = GetIt.instance<MediaServerClient>();
  final data = await client.adminTasksApi.getTask(taskId);
  return TaskInfo.fromJson(data);
});

final adminInstalledPluginsProvider =
    FutureProvider<List<PluginInfo>>((ref) async {
  final client = GetIt.instance<MediaServerClient>();
  return client.adminPluginsApi.getInstalledPlugins();
});

final adminAvailablePackagesProvider =
    FutureProvider<List<PackageInfo>>((ref) async {
  final client = GetIt.instance<MediaServerClient>();
  return client.adminPluginsApi.getAvailablePackages();
});

final adminRepositoriesProvider =
    FutureProvider<List<RepositoryInfo>>((ref) async {
  final client = GetIt.instance<MediaServerClient>();
  return client.adminPluginsApi.getRepositories();
});

final adminDevicesProvider = FutureProvider<List<DeviceInfoDto>>((ref) async {
  final client = GetIt.instance<MediaServerClient>();
  return client.adminDevicesApi.getDevices();
});
