import 'dart:async';

import 'package:flutter/material.dart';
import 'package:get_it/get_it.dart';
import 'package:server_core/server_core.dart';

class SessionDetailSheet extends StatefulWidget {
  final Map<String, dynamic> session;

  const SessionDetailSheet({super.key, required this.session});

  @override
  State<SessionDetailSheet> createState() => _SessionDetailSheetState();
}

class _SessionDetailSheetState extends State<SessionDetailSheet> {
  late final SessionApi _sessionApi;
  late Map<String, dynamic> _session;
  bool _busy = false;
  double? _seekPosition;
  Timer? _refreshTimer;

  @override
  void initState() {
    super.initState();
    _sessionApi = GetIt.instance<MediaServerClient>().sessionApi;
    _session = widget.session;
    _refreshTimer = Timer.periodic(const Duration(seconds: 5), (_) => _refresh());
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    super.dispose();
  }

  Future<void> _refresh() async {
    try {
      final sessions = await _sessionApi.getSessions();
      if (!mounted) return;
      final updated = sessions.firstWhere(
        (s) => s['Id'] == _session['Id'],
        orElse: () => _session,
      );
      setState(() => _session = updated);
    } catch (_) {}
  }

  Future<void> _run(Future<void> Function() action) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await action();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Command failed: $e'),
            backgroundColor: Theme.of(context).colorScheme.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _sendPlayState(String command, {int? seekTicks}) {
    return _run(() => _sessionApi.sendPlayStateCommand(
          _session['Id'] as String,
          command,
          seekPositionTicks: seekTicks,
        ));
  }

  Future<void> _sendGeneral(String commandName, {Map<String, String>? args}) {
    return _run(() => _sessionApi.sendGeneralCommand(
          _session['Id'] as String,
          commandName,
          arguments: args,
        ));
  }

