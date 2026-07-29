import 'package:flutter/material.dart';
import 'package:get_it/get_it.dart';
import 'package:server_core/server_core.dart';

class AdminBrandingScreen extends StatefulWidget {
  const AdminBrandingScreen({super.key});

  @override
  State<AdminBrandingScreen> createState() => _AdminBrandingScreenState();
}

class _AdminBrandingScreenState extends State<AdminBrandingScreen> {
  late final AdminSystemApi _api;
  Map<String, dynamic>? _config;
  bool _loading = true;
  bool _saving = false;
  String? _error;

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
      final config = await _api.getNamedConfiguration('branding');
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
      await _api.updateNamedConfiguration('branding', _config!);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Branding settings saved')),
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
            Text('Failed to load branding settings',
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
        Text('Branding', style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 24),
        TextFormField(
          initialValue: _config!['LoginDisclaimer']?.toString() ?? '',
          decoration: const InputDecoration(
            labelText: 'Login disclaimer',
            hintText: 'HTML displayed below the login form',
            border: OutlineInputBorder(),
            alignLabelWithHint: true,
          ),
          maxLines: 5,
          onChanged: (v) => _config!['LoginDisclaimer'] = v,
        ),
        const SizedBox(height: 16),
        TextFormField(
          initialValue: _config!['CustomCss']?.toString() ?? '',
          decoration: const InputDecoration(
            labelText: 'Custom CSS',
            hintText: 'Custom CSS applied to the web interface',
            border: OutlineInputBorder(),
            alignLabelWithHint: true,
          ),
          maxLines: 10,
          style: const TextStyle(fontFamily: 'monospace', fontSize: 13),
          onChanged: (v) => _config!['CustomCss'] = v,
        ),
        const SizedBox(height: 16),
        SwitchListTile(
          title: const Text('Enable splash screen'),
          value: _config!['SplashscreenEnabled'] as bool? ?? false,
          onChanged: (v) =>
              setState(() => _config!['SplashscreenEnabled'] = v),
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
}
