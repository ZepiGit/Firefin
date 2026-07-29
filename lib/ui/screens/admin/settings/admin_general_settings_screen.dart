import 'package:flutter/material.dart';
import 'package:get_it/get_it.dart';
import 'package:server_core/server_core.dart';

import '../widgets/filesystem_browser.dart';

class AdminGeneralSettingsScreen extends StatefulWidget {
  const AdminGeneralSettingsScreen({super.key});

  @override
  State<AdminGeneralSettingsScreen> createState() =>
      _AdminGeneralSettingsScreenState();
}

class _AdminGeneralSettingsScreenState
    extends State<AdminGeneralSettingsScreen> {
  late final AdminSystemApi _api;
  Map<String, dynamic>? _config;
  bool _loading = true;
  bool _saving = false;
  String? _error;
  String? _browsingField;

  @override
  void initState() {
    super.initState();
    _api = GetIt.instance<MediaServerClient>().adminSystemApi;
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final config = await _api.getServerConfiguration();
      if (!mounted) return;
      setState(() {
        _config = config;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  Future<void> _save() async {
    if (_config == null) return;
    setState(() => _saving = true);
    try {
      await _api.updateServerConfiguration(_config!);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Settings saved')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to save: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null || _config == null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Failed to load settings',
                style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            Text(_error ?? 'Unknown error',
                style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: 16),
            FilledButton.tonal(onPressed: _load, child: const Text('Retry')),
          ],
        ),
      );
    }

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('General Settings',
            style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 24),
        _textField('ServerName', 'Server name'),
        const SizedBox(height: 16),
        _textField('PreferredMetadataLanguage', 'Preferred metadata language',
            hint: 'e.g. en, de, fr'),
        const SizedBox(height: 16),
        _textField('MetadataCountryCode', 'Preferred metadata country',
            hint: 'e.g. US, DE, FR'),
        const SizedBox(height: 16),
        _pathField('CachePath', 'Cache path'),
        const SizedBox(height: 16),
        _pathField('MetadataPath', 'Metadata path'),
        const SizedBox(height: 16),
        _intField('LibraryScanFanoutConcurrency', 'Library scan concurrency'),
        const SizedBox(height: 16),
        _intField(
            'ParallelImageEncodingLimit', 'Parallel image encoding limit'),
        const SizedBox(height: 16),
        _intField('SlowResponseThresholdMs', 'Slow response threshold (ms)'),
        const SizedBox(height: 24),
        Align(
          alignment: Alignment.centerLeft,
          child: FilledButton(
            onPressed: _saving ? null : _save,
            child: _saving
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Save'),
          ),
        ),
      ],
    );
  }

  Widget _textField(String key, String label, {String? hint}) {
    return TextFormField(
      initialValue: _config![key]?.toString() ?? '',
      decoration: InputDecoration(
        labelText: label,
        hintText: hint,
        border: const OutlineInputBorder(),
      ),
      onChanged: (v) => _config![key] = v,
    );
  }

  Widget _intField(String key, String label) {
    return TextFormField(
      initialValue: (_config![key] as num?)?.toString() ?? '0',
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
      ),
      keyboardType: TextInputType.number,
      onChanged: (v) => _config![key] = int.tryParse(v) ?? 0,
    );
  }

  Widget _pathField(String key, String label) {
    final isBrowsing = _browsingField == key;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: TextFormField(
                key: ValueKey(_config![key]),
                initialValue: _config![key]?.toString() ?? '',
                decoration: InputDecoration(
                  labelText: label,
                  border: const OutlineInputBorder(),
                ),
                onChanged: (v) => _config![key] = v,
              ),
            ),
            const SizedBox(width: 8),
            IconButton(
              icon: Icon(isBrowsing ? Icons.close : Icons.folder_open),
              tooltip: isBrowsing ? 'Close browser' : 'Browse',
              onPressed: () => setState(() {
                _browsingField = isBrowsing ? null : key;
              }),
            ),
          ],
        ),
        if (isBrowsing) ...[
          const SizedBox(height: 8),
          SizedBox(
            height: 300,
            child: FilesystemBrowser(
              initialPath: _config![key]?.toString(),
              onPathSelected: (path) {
                setState(() {
                  _config![key] = path;
                  _browsingField = null;
                });
              },
            ),
          ),
        ],
      ],
    );
  }
}
