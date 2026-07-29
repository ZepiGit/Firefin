import 'package:flutter/material.dart';
import 'package:get_it/get_it.dart';
import 'package:server_core/server_core.dart';

import '../widgets/filesystem_browser.dart';

class AdminNetworkingScreen extends StatefulWidget {
  const AdminNetworkingScreen({super.key});

  @override
  State<AdminNetworkingScreen> createState() => _AdminNetworkingScreenState();
}

class _AdminNetworkingScreenState extends State<AdminNetworkingScreen> {
  late final AdminSystemApi _api;
  Map<String, dynamic>? _config;
  bool _loading = true;
  bool _saving = false;
  String? _error;
  bool _showCertBrowser = false;
  final _listControllers = <String, TextEditingController>{};

  @override
  void initState() {
    super.initState();
    _api = GetIt.instance<MediaServerClient>().adminSystemApi;
    _load();
  }

  @override
  void dispose() {
    for (final c in _listControllers.values) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final config = await _api.getNamedConfiguration('network');
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
      await _api.updateNamedConfiguration('network', _config!);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
                'Networking settings saved. A server restart may be required.'),
            duration: Duration(seconds: 5),
          ),
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
    final bottomSafe = MediaQuery.of(context).padding.bottom;
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null || _config == null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Failed to load networking settings',
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
      padding: EdgeInsets.fromLTRB(16, 16, 16, bottomSafe + 40),
      children: [
        Text('Networking', style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 8),
        Card(
          color: Theme.of(context).colorScheme.tertiaryContainer,
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                Icon(Icons.warning_amber,
                    color: Theme.of(context).colorScheme.onTertiaryContainer),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Changes to networking settings may require a server restart.',
                    style: TextStyle(
                        color: Theme.of(context)
                            .colorScheme
                            .onTertiaryContainer),
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        _switchTile('EnableRemoteAccess', 'Enable remote access'),
        const Divider(),
        _sectionHeader('Ports'),
        _intField('HttpServerPortNumber', 'HTTP port'),
        const SizedBox(height: 12),
        _intField('HttpsPortNumber', 'HTTPS port'),
        const SizedBox(height: 12),
        _intField('PublicHttpsPort', 'Public HTTPS port'),
        const SizedBox(height: 12),
        _textField('BaseUrl', 'Base URL', hint: 'e.g. /jellyfin'),
        const Divider(height: 32),
        _sectionHeader('HTTPS'),
        _switchTile('EnableHttps', 'Enable HTTPS'),
        const SizedBox(height: 8),
        _certPathField(),
        const Divider(height: 32),
        _sectionHeader('Local Network'),
        _listEditor(
          'LocalNetworkAddresses',
          'Local network addresses',
          hint: 'e.g. 192.168.1.0/24',
        ),
        const SizedBox(height: 16),
        _listEditor(
          'KnownProxies',
          'Known proxies',
          hint: 'e.g. 10.0.0.1',
        ),
        const Divider(height: 32),
        _sectionHeader('Remote IP Filter'),
        _filterModeSelector(),
        const SizedBox(height: 12),
        _listEditor(
          'RemoteIPFilter',
          'Remote IP filter',
          hint: 'e.g. 192.168.1.0/24',
        ),
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

  Widget _sectionHeader(String text) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child:
          Text(text, style: Theme.of(context).textTheme.titleMedium),
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

  Widget _switchTile(String key, String title) {
    return SwitchListTile(
      title: Text(title),
      value: _config![key] as bool? ?? false,
      onChanged: (v) => setState(() => _config![key] = v),
    );
  }

  Widget _certPathField() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: TextFormField(
                key: ValueKey(_config!['CertificatePath']),
                initialValue: _config!['CertificatePath']?.toString() ?? '',
                decoration: const InputDecoration(
                  labelText: 'Certificate path',
                  border: OutlineInputBorder(),
                ),
                onChanged: (v) => _config!['CertificatePath'] = v,
              ),
            ),
            const SizedBox(width: 8),
            IconButton(
              icon:
                  Icon(_showCertBrowser ? Icons.close : Icons.folder_open),
              tooltip: _showCertBrowser ? 'Close browser' : 'Browse',
              onPressed: () =>
                  setState(() => _showCertBrowser = !_showCertBrowser),
            ),
          ],
        ),
        if (_showCertBrowser) ...[
          const SizedBox(height: 8),
          SizedBox(
            height: 300,
            child: FilesystemBrowser(
              initialPath: _config!['CertificatePath']?.toString(),
              onPathSelected: (path) {
                setState(() {
                  _config!['CertificatePath'] = path;
                  _showCertBrowser = false;
                });
              },
            ),
          ),
        ],
      ],
    );
  }

  Widget _filterModeSelector() {
    final isBlacklist =
        _config!['IsRemoteIPFilterBlacklist'] as bool? ?? false;
    return Row(
      children: [
        ChoiceChip(
          label: const Text('Whitelist'),
          selected: !isBlacklist,
          onSelected: (v) {
            if (v) {
              setState(
                  () => _config!['IsRemoteIPFilterBlacklist'] = false);
            }
          },
        ),
        const SizedBox(width: 8),
        ChoiceChip(
          label: const Text('Blacklist'),
          selected: isBlacklist,
          onSelected: (v) {
            if (v) {
              setState(
                  () => _config!['IsRemoteIPFilterBlacklist'] = true);
            }
          },
        ),
      ],
    );
  }

  TextEditingController _controllerFor(String key) {
    return _listControllers.putIfAbsent(key, () => TextEditingController());
  }

  void _addListItem(String key) {
    final controller = _controllerFor(key);
    final v = controller.text.trim();
    if (v.isEmpty) return;
    final items = List<String>.from(
        (_config![key] as List<dynamic>?) ?? <dynamic>[]);
    items.add(v);
    _config![key] = items;
    controller.clear();
    setState(() {});
  }

  Widget _listEditor(String key, String label, {String? hint}) {
    final items = List<String>.from(
        (_config![key] as List<dynamic>?) ?? <dynamic>[]);
    final controller = _controllerFor(key);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: 8),
        ...items.asMap().entries.map((entry) => Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Row(
                children: [
                  Expanded(
                    child: Text(entry.value,
                        style: const TextStyle(
                            fontFamily: 'monospace', fontSize: 13)),
                  ),
                  IconButton(
                    icon: const Icon(Icons.remove_circle_outline, size: 20),
                    visualDensity: VisualDensity.compact,
                    onPressed: () {
                      setState(() {
                        items.removeAt(entry.key);
                        _config![key] = items;
                      });
                    },
                  ),
                ],
              ),
            )),
        Row(
          children: [
            Expanded(
              child: TextField(
                controller: controller,
                decoration: InputDecoration(
                  hintText: hint ?? 'Add entry',
                  border: const OutlineInputBorder(),
                  isDense: true,
                ),
                onSubmitted: (_) => _addListItem(key),
              ),
            ),
            const SizedBox(width: 8),
            IconButton(
              icon: const Icon(Icons.add),
              onPressed: () => _addListItem(key),
            ),
          ],
        ),
      ],
    );
  }
}
