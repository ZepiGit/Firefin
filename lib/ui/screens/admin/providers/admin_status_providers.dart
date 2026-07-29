import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:get_it/get_it.dart';
import 'package:server_core/server_core.dart';

import '../admin_plugin_version_utils.dart';
import 'admin_user_providers.dart';

class AdminNotificationSummary {
  final int pluginUpdateCount;
  final int restartPendingPluginCount;
  final List<String> restartPendingPlugins;
  final int failedTaskCount;
  final int alertCount;
  final String? latestAlertText;

  const AdminNotificationSummary({
    this.pluginUpdateCount = 0,
    this.restartPendingPluginCount = 0,
    this.restartPendingPlugins = const [],
    this.failedTaskCount = 0,
    this.alertCount = 0,
    this.latestAlertText,
  });

  bool get hasPluginUpdates => pluginUpdateCount > 0;

  bool get hasRestartPendingPlugins => restartPendingPluginCount > 0;

  bool get hasFailedTasks => failedTaskCount > 0;

  bool get hasAlerts => alertCount > 0;

  int get count =>
      (hasPluginUpdates ? 1 : 0) +
      (hasRestartPendingPlugins ? 1 : 0) +
      (hasFailedTasks ? 1 : 0) +
      (hasAlerts ? 1 : 0);
}

final adminNotificationSummaryProvider =
    FutureProvider<AdminNotificationSummary>((ref) async {
  final client = GetIt.instance<MediaServerClient>();

  try {
    final results = await Future.wait<dynamic>([
      ref.watch(adminInstalledPluginsProvider.future),
      ref.watch(adminAvailablePackagesProvider.future),
      ref.watch(adminTasksProvider.future),
      client.adminSystemApi.getActivityLog(limit: 20),
    ]);

    final installed = results[0] as List<PluginInfo>;
    final available = results[1] as List<PackageInfo>;
    final tasks = results[2] as List<TaskInfo>;
    final activity = results[3] as ActivityLogResult;

    final availableById = {
      for (final package in available)
        if (package.id.isNotEmpty) package.id: package,
    };

    final pluginUpdateCount = installed.where((plugin) {
      final package = availableById[plugin.id];
      if (package == null || plugin.version.isEmpty) {
        return false;
      }
      return package.versions.any((version) {
        if (version.version.isEmpty) {
          return false;
        }
        return compareVersionStrings(version.version, plugin.version) > 0;
      });
    }).length;

    final restartPendingPlugins = installed
        .where((plugin) =>
            plugin.status == PluginStatus.restart ||
            plugin.status == PluginStatus.deleted)
        .map((plugin) => plugin.name)
        .toList();

    final failedTaskCount = tasks.where(
      (task) => task.lastExecutionResult?.status == 'Failed',
    ).length;

    final alertEntries = activity.items
        .where(
          (entry) =>
              entry.severity == 'Error' ||
              entry.severity == 'Warning' ||
              entry.severity == 'Warn',
        )
        .toList();

    final alertCount = alertEntries.length;
    final latestAlertText = alertEntries.isNotEmpty
        ? alertEntries.first.name
        : null;

    return AdminNotificationSummary(
      pluginUpdateCount: pluginUpdateCount,
      restartPendingPluginCount: restartPendingPlugins.length,
      restartPendingPlugins: restartPendingPlugins,
      failedTaskCount: failedTaskCount,
      alertCount: alertCount,
      latestAlertText: latestAlertText,
    );
  } catch (_) {
    return const AdminNotificationSummary();
  }
});