import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:get_it/get_it.dart';
import 'package:server_core/server_core.dart';

import '../providers/admin_user_providers.dart';

class AdminTaskDetailScreen extends ConsumerStatefulWidget {
  final String taskId;
  const AdminTaskDetailScreen({super.key, required this.taskId});

  @override
  ConsumerState<AdminTaskDetailScreen> createState() =>
      _AdminTaskDetailScreenState();
}

class _AdminTaskDetailScreenState
    extends ConsumerState<AdminTaskDetailScreen> {
  Timer? _refreshTimer;

  @override
  void initState() {
    super.initState();
    _refreshTimer = Timer.periodic(const Duration(seconds: 5), (_) {
      ref.invalidate(adminTaskProvider(widget.taskId));
    });
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    super.dispose();
  }

  AdminTasksApi get _api =>
      GetIt.instance<MediaServerClient>().adminTasksApi;

  @override
  Widget build(BuildContext context) {
    final taskAsync = ref.watch(adminTaskProvider(widget.taskId));

    return taskAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Failed to load task: $error'),
            const SizedBox(height: 8),
            ElevatedButton(
              onPressed: () =>
                  ref.invalidate(adminTaskProvider(widget.taskId)),
              child: const Text('Retry'),
            ),
          ],
        ),
      ),
      data: (task) => _buildContent(context, task),
    );
  }

  Widget _buildContent(BuildContext context, TaskInfo task) {
    final theme = Theme.of(context);
    final isRunning = task.state == 'Running';
    final isCancelling = task.state == 'Cancelling';

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(task.name, style: theme.textTheme.headlineSmall),
        if (task.description != null) ...[
          const SizedBox(height: 4),
          Text(task.description!,
              style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant)),
        ],
        if (task.category != null) ...[
          const SizedBox(height: 4),
          Chip(label: Text(task.category!)),
        ],
        const SizedBox(height: 16),

        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Status', style: theme.textTheme.titleMedium),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Icon(
                      isRunning
                          ? Icons.sync
                          : isCancelling
                              ? Icons.hourglass_top
                              : Icons.check_circle_outline,
                      color: isRunning
                          ? theme.colorScheme.primary
                          : isCancelling
                              ? Colors.orange
                              : theme.colorScheme.onSurfaceVariant,
                    ),
                    const SizedBox(width: 8),
                    Text(task.state, style: theme.textTheme.bodyLarge),
                    const Spacer(),
                    if (isRunning || isCancelling)
                      FilledButton.tonalIcon(
                        onPressed: isCancelling
                            ? null
                            : () => _stopTask(task.id),
                        icon: const Icon(Icons.stop),
                        label: const Text('Stop'),
                      )
                    else
                      FilledButton.tonalIcon(
                        onPressed: () => _startTask(task.id),
                        icon: const Icon(Icons.play_arrow),
                        label: const Text('Run Now'),
                      ),
                  ],
                ),
                if ((isRunning || isCancelling) &&
                    task.currentProgressPercentage != null) ...[
                  const SizedBox(height: 12),
                  LinearProgressIndicator(
                      value: task.currentProgressPercentage! / 100),
                  const SizedBox(height: 4),
                  Text(
                      '${task.currentProgressPercentage!.toStringAsFixed(1)}%'),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),

        if (task.lastExecutionResult != null) ...[
          _LastExecutionCard(result: task.lastExecutionResult!),
          const SizedBox(height: 12),
        ],

        _TriggersSection(
          triggers: task.triggers,
          onAdd: () => _showAddTriggerDialog(task),
          onRemove: (index) => _removeTrigger(task, index),
        ),
      ],
    );
  }

  Future<void> _startTask(String taskId) async {
    try {
      await _api.startTask(taskId);
      ref.invalidate(adminTaskProvider(widget.taskId));
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Failed to start task: $e')));
      }
    }
  }

  Future<void> _stopTask(String taskId) async {
    try {
      await _api.stopTask(taskId);
      ref.invalidate(adminTaskProvider(widget.taskId));
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Failed to stop task: $e')));
      }
    }
  }

  Future<void> _removeTrigger(TaskInfo task, int index) async {
    final triggers = List<TaskTriggerInfo>.from(task.triggers)
      ..removeAt(index);
    try {
      await _api.updateTaskTriggers(
        task.id,
        triggers.map((t) => t.toJson()).toList(),
      );
      ref.invalidate(adminTaskProvider(widget.taskId));
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to remove trigger: $e')));
      }
    }
  }

  Future<void> _showAddTriggerDialog(TaskInfo task) async {
    final trigger = await showDialog<TaskTriggerInfo>(
      context: context,
      builder: (context) => const _AddTriggerDialog(),
    );
    if (trigger == null || !mounted) return;

    final triggers = [...task.triggers, trigger];
    try {
      await _api.updateTaskTriggers(
        task.id,
        triggers.map((t) => t.toJson()).toList(),
      );
      ref.invalidate(adminTaskProvider(widget.taskId));
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to add trigger: $e')));
      }
    }
  }
}

