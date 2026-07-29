import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:get_it/get_it.dart';
import 'package:server_core/server_core.dart';

class AdminLiveTvScreen extends StatefulWidget {
  const AdminLiveTvScreen({super.key});

  @override
  State<AdminLiveTvScreen> createState() => _AdminLiveTvScreenState();
}

class _AdminLiveTvScreenState extends State<AdminLiveTvScreen> {
  bool _loading = true;
  bool _discovering = false;
  bool _savingConfig = false;
  String? _error;

  List<Map<String, dynamic>> _tuners = const [];
  List<Map<String, dynamic>> _providers = const [];
  List<Map<String, dynamic>> _discoveredTuners = const [];
  Map<String, dynamic> _config = const {};

  AdminLiveTvApi get _api => GetIt.instance<MediaServerClient>().adminLiveTvApi;

  @override
  void initState() {
    super.initState();
    _loadAll();
  }

  String _friendlyError(Object error) {
    if (error is DioException) {
      final status = error.response?.statusCode;
      if (status == 404 || status == 405 || status == 501) {
        return 'Live TV administration is not available on this server build.';
      }
    }
    return error.toString();
  }

  Future<void> _loadAll() async {
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final config = await _api.getLiveTvConfiguration();
      final tuners = (config['TunerHosts'] as List<dynamic>? ?? const [])
          .whereType<Map>()
          .map((item) => item.cast<String, dynamic>())
          .toList();
      final providers = (config['ListingProviders'] as List<dynamic>? ?? const [])
          .whereType<Map>()
          .map((item) => item.cast<String, dynamic>())
          .toList();
      if (!mounted) return;
      setState(() {
        _tuners = tuners;
        _providers = providers;
        _config = config;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = _friendlyError(e);
        _loading = false;
      });
    }
  }

  String _display(Map<String, dynamic> item, List<String> keys, {String fallback = 'Unknown'}) {
    for (final key in keys) {
      final value = item[key];
      if (value == null) continue;
      final text = value.toString().trim();
      if (text.isNotEmpty) return text;
    }
    return fallback;
  }

  String _idOf(Map<String, dynamic> item) {
    return _display(item, const ['Id', 'ID', 'Guid', 'Name'], fallback: '');
  }

  int _intValue(dynamic value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }

  Future<void> _discoverTuners() async {
    if (_discovering) return;
    setState(() => _discovering = true);
    try {
      final found = await _api.discoverTuners();
      if (!mounted) return;
      setState(() {
        _discoveredTuners = found;
        _discovering = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _discovering = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Tuner discovery failed: $e')),
      );
    }
  }

  Future<void> _showAddTunerDialog({Map<String, dynamic>? seed}) async {
    final typeController = TextEditingController(
      text: _display(seed ?? const {}, const ['Type', 'TunerType'], fallback: 'M3U'),
    );
    final urlController = TextEditingController(
      text: _display(seed ?? const {}, const ['Url', 'Path', 'ImportUrl'], fallback: ''),
    );
    final nameController = TextEditingController(
      text: _display(seed ?? const {}, const ['Name', 'FriendlyName'], fallback: ''),
    );

    final payload = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Add Tuner'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: typeController,
                decoration: const InputDecoration(
                  labelText: 'Tuner Type',
                  hintText: 'HDHomeRun, M3U, Other',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: urlController,
                decoration: const InputDecoration(
                  labelText: 'URL / Path',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: nameController,
                decoration: const InputDecoration(
                  labelText: 'Name (optional)',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              final type = typeController.text.trim();
              final url = urlController.text.trim();
              final name = nameController.text.trim();
              Navigator.pop(ctx, {
                'Type': type,
                'Url': url,
                if (name.isNotEmpty) 'Name': name,
              });
            },
            child: const Text('Add'),
          ),
        ],
      ),
    );

    typeController.dispose();
    urlController.dispose();
    nameController.dispose();

    if (payload == null || !mounted) return;
    try {
      await _api.addTunerHost(payload);
      await _loadAll();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Tuner added')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to add tuner: $e')),
      );
    }
  }

  Future<void> _showAddProviderDialog() async {
    final typeController = TextEditingController(text: 'XMLTV');
    final urlController = TextEditingController();
    final usernameController = TextEditingController();
    final passwordController = TextEditingController();
    final refreshHoursController = TextEditingController(text: '24');

    final payload = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Add Guide Provider'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: typeController,
                decoration: const InputDecoration(
                  labelText: 'Provider Type',
                  hintText: 'SchedulesDirect or XMLTV',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: urlController,
                decoration: const InputDecoration(
                  labelText: 'URL / Path',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: usernameController,
                decoration: const InputDecoration(
                  labelText: 'Username (optional)',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: passwordController,
                obscureText: true,
                decoration: const InputDecoration(
                  labelText: 'Password (optional)',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: refreshHoursController,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: 'Refresh interval (hours)',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              final refresh = int.tryParse(refreshHoursController.text.trim());
              final payload = <String, dynamic>{
                'Type': typeController.text.trim(),
                if (urlController.text.trim().isNotEmpty) 'Url': urlController.text.trim(),
                if (usernameController.text.trim().isNotEmpty) 'Username': usernameController.text.trim(),
                if (passwordController.text.trim().isNotEmpty) 'Password': passwordController.text.trim(),
                if (refresh != null) 'RefreshIntervalHours': refresh,
              };
              Navigator.pop(ctx, payload);
            },
            child: const Text('Add'),
          ),
        ],
      ),
    );

    typeController.dispose();
    urlController.dispose();
    usernameController.dispose();
    passwordController.dispose();
    refreshHoursController.dispose();

    if (payload == null || !mounted) return;

    try {
      await _api.addListingProvider(payload);
      await _loadAll();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Provider added')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to add provider: $e')),
      );
    }
  }

  Future<void> _removeTuner(Map<String, dynamic> tuner) async {
    final id = _idOf(tuner);
    if (id.isEmpty) return;
    try {
      await _api.removeTunerHost(id);
      await _loadAll();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to remove tuner: $e')),
      );
    }
  }

  Future<void> _resetTuner(Map<String, dynamic> tuner) async {
    final id = _idOf(tuner);
    if (id.isEmpty) return;
    try {
      await _api.resetTuner(id);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Tuner reset requested')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to reset tuner: $e')),
      );
    }
  }

  Future<void> _removeProvider(Map<String, dynamic> provider) async {
    final id = _idOf(provider);
    if (id.isEmpty) return;
    try {
      await _api.removeListingProvider(id);
      await _loadAll();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to remove provider: $e')),
      );
    }
  }

  Future<void> _saveRecordingSettings() async {
    final prePadding = _intValue(
      _config['PrePaddingSeconds'] ?? _config['PrePaddingMinutes'],
    );
    final postPadding = _intValue(
      _config['PostPaddingSeconds'] ?? _config['PostPaddingMinutes'],
    );
    final recordingPath = (_config['RecordingPath'] ?? '').toString();
    final seriesPath = (_config['SeriesRecordingPath'] ?? '').toString();

    final preController = TextEditingController(
      text: (prePadding ~/ 60).toString(),
    );
    final postController = TextEditingController(
      text: (postPadding ~/ 60).toString(),
    );
    final recPathController = TextEditingController(text: recordingPath);
    final seriesPathController = TextEditingController(text: seriesPath);

    final updated = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Recording Settings'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: preController,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: 'Pre-padding (minutes)',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: postController,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: 'Post-padding (minutes)',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: recPathController,
                decoration: const InputDecoration(
                  labelText: 'Recording path',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: seriesPathController,
                decoration: const InputDecoration(
                  labelText: 'Series recording path',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              final preMinutes = int.tryParse(preController.text.trim()) ?? 0;
              final postMinutes = int.tryParse(postController.text.trim()) ?? 0;
              Navigator.pop(ctx, {
                ..._config,
                'PrePaddingSeconds': preMinutes * 60,
                'PostPaddingSeconds': postMinutes * 60,
                'RecordingPath': recPathController.text.trim(),
                'SeriesRecordingPath': seriesPathController.text.trim(),
              });
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );

    preController.dispose();
    postController.dispose();
    recPathController.dispose();
    seriesPathController.dispose();

    if (updated == null || !mounted) return;

    setState(() => _savingConfig = true);
    try {
      await _api.updateLiveTvConfiguration(updated);
      if (!mounted) return;
      setState(() {
        _config = updated;
        _savingConfig = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Recording settings saved')),
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _savingConfig = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to save settings: $e')),
      );
    }
  }

  Future<void> _setChannelMappings() async {
    final controller = TextEditingController();
    final payload = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Set Channel Mappings'),
        content: TextField(
          controller: controller,
          decoration: const InputDecoration(
            labelText: 'Mapping JSON',
            hintText: '{"Mappings": [...] }',
            border: OutlineInputBorder(),
          ),
          minLines: 4,
          maxLines: 8,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              final text = controller.text.trim();
              if (text.isEmpty) {
                Navigator.pop(ctx, const <String, dynamic>{});
                return;
              }
              try {
                final decoded = jsonDecode(text);
                if (decoded is Map<String, dynamic>) {
                  Navigator.pop(ctx, decoded);
                } else {
                  Navigator.pop(ctx, {'Mappings': decoded});
                }
              } catch (_) {
                Navigator.pop(ctx, null);
              }
            },
            child: const Text('Apply'),
          ),
        ],
      ),
    );
    controller.dispose();

    if (payload == null || !mounted) return;

    try {
      await _api.setChannelMappings(payload);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Channel mappings updated')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Failed to update mappings: $e')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_error != null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Failed to load Live TV administration'),
            const SizedBox(height: 8),
            Text(_error!, textAlign: TextAlign.center),
            const SizedBox(height: 12),
            FilledButton.tonal(
              onPressed: _loadAll,
              child: const Text('Retry'),
            ),
          ],
        ),
      );
    }

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                'Live TV Administration',
                style: Theme.of(context).textTheme.headlineSmall,
              ),
            ),
            IconButton(
              tooltip: 'Refresh',
              onPressed: _loadAll,
              icon: const Icon(Icons.refresh),
            ),
          ],
        ),
        const SizedBox(height: 8),
        _sectionCard(
          context,
          title: 'Tuner Devices',
          action: FilledButton.tonalIcon(
            onPressed: () => _showAddTunerDialog(),
            icon: const Icon(Icons.add),
            label: const Text('Add Tuner'),
          ),
          child: _tuners.isEmpty
              ? const Text('No tuner hosts configured')
              : Column(
                  children: _tuners.map((tuner) {
                    final name = _display(tuner, const ['FriendlyName', 'Name']);
                    final type = _display(tuner, const ['Type', 'TunerType']);
                    final id = _idOf(tuner);
                    return ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(name),
                      subtitle: Text(type),
                      trailing: Wrap(
                        spacing: 4,
                        children: [
                          IconButton(
                            tooltip: 'Reset',
                            onPressed: id.isEmpty ? null : () => _resetTuner(tuner),
                            icon: const Icon(Icons.restart_alt),
                          ),
                          IconButton(
                            tooltip: 'Delete',
                            onPressed: id.isEmpty ? null : () => _removeTuner(tuner),
                            icon: const Icon(Icons.delete_outline),
                          ),
                        ],
                      ),
                    );
                  }).toList(),
                ),
        ),
        const SizedBox(height: 12),
        _sectionCard(
          context,
          title: 'Guide Providers',
          action: FilledButton.tonalIcon(
            onPressed: _showAddProviderDialog,
            icon: const Icon(Icons.add),
            label: const Text('Add Provider'),
          ),
          child: _providers.isEmpty
              ? const Text('No listing providers configured')
              : Column(
                  children: _providers.map((provider) {
                    final name = _display(provider, const ['Name', 'Type']);
                    final url = _display(provider, const ['Url', 'Path'], fallback: '');
                    final id = _idOf(provider);
                    return ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(name),
                      subtitle: url.isEmpty ? null : Text(url),
                      trailing: IconButton(
                        tooltip: 'Delete',
                        onPressed: id.isEmpty ? null : () => _removeProvider(provider),
                        icon: const Icon(Icons.delete_outline),
                      ),
                    );
                  }).toList(),
                ),
        ),
        const SizedBox(height: 12),
        _sectionCard(
          context,
          title: 'Recording Settings',
          action: FilledButton.tonalIcon(
            onPressed: _savingConfig ? null : _saveRecordingSettings,
            icon: const Icon(Icons.save),
            label: const Text('Edit'),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Recording path: ${_display(_config, const ['RecordingPath'], fallback: 'Not set')}'),
              const SizedBox(height: 4),
              Text('Series path: ${_display(_config, const ['SeriesRecordingPath'], fallback: 'Not set')}'),
              const SizedBox(height: 4),
              Text('Pre-padding: ${_intValue(_config['PrePaddingSeconds']) ~/ 60} min'),
              const SizedBox(height: 4),
              Text('Post-padding: ${_intValue(_config['PostPaddingSeconds']) ~/ 60} min'),
            ],
          ),
        ),
        const SizedBox(height: 12),
        _sectionCard(
          context,
          title: 'Tuner Discovery',
          actionBelowTitle: true,
          action: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              FilledButton.tonalIcon(
                onPressed: _discovering ? null : _discoverTuners,
                icon: const Icon(Icons.radar),
                label: const Text('Discover'),
              ),
              const SizedBox(width: 8),
              FilledButton.tonal(
                onPressed: _setChannelMappings,
                child: const Text('Channel Mappings'),
              ),
            ],
          ),
          child: _discovering
              ? const Padding(
                  padding: EdgeInsets.symmetric(vertical: 8),
                  child: LinearProgressIndicator(),
                )
              : _discoveredTuners.isEmpty
                  ? const Text('No discovered tuners yet')
                  : Column(
                      children: _discoveredTuners.map((item) {
                        final name = _display(item, const ['FriendlyName', 'Name']);
                        final type = _display(item, const ['Type', 'TunerType']);
                        return ListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text(name),
                          subtitle: Text(type),
                          trailing: FilledButton.tonal(
                            onPressed: () => _showAddTunerDialog(seed: item),
                            child: const Text('Add'),
                          ),
                        );
                      }).toList(),
                    ),
        ),
      ],
    );
  }

  Widget _sectionCard(
    BuildContext context, {
    required String title,
    required Widget child,
    Widget? action,
    bool actionBelowTitle = false,
  }) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (!actionBelowTitle)
              Row(
                children: [
                  Expanded(
                    child: Text(
                      title,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                  ),
                  if (action != null) action,
                ],
              )
            else ...[
              Text(
                title,
                style: Theme.of(context).textTheme.titleMedium,
              ),
              if (action != null) ...[
                const SizedBox(height: 8),
                action,
              ],
            ],
            const SizedBox(height: 8),
            child,
          ],
        ),
      ),
    );
  }
}
