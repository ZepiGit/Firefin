import 'package:flutter/material.dart';
import 'package:get_it/get_it.dart';
import 'package:server_core/server_core.dart';

class AdminTrickplayScreen extends StatefulWidget {
  const AdminTrickplayScreen({super.key});

  @override
  State<AdminTrickplayScreen> createState() => _AdminTrickplayScreenState();
}

class _AdminTrickplayScreenState extends State<AdminTrickplayScreen> {
  late final AdminSystemApi _api;
  Map<String, dynamic>? _config;
  bool _loading = true;
  bool _saving = false;
  String? _error;

  static const _scanBehaviors = [
    ('NonBlocking', 'Non-Blocking'),
    ('Blocking', 'Blocking'),
  ];

  static const _priorities = [
    ('High', 'High'),
    ('AboveNormal', 'Above Normal'),
    ('Normal', 'Normal'),
    ('BelowNormal', 'Below Normal'),
    ('Idle', 'Idle'),
  ];

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
        _config!['TrickplayOptions'] ??= <String, dynamic>{};
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
          const SnackBar(content: Text('Trickplay settings saved')),
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

  Map<String, dynamic> get _opts =>
      _config!['TrickplayOptions'] as Map<String, dynamic>;

  int _intOpt(String key, [int fallback = 0]) =>
      (_opts[key] as num?)?.toInt() ?? fallback;

  bool _boolOpt(String key) => _opts[key] as bool? ?? false;

  String _strOpt(String key, [String fallback = '']) =>
      _opts[key]?.toString() ?? fallback;

  String _widthResolutionsText() {
    final raw = _opts['WidthResolutions'];
    if (raw is List) return raw.map((e) => e.toString()).join(', ');
    return '320';
  }

  @override
  Widget build(BuildContext context) {
    final bottomSafe = MediaQuery.of(context).padding.bottom;
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_error != null || _config == null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Failed to load trickplay settings',
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
        Text('Trickplay', style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 8),
        Text(
          'Configure trickplay image generation for seek preview thumbnails.',
          style: Theme.of(context).textTheme.bodyMedium,
        ),
        const SizedBox(height: 24),
        _sectionHeader('General'),
        SwitchListTile(
          title: const Text('Enable hardware acceleration'),
          value: _boolOpt('EnableHwAcceleration'),
          onChanged: (v) => setState(() => _opts['EnableHwAcceleration'] = v),
        ),
        SwitchListTile(
          title: const Text('Enable hardware encoding'),
          value: _boolOpt('EnableHwEncoding'),
          onChanged: (v) => setState(() => _opts['EnableHwEncoding'] = v),
        ),
        SwitchListTile(
          title: const Text('Enable key frame only extraction'),
          subtitle: const Text('Faster but lower accuracy'),
          value: _boolOpt('EnableKeyFrameOnlyExtraction'),
          onChanged: (v) =>
              setState(() => _opts['EnableKeyFrameOnlyExtraction'] = v),
        ),
        const SizedBox(height: 16),
        DropdownButtonFormField<String>(
          value: _scanBehaviors.any((o) => o.$1 == _strOpt('ScanBehavior', 'NonBlocking'))
              ? _strOpt('ScanBehavior', 'NonBlocking')
              : 'NonBlocking',
          decoration: const InputDecoration(
            labelText: 'Scan behavior',
            border: OutlineInputBorder(),
          ),
          items: _scanBehaviors
              .map((o) => DropdownMenuItem(value: o.$1, child: Text(o.$2)))
              .toList(),
          onChanged: (v) => setState(() => _opts['ScanBehavior'] = v),
        ),
        const SizedBox(height: 12),
        DropdownButtonFormField<String>(
          value: _priorities.any((o) => o.$1 == _strOpt('ProcessPriority', 'BelowNormal'))
              ? _strOpt('ProcessPriority', 'BelowNormal')
              : 'BelowNormal',
          decoration: const InputDecoration(
            labelText: 'Process priority',
            border: OutlineInputBorder(),
          ),
          items: _priorities
              .map((o) => DropdownMenuItem(value: o.$1, child: Text(o.$2)))
              .toList(),
          onChanged: (v) => setState(() => _opts['ProcessPriority'] = v),
        ),
        const Divider(height: 32),
        _sectionHeader('Image Settings'),
        _intField('Interval', 'Interval (ms)', fallback: 10000,
            subtitle: 'How often to capture frames'),
        const SizedBox(height: 12),
        TextFormField(
          initialValue: _widthResolutionsText(),
          decoration: const InputDecoration(
            labelText: 'Width resolutions',
            helperText: 'Comma-separated pixel widths (e.g. 320)',
            border: OutlineInputBorder(),
          ),
          onChanged: (v) {
            _opts['WidthResolutions'] = v
                .split(',')
                .map((s) => int.tryParse(s.trim()))
                .where((n) => n != null && n > 0)
                .toList();
          },
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: _intField('TileWidth', 'Tile width', fallback: 10),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _intField('TileHeight', 'Tile height', fallback: 10),
            ),
          ],
        ),
        const Divider(height: 32),
        _sectionHeader('Quality'),
        _sliderField('Qscale', 'Quality scale', min: 2, max: 31, fallback: 4,
            subtitle: 'Lower values = better quality, larger files'),
        const SizedBox(height: 16),
        _sliderField('JpegQuality', 'JPEG quality', min: 1, max: 100,
            fallback: 90),
        const Divider(height: 32),
        _sectionHeader('Processing'),
        _intField('ProcessThreads', 'Process threads', fallback: 1,
            subtitle: '0 = automatic'),
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
      child: Text(text, style: Theme.of(context).textTheme.titleMedium),
    );
  }

  Widget _intField(String key, String label,
      {int fallback = 0, String? subtitle}) {
    return TextFormField(
      initialValue: _intOpt(key, fallback).toString(),
      decoration: InputDecoration(
        labelText: label,
        helperText: subtitle,
        border: const OutlineInputBorder(),
      ),
      keyboardType: TextInputType.number,
      onChanged: (v) => _opts[key] = int.tryParse(v) ?? fallback,
    );
  }

  Widget _sliderField(
    String key,
    String label, {
    required double min,
    required double max,
    int fallback = 0,
    String? subtitle,
  }) {
    final value =
        _intOpt(key, fallback).clamp(min.toInt(), max.toInt()).toDouble();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: Theme.of(context).textTheme.bodyLarge),
        if (subtitle != null)
          Text(subtitle, style: Theme.of(context).textTheme.bodySmall),
        Row(
          children: [
            Expanded(
              child: Slider(
                value: value,
                min: min,
                max: max,
                divisions: (max - min).toInt(),
                label: value.toInt().toString(),
                onChanged: (v) => setState(() => _opts[key] = v.toInt()),
              ),
            ),
            SizedBox(
              width: 40,
              child: Text('${value.toInt()}',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodyMedium),
            ),
          ],
        ),
      ],
    );
  }
}
