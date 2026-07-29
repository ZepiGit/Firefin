import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:get_it/get_it.dart';
import 'package:server_core/server_core.dart';
import 'package:dio/dio.dart';
import 'dart:convert';
import 'dart:io';

import '../admin_plugin_version_utils.dart';
import '../providers/admin_user_providers.dart';
import 'plugin_web_settings_screen.dart';

final _packageInfoProvider =
    FutureProvider.family<PackageInfo?, String>((ref, pluginId) async {
  final client = GetIt.instance<MediaServerClient>();
  try {
    final packages = await client.adminPluginsApi.getAvailablePackages();
    for (final pkg in packages) {
      if (pkg.id == pluginId) return pkg;
    }
    return null;
  } catch (_) {
    return null;
  }
});

class AdminPluginDetailScreen extends ConsumerStatefulWidget {
  final String pluginId;
  const AdminPluginDetailScreen({super.key, required this.pluginId});

  @override
  ConsumerState<AdminPluginDetailScreen> createState() =>
      _AdminPluginDetailScreenState();
}

class _AdminPluginDetailScreenState
    extends ConsumerState<AdminPluginDetailScreen> {
  bool _toggling = false;

  AdminPluginsApi get _api =>
      GetIt.instance<MediaServerClient>().adminPluginsApi;

  PluginInfo? _findPlugin(List<PluginInfo> plugins) {
    for (final p in plugins) {
      if (p.id == widget.pluginId) return p;
    }
    return null;
  }

  Future<bool> _confirmExperimentalHtmlSettings() async {
    final accepted = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Experimental Integration'),
        content: const Text(
          'Plugin settings integration is still experimental. Some fields or layouts may not render correctly yet.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Continue'),
          ),
        ],
      ),
    );

    return accepted == true;
  }

  Future<void> _togglePlugin(PluginInfo plugin) async {
    setState(() => _toggling = true);
    try {
      if (plugin.status == PluginStatus.disabled) {
        await _api.enablePlugin(plugin.id, plugin.version);
      } else {
        await _api.disablePlugin(plugin.id, plugin.version);
      }
      ref.invalidate(adminInstalledPluginsProvider);
    } catch (e) {
      final message = switch (e) {
        DioException(response: final response) when response?.statusCode == 404 =>
          'Failed to toggle plugin. The server could not find this plugin version. Try refreshing plugins, then retry.',
        DioException() => 'Failed to toggle plugin. Please check server logs for details.',
        _ => 'Failed to toggle plugin: $e',
      };

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
      }
    } finally {
      if (mounted) setState(() => _toggling = false);
    }
  }

  Future<void> _uninstallPlugin(PluginInfo plugin) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Uninstall Plugin'),
        content: Text('Are you sure you want to uninstall "${plugin.name}"?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
            child: const Text('Uninstall'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    try {
      await _api.uninstallPlugin(plugin.id, plugin.version);
      ref.invalidate(adminInstalledPluginsProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text(
                '"${plugin.name}" will be removed after server restart')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to uninstall: $e')));
      }
    }
  }

  Future<void> _installPluginUpdate(
    PluginInfo plugin,
    PackageInfo package,
    VersionInfo version,
  ) async {
    try {
      await _api.installPackage(
        package.name,
        assemblyGuid: package.id,
        version: version.version,
        repositoryUrl:
            version.repositoryUrl.isNotEmpty ? version.repositoryUrl : null,
      );

      ref.invalidate(adminInstalledPluginsProvider);
      ref.invalidate(adminAvailablePackagesProvider);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Updating "${plugin.name}" to v${version.version}...',
            ),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to install update: $e')),
        );
      }
    }
  }

  String _pluginImageUrl(PluginInfo plugin) {
    final client = GetIt.instance<MediaServerClient>();
    return '${client.baseUrl}/Plugins/${plugin.id}/${plugin.version}/Image';
  }

  Uri _pluginHtmlSettingsUri(String configPageName) {
    final client = GetIt.instance<MediaServerClient>();
    return Uri.parse(client.baseUrl).resolve(
      '/web/ConfigurationPage?name=${Uri.encodeQueryComponent(configPageName)}',
    );
  }

  Future<String?> _resolveConfigurationPageName(PluginInfo plugin) async {
    final client = GetIt.instance<MediaServerClient>();
    final token = client.accessToken;
    if (token == null || token.isEmpty) {
      return null;
    }

    final uri = Uri.parse(client.baseUrl).resolve('/web/ConfigurationPages');
    final httpClient = HttpClient();
    try {
      final request = await httpClient.getUrl(uri);
      request.headers.set('X-Emby-Token', token);
      request.headers.set('Accept', 'application/json');
      final response = await request.close();
      if (response.statusCode < 200 || response.statusCode >= 300) {
        return null;
      }

      final jsonText = await utf8.decoder.bind(response).join();
      final decoded = jsonDecode(jsonText);
      if (decoded is! List) {
        return null;
      }

      Map<String, dynamic>? firstMatch;
      for (final entry in decoded) {
        if (entry is! Map) {
          continue;
        }

        final mapped = Map<String, dynamic>.from(entry);
        final pagePluginId = (mapped['PluginId'] ?? '').toString();
        if (pagePluginId.toLowerCase() != plugin.id.toLowerCase()) {
          continue;
        }

        firstMatch ??= mapped;
        final inMainMenu = mapped['EnableInMainMenu'] == true;
        if (inMainMenu) {
          firstMatch = mapped;
          break;
        }
      }

      final name = (firstMatch?['Name'] ?? '').toString().trim();
      return name.isEmpty ? null : name;
    } catch (_) {
      return null;
    } finally {
      httpClient.close(force: true);
    }
  }

  Future<void> _openHtmlSettings(PluginInfo plugin) async {
    final proceed = await _confirmExperimentalHtmlSettings();
    if (!proceed || !mounted) {
      return;
    }

    final client = GetIt.instance<MediaServerClient>();
    final token = client.accessToken;
    if (token == null || token.isEmpty) {
      if (!mounted) {
        return;
      }

      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Unable to open settings: missing auth token.')),
      );
      return;
    }

    final configPageName = await _resolveConfigurationPageName(plugin) ?? plugin.name;
    if (!mounted) {
      return;
    }

    final uri = _pluginHtmlSettingsUri(configPageName);
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (context) => PluginWebSettingsScreen(
          configurationPageUri: uri,
          serverBaseUrl: client.baseUrl,
          accessToken: token,
          userId: client.userId,
          title: '${plugin.name} Settings',
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final pluginsAsync = ref.watch(adminInstalledPluginsProvider);
    final packageInfoAsync = ref.watch(_packageInfoProvider(widget.pluginId));

    return pluginsAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Failed to load plugin: $error'),
            const SizedBox(height: 8),
            ElevatedButton(
              onPressed: () => ref.invalidate(adminInstalledPluginsProvider),
              child: const Text('Retry'),
            ),
          ],
        ),
      ),
      data: (plugins) {
        final plugin = _findPlugin(plugins);
        if (plugin == null) {
          return const Center(child: Text('Plugin not found'));
        }
        final packageInfo = packageInfoAsync.valueOrNull;
        return _buildContent(context, plugin, packageInfo);
      },
    );
  }

  Widget _buildContent(
      BuildContext context, PluginInfo plugin, PackageInfo? packageInfo) {
    final theme = Theme.of(context);
    final isWide = MediaQuery.sizeOf(context).width >= 800;
    final latestUpdateVersion = packageInfo == null || plugin.version.isEmpty
      ? null
      : latestVersionInfoAfter(plugin.version, packageInfo.versions);

    final statusBanner = _buildStatusBanner(context, plugin);

    if (isWide) {
      return ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (statusBanner != null) ...[statusBanner, const SizedBox(height: 12)],
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                flex: 2,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(plugin.name, style: theme.textTheme.headlineMedium),
                    if (packageInfo?.description.isNotEmpty == true ||
                        plugin.description.isNotEmpty) ...[
                      const SizedBox(height: 8),
                      Text(
                        packageInfo?.description ?? plugin.description,
                        style: theme.textTheme.bodyLarge?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant),
                      ),
                    ],
                    if (packageInfo != null &&
                        packageInfo.versions.isNotEmpty) ...[
                      const SizedBox(height: 16),
                      _RevisionHistory(
                        versions: packageInfo.versions,
                        installedVersion: plugin.version,
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(width: 24),
              SizedBox(
                width: 300,
                child: Column(
                  children: [
                    _PluginImage(
                      imageUrl: plugin.hasImage
                          ? _pluginImageUrl(plugin)
                          : packageInfo?.imageUrl,
                    ),
                    const SizedBox(height: 16),
                    if (plugin.canUninstall) ...[
                      _ActionsSection(
                        plugin: plugin,
                        toggling: _toggling,
                        latestUpdateVersion: latestUpdateVersion?.version,
                        onToggle: () => _togglePlugin(plugin),
                        onInstallUpdate: latestUpdateVersion == null ||
                                packageInfo == null
                            ? null
                            : () => _installPluginUpdate(
                                  plugin,
                                  packageInfo,
                                  latestUpdateVersion,
                                ),
                        onUninstall: () => _uninstallPlugin(plugin),
                        onOpenHtmlSettings: () => _openHtmlSettings(plugin),
                      ),
                      const SizedBox(height: 16),
                    ],
                    _DetailsTable(
                      plugin: plugin,
                      packageInfo: packageInfo,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      );
    }

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (statusBanner != null) ...[statusBanner, const SizedBox(height: 12)],
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _PluginImage(
              imageUrl: plugin.hasImage
                  ? _pluginImageUrl(plugin)
                  : packageInfo?.imageUrl,
              size: 64,
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(plugin.name, style: theme.textTheme.headlineSmall),
                  const SizedBox(height: 4),
                  Text('Version ${plugin.version}',
                      style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant)),
                ],
              ),
            ),
          ],
        ),

        if (packageInfo?.description.isNotEmpty == true ||
            plugin.description.isNotEmpty) ...[
          const SizedBox(height: 12),
          Text(
            packageInfo?.description ?? plugin.description,
            style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant),
          ),
        ],

        if (plugin.canUninstall) ...[
          const SizedBox(height: 16),
          _ActionsSection(
            plugin: plugin,
            toggling: _toggling,
            latestUpdateVersion: latestUpdateVersion?.version,
            onToggle: () => _togglePlugin(plugin),
            onInstallUpdate: latestUpdateVersion == null || packageInfo == null
                ? null
                : () => _installPluginUpdate(
                      plugin,
                      packageInfo,
                      latestUpdateVersion,
                    ),
            onUninstall: () => _uninstallPlugin(plugin),
            onOpenHtmlSettings: () => _openHtmlSettings(plugin),
          ),
        ],

        const SizedBox(height: 16),
        _DetailsTable(plugin: plugin, packageInfo: packageInfo),

        if (packageInfo != null && packageInfo.versions.isNotEmpty) ...[
          const SizedBox(height: 16),
          _RevisionHistory(
            versions: packageInfo.versions,
            installedVersion: plugin.version,
          ),
        ],
      ],
    );
  }

  Widget? _buildStatusBanner(BuildContext context, PluginInfo plugin) {
    final theme = Theme.of(context);
    String? message;
    Color? color;

    switch (plugin.status) {
      case PluginStatus.restart:
        message = 'A server restart is required for changes to take effect.';
        color = theme.colorScheme.tertiary;
      case PluginStatus.deleted:
        message = 'This plugin will be removed after server restart.';
        color = theme.colorScheme.error;
      case PluginStatus.malfunctioned:
        message = 'This plugin has malfunctioned and may not work correctly.';
        color = theme.colorScheme.error;
      case PluginStatus.notSupported:
        message = 'This plugin is not supported by the current server version.';
        color = theme.colorScheme.error;
      case PluginStatus.superseded:
        message = 'This plugin has been superseded by a newer version.';
        color = theme.colorScheme.tertiary;
      default:
        return null;
    }

    return Card(
      color: color.withValues(alpha: 0.12),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            Icon(Icons.info_outline, size: 20, color: color),
            const SizedBox(width: 12),
            Expanded(
              child: Text(message,
                  style: theme.textTheme.bodyMedium?.copyWith(color: color)),
            ),
          ],
        ),
      ),
    );
  }
}