class _LastExecutionCard extends StatelessWidget {
  final TaskResult result;
  const _LastExecutionCard({required this.result});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final duration = result.endTime.difference(result.startTime);

    Color statusColor;
    switch (result.status) {
      case 'Completed':
        statusColor = Colors.green;
      case 'Failed':
        statusColor = theme.colorScheme.error;
      case 'Cancelled':
      case 'Aborted':
        statusColor = Colors.orange;
      default:
        statusColor = theme.colorScheme.onSurfaceVariant;
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Last Execution', style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            Row(
              children: [
                Icon(Icons.circle, size: 10, color: statusColor),
                const SizedBox(width: 8),
                Text(result.status,
                    style: TextStyle(
                        color: statusColor, fontWeight: FontWeight.w500)),
              ],
            ),
            const SizedBox(height: 8),
            _infoRow('Started', _formatDateTime(result.startTime)),
            _infoRow('Ended', _formatDateTime(result.endTime)),
            _infoRow('Duration', _formatDuration(duration)),
            if (result.errorMessage != null) ...[
              const SizedBox(height: 8),
              Text('Error:',
                  style: TextStyle(color: theme.colorScheme.error)),
              const SizedBox(height: 4),
              Text(result.errorMessage!,
                  style: theme.textTheme.bodySmall
                      ?.copyWith(color: theme.colorScheme.error)),
            ],
          ],
        ),
      ),
    );
  }

  Widget _infoRow(String label, String value) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 2),
        child: Row(
          children: [
            SizedBox(
                width: 80,
                child: Text(label,
                    style: const TextStyle(fontWeight: FontWeight.w500))),
            Text(value),
          ],
        ),
      );

  String _formatDateTime(DateTime dt) {
    final local = dt.toLocal();
    return '${local.year}-${_p(local.month)}-${_p(local.day)} '
        '${_p(local.hour)}:${_p(local.minute)}:${_p(local.second)}';
  }

  String _p(int n) => n.toString().padLeft(2, '0');

  String _formatDuration(Duration d) {
    if (d.inHours > 0) {
      return '${d.inHours}h ${d.inMinutes.remainder(60)}m ${d.inSeconds.remainder(60)}s';
    }
    if (d.inMinutes > 0) {
      return '${d.inMinutes}m ${d.inSeconds.remainder(60)}s';
    }
    return '${d.inSeconds}s';
  }
}

class _TriggersSection extends StatelessWidget {
  final List<TaskTriggerInfo> triggers;
  final VoidCallback onAdd;
  final void Function(int index) onRemove;

  const _TriggersSection({
    required this.triggers,
    required this.onAdd,
    required this.onRemove,
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
            Row(
              children: [
                Text('Triggers', style: theme.textTheme.titleMedium),
                const Spacer(),
                FilledButton.tonalIcon(
                  onPressed: onAdd,
                  icon: const Icon(Icons.add),
                  label: const Text('Add Trigger'),
                ),
              ],
            ),
            const SizedBox(height: 8),
            if (triggers.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 16),
                child: Text('No triggers configured',
                    style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant)),
              )
            else
              ...triggers.asMap().entries.map((entry) {
                final i = entry.key;
                final trigger = entry.value;
                return ListTile(
                  leading: Icon(_triggerIcon(trigger.type)),
                  title: Text(_triggerDescription(trigger)),
                  subtitle: trigger.maxRuntimeTicks != null
                      ? Text(
                          'Time limit: ${_ticksToDuration(trigger.maxRuntimeTicks!)}')
                      : null,
                  trailing: IconButton(
                    icon: const Icon(Icons.delete_outline),
                    onPressed: () => onRemove(i),
                  ),
                );
              }),
          ],
        ),
      ),
    );
  }

  IconData _triggerIcon(String type) {
    switch (type) {
      case 'DailyTrigger':
        return Icons.today;
      case 'WeeklyTrigger':
        return Icons.calendar_view_week;
      case 'IntervalTrigger':
        return Icons.timer;
      case 'StartupTrigger':
        return Icons.power_settings_new;
      default:
        return Icons.schedule;
    }
  }

  String _triggerDescription(TaskTriggerInfo trigger) {
    switch (trigger.type) {
      case 'DailyTrigger':
        return 'Daily at ${_ticksToTime(trigger.timeOfDayTicks ?? 0)}';
      case 'WeeklyTrigger':
        return 'Every ${trigger.dayOfWeek ?? 'Unknown'} at ${_ticksToTime(trigger.timeOfDayTicks ?? 0)}';
      case 'IntervalTrigger':
        return 'Every ${_ticksToDuration(trigger.intervalTicks ?? 0)}';
      case 'StartupTrigger':
        return 'On application startup';
      default:
        return trigger.type;
    }
  }

  String _ticksToTime(int ticks) {
    final totalMinutes = ticks ~/ 600000000;
    final hours = totalMinutes ~/ 60;
    final minutes = totalMinutes % 60;
    final period = hours >= 12 ? 'PM' : 'AM';
    final displayHour = hours == 0 ? 12 : (hours > 12 ? hours - 12 : hours);
    return '$displayHour:${minutes.toString().padLeft(2, '0')} $period';
  }

  String _ticksToDuration(int ticks) {
    final totalMinutes = ticks ~/ 600000000;
    final hours = totalMinutes ~/ 60;
    final minutes = totalMinutes % 60;
    if (hours > 0 && minutes > 0) return '${hours}h ${minutes}m';
    if (hours > 0) return '${hours}h';
    return '${minutes}m';
  }
}