  void _showSendMessageDialog() {
    final controller = TextEditingController();
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Send Message'),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLines: 3,
          decoration: const InputDecoration(hintText: 'Message text'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(
            onPressed: () {
              final text = controller.text.trim();
              Navigator.pop(ctx);
              if (text.isNotEmpty) {
                _run(() => _sessionApi.sendMessage(_session['Id'] as String, text));
              }
            },
            child: const Text('Send'),
          ),
        ],
      ),
    );
  }

  void _showVolumeDialog() {
    final volumeData = _session['PlayState']?['VolumeLevel'] as int?;
    var volume = volumeData?.toDouble() ?? 50.0;
    showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setInner) => AlertDialog(
          title: const Text('Set Volume'),
          content: Slider(
            value: volume,
            min: 0,
            max: 100,
            divisions: 100,
            label: '${volume.round()}%',
            onChanged: (v) => setInner(() => volume = v),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
            FilledButton(
              onPressed: () {
                Navigator.pop(ctx);
                _sendGeneral('SetVolume', args: {'Volume': volume.round().toString()});
              },
              child: const Text('Set'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final sessionId = _session['Id'] as String? ?? '';
    if (sessionId.isEmpty) {
      return const SizedBox.shrink();
    }
    final userName = _session['UserName'] as String? ?? 'Unknown';
    final client = _session['Client'] as String? ?? '';
    final device = _session['DeviceName'] as String? ?? '';
    final appVersion = _session['ApplicationVersion'] as String? ?? '';
    final nowPlaying = _session['NowPlayingItem'] as Map<String, dynamic>?;
    final playState = _session['PlayState'] as Map<String, dynamic>?;
    final transcodingInfo = _session['TranscodingInfo'] as Map<String, dynamic>?;

    final isPaused = playState?['IsPaused'] as bool? ?? false;
    final isMuted = playState?['IsMuted'] as bool? ?? false;
    final positionTicks = playState?['PositionTicks'] as int?;
    final runtimeTicks = nowPlaying?['RunTimeTicks'] as int?;

    String ticksToTime(int ticks) {
      final duration = Duration(microseconds: ticks ~/ 10);
      final h = duration.inHours;
      final m = duration.inMinutes.remainder(60).toString().padLeft(2, '0');
      final s = duration.inSeconds.remainder(60).toString().padLeft(2, '0');
      return h > 0 ? '$h:$m:$s' : '$m:$s';
    }

    return DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.7,
      minChildSize: 0.4,
      maxChildSize: 0.95,
      builder: (context, scrollController) => Container(
        decoration: BoxDecoration(
          color: theme.colorScheme.surface,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
        ),
        child: Column(
          children: [
            Container(
              margin: const EdgeInsets.symmetric(vertical: 10),
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: theme.colorScheme.outlineVariant,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            Expanded(
              child: ListView(
                controller: scrollController,
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
                children: [
                  Row(
                    children: [
                      CircleAvatar(
                        radius: 22,
                        backgroundColor: theme.colorScheme.primaryContainer,
                        child: Text(
                          userName.isNotEmpty ? userName[0].toUpperCase() : '?',
                          style: theme.textTheme.titleMedium,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(userName, style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                            Text(
                              '$client · $device${appVersion.isNotEmpty ? ' ($appVersion)' : ''}',
                              style: theme.textTheme.bodySmall,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      ),
                      if (_busy) const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2)),
                    ],
                  ),
                  const SizedBox(height: 16),

                  if (nowPlaying != null) ...[
                    _SectionLabel('Now Playing'),
                    const SizedBox(height: 8),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surfaceContainerLow,
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: theme.colorScheme.outlineVariant.withValues(alpha: 0.5)),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            nowPlaying['Name'] as String? ?? '',
                            style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                          ),
                          if ((nowPlaying['SeriesName'] as String?) != null)
                            Text(
                              nowPlaying['SeriesName'] as String,
                              style: theme.textTheme.bodySmall,
                            ),
                          if (positionTicks != null && runtimeTicks != null && runtimeTicks > 0) ...[
                            const SizedBox(height: 8),
                            Row(
                              children: [
                                Text(ticksToTime(positionTicks), style: theme.textTheme.labelSmall),
                                Expanded(
                                  child: Slider(
                                    value: (_seekPosition ?? positionTicks / runtimeTicks).clamp(0.0, 1.0),
                                    onChanged: (v) => setState(() => _seekPosition = v),
                                    onChangeEnd: (v) {
                                      final target = (v * runtimeTicks).round();
                                      _seekPosition = null;
                                      _sendPlayState('Seek', seekTicks: target);
                                    },
                                  ),
                                ),
                                Text(ticksToTime(runtimeTicks), style: theme.textTheme.labelSmall),
                              ],
                            ),
                          ],
                          if (isPaused)
                            Container(
                              margin: const EdgeInsets.only(top: 4),
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                              decoration: BoxDecoration(
                                color: theme.colorScheme.secondaryContainer,
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: Text('Paused', style: theme.textTheme.labelSmall),
                            ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 12),

                    _SectionLabel('Playback'),
                    const SizedBox(height: 8),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        _ControlButton(
                          icon: Icons.skip_previous,
                          label: 'Prev',
                          onTap: () => _sendPlayState('PreviousTrack'),
                        ),
                        const SizedBox(width: 8),
                        _ControlButton(
                          icon: Icons.replay_10,
                          label: 'Rewind',
                          onTap: () => _sendPlayState('Rewind'),
                        ),
                        const SizedBox(width: 8),
                        _ControlButton(
                          icon: isPaused ? Icons.play_arrow : Icons.pause,
                          label: isPaused ? 'Play' : 'Pause',
                          onTap: () => _sendPlayState('PlayPause'),
                          primary: true,
                        ),
                        const SizedBox(width: 8),
                        _ControlButton(
                          icon: Icons.forward_10,
                          label: 'Forward',
                          onTap: () => _sendPlayState('FastForward'),
                        ),
                        const SizedBox(width: 8),
                        _ControlButton(
                          icon: Icons.skip_next,
                          label: 'Next',
                          onTap: () => _sendPlayState('NextTrack'),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Center(
                      child: _ControlButton(
                        icon: Icons.stop,
                        label: 'Stop',
                        onTap: () => _sendPlayState('Stop'),
                        color: theme.colorScheme.error,
                      ),
                    ),
                    const SizedBox(height: 12),

                    _SectionLabel('Volume'),
                    const SizedBox(height: 8),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        _ControlButton(
                          icon: isMuted ? Icons.volume_off : Icons.volume_up,
                          label: isMuted ? 'Unmute' : 'Mute',
                          onTap: () => _sendGeneral(isMuted ? 'Unmute' : 'Mute'),
                        ),
                        const SizedBox(width: 8),
                        _ControlButton(
                          icon: Icons.volume_down,
                          label: 'Vol –',
                          onTap: () => _sendGeneral('VolumeDown'),
                        ),
                        const SizedBox(width: 8),
                        _ControlButton(
                          icon: Icons.volume_up,
                          label: 'Vol +',
                          onTap: () => _sendGeneral('VolumeUp'),
                        ),
                        const SizedBox(width: 8),
                        _ControlButton(
                          icon: Icons.tune,
                          label: 'Set',
                          onTap: _showVolumeDialog,
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                  ],

                  if (transcodingInfo != null) ...[
                    _SectionLabel('Transcoding'),
                    const SizedBox(height: 8),
                    _InfoGrid({
                      'Video Codec': transcodingInfo['VideoCodec'] as String? ?? '–',
                      'Audio Codec': transcodingInfo['AudioCodec'] as String? ?? '–',
                      'Container': transcodingInfo['Container'] as String? ?? '–',
                      'Bitrate': transcodingInfo['Bitrate'] != null
                          ? '${((transcodingInfo['Bitrate'] as int) / 1000000).toStringAsFixed(1)} Mbps'
                          : '–',
                      'HW Accel': transcodingInfo['IsVideoDirect'] == true ? 'Direct' : 'Transcoding',
                      'Completion': transcodingInfo['CompletionPercentage'] != null
                          ? '${(transcodingInfo['CompletionPercentage'] as num).toStringAsFixed(1)}%'
                          : '–',
                    }),
                    const SizedBox(height: 12),
                  ],

                  _SectionLabel('Actions'),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton.icon(
                          icon: const Icon(Icons.message_outlined),
                          label: const Text('Send Message'),
                          onPressed: _showSendMessageDialog,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: OutlinedButton.icon(
                          icon: const Icon(Icons.link_off),
                          label: const Text('Disconnect'),
                          style: OutlinedButton.styleFrom(foregroundColor: theme.colorScheme.error),
                          onPressed: () => _run(() => _sessionApi.sendGeneralCommand(sessionId, 'GoHome')),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  final String text;
  const _SectionLabel(this.text);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Text(
      text.toUpperCase(),
      style: theme.textTheme.labelSmall?.copyWith(
        color: theme.colorScheme.primary,
        fontWeight: FontWeight.w700,
        letterSpacing: 0.8,
      ),
    );
  }
}

class _ControlButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool primary;
  final Color? color;

  const _ControlButton({
    required this.icon,
    required this.label,
    required this.onTap,
    this.primary = false,
    this.color,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final fg = color ?? (primary ? theme.colorScheme.primary : theme.colorScheme.onSurface);
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: primary ? 36 : 28, color: fg),
            const SizedBox(height: 2),
            Text(label, style: theme.textTheme.labelSmall?.copyWith(color: fg)),
          ],
        ),
      ),
    );
  }
}

class _InfoGrid extends StatelessWidget {
  final Map<String, String> data;
  const _InfoGrid(this.data);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final entries = data.entries.toList();
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: theme.colorScheme.outlineVariant.withValues(alpha: 0.5)),
      ),
      child: Wrap(
        spacing: 24,
        runSpacing: 8,
        children: entries
            .map((e) => Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(e.key, style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.outline)),
                    Text(e.value, style: theme.textTheme.bodySmall?.copyWith(fontWeight: FontWeight.w600)),
                  ],
                ))
            .toList(),
      ),
    );
  }
}