class _PluginImage extends StatelessWidget {
  final String? imageUrl;
  final double size;

  const _PluginImage({
    this.imageUrl,
    this.size = 120,
  });

  @override
  Widget build(BuildContext context) {
    if (imageUrl == null) return _fallback(context);
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: Image.network(
        imageUrl!,
        width: size,
        height: size,
        fit: BoxFit.cover,
        errorBuilder: (_, __, ___) => _fallback(context),
      ),
    );
  }

  Widget _fallback(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Icon(Icons.extension, size: size * 0.45,
          color: theme.colorScheme.onSurfaceVariant),
    );
  }
}

class _ActionsSection extends StatelessWidget {
  final PluginInfo plugin;
  final bool toggling;
  final String? latestUpdateVersion;
  final VoidCallback onToggle;
  final VoidCallback? onInstallUpdate;
  final VoidCallback onUninstall;
  final VoidCallback onOpenHtmlSettings;

  const _ActionsSection({
    required this.plugin,
    required this.toggling,
    this.latestUpdateVersion,
    required this.onToggle,
    this.onInstallUpdate,
    required this.onUninstall,
    required this.onOpenHtmlSettings,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isRestartPending = plugin.status == PluginStatus.restart;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Enable Plugin'),
              value: plugin.status != PluginStatus.disabled,
              onChanged: (isRestartPending || toggling)
                  ? null
                  : (_) => onToggle(),
            ),
            const Divider(),
            if (onInstallUpdate != null)
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(
                  Icons.download,
                  color: theme.colorScheme.primary,
                ),
                title: Text(
                  latestUpdateVersion != null
                      ? 'Install update (v$latestUpdateVersion)'
                      : 'Install update',
                ),
                onTap: onInstallUpdate,
              ),
            if (onInstallUpdate != null) const Divider(),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(Icons.web, color: theme.colorScheme.primary),
              title: const Text('Settings'),
              subtitle: const Text('Plugin settings page'),
              onTap: onOpenHtmlSettings,
            ),
            const Divider(),
            ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(Icons.delete_outline, color: theme.colorScheme.error),
                title: Text('Uninstall',
                    style: TextStyle(color: theme.colorScheme.error)),
                onTap: onUninstall,
              ),
          ],
        ),
      ),
    );
  }
}

