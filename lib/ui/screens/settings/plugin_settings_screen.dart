import 'package:flutter/material.dart';
import 'package:get_it/get_it.dart';
import 'package:server_core/server_core.dart';

import '../../../data/services/plugin_sync_service.dart';
import '../../../preference/user_preferences.dart';
import '../../widgets/settings/preference_tiles.dart';

class PluginSettingsScreen extends StatefulWidget {
  const PluginSettingsScreen({super.key});

  @override
  State<PluginSettingsScreen> createState() => _PluginSettingsScreenState();
}

class _PluginSettingsScreenState extends State<PluginSettingsScreen> {
  late final PluginSyncService _syncService;

  @override
  void initState() {
    super.initState();
    _syncService = GetIt.instance<PluginSyncService>();
    _syncService.addListener(_onSyncStateChanged);
    _refreshPluginStatus();
  }

  @override
  void dispose() {
    _syncService.removeListener(_onSyncStateChanged);
    super.dispose();
  }

  void _onSyncStateChanged() {
    if (!mounted) return;
    setState(() {});
  }

  Future<void> _refreshPluginStatus() async {
    if (!GetIt.instance.isRegistered<MediaServerClient>()) return;
    final client = GetIt.instance<MediaServerClient>();
    await _syncService.refreshAvailability(client);
  }

  void _pushSync() {
    if (_syncService.pluginAvailable) {
      final client = GetIt.instance<MediaServerClient>();
      _syncService.pushSettings(client);
    }
  }

  @override
  Widget build(BuildContext context) {
    final pluginAvailable = _syncService.pluginAvailable;
    final pluginVersion = _syncService.pluginVersion;
    final pluginStateText = pluginAvailable
        ? 'Server plugin detected. Sync is enabled automatically the first time the plugin is found.'
        : 'Server plugin is not currently detected. Local settings still use their saved values or built-in defaults.';
    final availableServices = <String>[
      if (_syncService.mdblistAvailable) 'MDBList',
      if (_syncService.tmdbAvailable) 'TMDB',
      if (_syncService.seerrEnabled) 'Seerr',
    ];

    return Scaffold(
      appBar: AppBar(title: const Text('Plugin')),
      body: ListView(
        children: [
          ListTile(
            leading: Icon(
              pluginAvailable ? Icons.extension : Icons.extension_off,
              color: pluginAvailable ? Colors.green : null,
            ),
            title: Text(pluginAvailable ? 'Plugin Detected' : 'Plugin Not Detected'),
            subtitle: Text(
              pluginVersion != null && pluginVersion.trim().isNotEmpty
                  ? '$pluginStateText\nVersion: $pluginVersion'
                  : pluginStateText,
            ),
          ),
          if (availableServices.isNotEmpty)
            ListTile(
              leading: const Icon(Icons.hub),
              title: const Text('Available Services'),
              subtitle: Text(availableServices.join(', ')),
            ),
          const Divider(),
          SwitchPreferenceTile(
            preference: UserPreferences.pluginSyncEnabled,
            title: 'Server Plugin Sync',
            subtitle: 'Sync settings with the server plugin',
            icon: Icons.sync,
            onChanged: _pushSync,
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.info_outline),
            title: const Text('What sync controls'),
            subtitle: const Text(
              'Sync only controls whether plugin-backed settings are pushed to and pulled from the server. Profile selection and profile sync actions are in Customization settings when plugin sync is enabled.',
            ),
          ),
        ],
      ),
    );
  }
}