class _AddTriggerDialog extends StatefulWidget {
  const _AddTriggerDialog();

  @override
  State<_AddTriggerDialog> createState() => _AddTriggerDialogState();
}

class _AddTriggerDialogState extends State<_AddTriggerDialog> {
  String _type = 'IntervalTrigger';
  TimeOfDay _time = const TimeOfDay(hour: 3, minute: 0);
  String _dayOfWeek = 'Sunday';
  int _intervalHours = 24;
  int? _maxRuntimeHours;

  static const _triggerTypes = {
    'DailyTrigger': 'Daily',
    'WeeklyTrigger': 'Weekly',
    'IntervalTrigger': 'On an interval',
    'StartupTrigger': 'On application startup',
  };

  static const _daysOfWeek = [
    'Sunday',
    'Monday',
    'Tuesday',
    'Wednesday',
    'Thursday',
    'Friday',
    'Saturday',
  ];

  static const _intervalOptions = {
    1: 'Every hour',
    6: 'Every 6 hours',
    12: 'Every 12 hours',
    24: 'Every 24 hours',
    48: 'Every 2 days',
  };

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Add Trigger'),
      content: SizedBox(
        width: 360,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            DropdownButtonFormField<String>(
              value: _type,
              decoration: const InputDecoration(labelText: 'Trigger Type'),
              items: _triggerTypes.entries
                  .map((e) =>
                      DropdownMenuItem(value: e.key, child: Text(e.value)))
                  .toList(),
              onChanged: (v) => setState(() => _type = v!),
            ),
            const SizedBox(height: 16),
            ..._buildTypeFields(),
            const SizedBox(height: 16),
            DropdownButtonFormField<int?>(
              value: _maxRuntimeHours,
              decoration: const InputDecoration(
                  labelText: 'Time limit (optional)'),
              items: [
                const DropdownMenuItem(value: null, child: Text('No limit')),
                ...[1, 2, 3, 6, 12, 24].map((h) => DropdownMenuItem(
                    value: h,
                    child: Text('$h hour${h > 1 ? 's' : ''}'))),
              ],
              onChanged: (v) => setState(() => _maxRuntimeHours = v),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: _save,
          child: const Text('Save'),
        ),
      ],
    );
  }

  List<Widget> _buildTypeFields() {
    switch (_type) {
      case 'DailyTrigger':
        return [_buildTimePicker()];
      case 'WeeklyTrigger':
        return [
          DropdownButtonFormField<String>(
            value: _dayOfWeek,
            decoration: const InputDecoration(labelText: 'Day of week'),
            items: _daysOfWeek
                .map((d) => DropdownMenuItem(value: d, child: Text(d)))
                .toList(),
            onChanged: (v) => setState(() => _dayOfWeek = v!),
          ),
          const SizedBox(height: 16),
          _buildTimePicker(),
        ];
      case 'IntervalTrigger':
        return [
          DropdownButtonFormField<int>(
            value: _intervalHours,
            decoration: const InputDecoration(labelText: 'Interval'),
            items: _intervalOptions.entries
                .map((e) =>
                    DropdownMenuItem(value: e.key, child: Text(e.value)))
                .toList(),
            onChanged: (v) => setState(() => _intervalHours = v!),
          ),
        ];
      case 'StartupTrigger':
        return [];
      default:
        return [];
    }
  }

  Widget _buildTimePicker() {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: const Text('Time'),
      trailing: TextButton(
        onPressed: () async {
          final picked = await showTimePicker(
            context: context,
            initialTime: _time,
          );
          if (picked != null) setState(() => _time = picked);
        },
        child: Text(_time.format(context)),
      ),
    );
  }

  void _save() {
    int? timeOfDayTicks;
    if (_type == 'DailyTrigger' || _type == 'WeeklyTrigger') {
      timeOfDayTicks = (_time.hour * 60 + _time.minute) * 600000000;
    }

    int? intervalTicks;
    if (_type == 'IntervalTrigger') {
      intervalTicks = _intervalHours * 60 * 600000000;
    }

    final trigger = TaskTriggerInfo(
      type: _type,
      timeOfDayTicks: timeOfDayTicks,
      intervalTicks: intervalTicks,
      dayOfWeek: _type == 'WeeklyTrigger' ? _dayOfWeek : null,
      maxRuntimeTicks:
          _maxRuntimeHours != null ? _maxRuntimeHours! * 60 * 600000000 : null,
    );

    Navigator.pop(context, trigger);
  }
}