class _DetailsTable extends StatelessWidget {
  final PluginInfo plugin;
  final PackageInfo? packageInfo;

  const _DetailsTable({
    required this.plugin,
    this.packageInfo,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final developer = packageInfo?.owner;
    final repoName = packageInfo?.versions.isNotEmpty == true
        ? packageInfo!.versions.first.repositoryName
        : null;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Details', style: theme.textTheme.titleMedium),
            const SizedBox(height: 12),
            _row(context, 'Status', plugin.status.label),
            _row(context, 'Version', plugin.version),
            _row(context, 'Developer', developer ?? 'Unknown'),
            _row(
              context,
              'Repository',
              plugin.canUninstall
                  ? (repoName ?? 'Unknown')
                  : 'Bundled',
            ),
          ],
        ),
      ),
    );
  }

  Widget _row(BuildContext context, String label, String value) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 100,
            child: Text(label,
                style: theme.textTheme.bodyMedium
                    ?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          ),
          Expanded(child: Text(value, style: theme.textTheme.bodyMedium)),
        ],
      ),
    );
  }
}

class _RevisionHistory extends StatelessWidget {
  final List<VersionInfo> versions;
  final String installedVersion;

  const _RevisionHistory({
    required this.versions,
    required this.installedVersion,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Revision History', style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            ...versions.take(10).map((v) {
              final isInstalled = v.version == installedVersion;
              return ExpansionTile(
                tilePadding: EdgeInsets.zero,
                title: Row(
                  children: [
                    Text(v.version,
                        style: theme.textTheme.bodyMedium?.copyWith(
                            fontWeight: isInstalled
                                ? FontWeight.bold
                                : FontWeight.normal)),
                    if (isInstalled) ...[
                      const SizedBox(width: 8),
                      Chip(
                        label: const Text('Installed'),
                        visualDensity: VisualDensity.compact,
                        labelStyle: theme.textTheme.labelSmall,
                        padding: EdgeInsets.zero,
                      ),
                    ],
                  ],
                ),
                subtitle: v.timestamp != null
                    ? Text(v.timestamp!,
                        style: theme.textTheme.bodySmall)
                    : null,
                children: [
                  if (v.changelog != null && v.changelog!.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(
                          left: 16, right: 16, bottom: 12),
                      child: Align(
                        alignment: Alignment.centerLeft,
                        child: Text(v.changelog!,
                            style: theme.textTheme.bodySmall),
                      ),
                    )
                  else
                    const Padding(
                      padding: EdgeInsets.only(left: 16, bottom: 12),
                      child: Text('No changelog available.'),
                    ),
                ],
              );
            }),
          ],
        ),
      ),
    );
  }
}
