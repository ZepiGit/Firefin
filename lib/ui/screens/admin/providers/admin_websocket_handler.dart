import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:server_core/server_core.dart';

import '../../../../di/providers.dart';
import 'admin_status_providers.dart';
import 'admin_user_providers.dart';

class AdminWebSocketHandler extends ConsumerStatefulWidget {
  final Widget child;

  const AdminWebSocketHandler({super.key, required this.child});

  @override
  ConsumerState<AdminWebSocketHandler> createState() =>
      _AdminWebSocketHandlerState();
}

class _AdminWebSocketHandlerState extends ConsumerState<AdminWebSocketHandler> {
  StreamSubscription<ServerWebSocketMessage>? _subscription;

  @override
  void initState() {
    super.initState();
    _subscription = ref.read(socketHandlerProvider).events.listen(_onEvent);
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }

  void _onEvent(ServerWebSocketMessage event) {
    switch (event) {
      case LibraryChangedMessage():
        ref.invalidate(adminLibrariesProvider);
      case SessionEndedMessage():
        ref.invalidate(adminNotificationSummaryProvider);
      case ScheduledTaskEndedMessage():
        ref.invalidate(adminTasksProvider);
        ref.invalidate(adminNotificationSummaryProvider);
      case ServerEventMessage(:final type):
        switch (type) {
          case 'SessionsStart':
          case 'SessionsStop':
            ref.invalidate(adminNotificationSummaryProvider);
          case 'ScheduledTasksInfoStart':
          case 'ScheduledTasksInfoStop':
            ref.invalidate(adminTasksProvider);
            ref.invalidate(adminNotificationSummaryProvider);
          case 'PackageInstallationCompleted':
          case 'PackageInstallationFailed':
          case 'PluginInstalled':
          case 'PluginUninstalled':
            ref.invalidate(adminInstalledPluginsProvider);
            ref.invalidate(adminAvailablePackagesProvider);
            ref.invalidate(adminRepositoriesProvider);
            ref.invalidate(adminNotificationSummaryProvider);
          case 'UserCreated':
          case 'UserDeleted':
          case 'UserUpdated':
            ref.invalidate(adminUsersListProvider);
          case 'ActivityLogEntry':
            ref.invalidate(adminNotificationSummaryProvider);
          default:
            break;
        }
      default:
        break;
    }
  }

  @override
  Widget build(BuildContext context) => widget.child;
}