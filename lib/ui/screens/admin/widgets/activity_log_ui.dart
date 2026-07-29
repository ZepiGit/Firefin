import 'package:flutter/material.dart';
import 'package:server_core/server_core.dart';

String activityBucketLabel(DateTime date, {DateTime? now}) {
  final reference = now ?? DateTime.now();
  final diff = reference.difference(date);

  if (diff.inMinutes < 5) {
    return 'Just now';
  }
  if (diff.inMinutes < 60) {
    return 'Last hour';
  }

  final local = date.toLocal();
  final today = DateTime(reference.year, reference.month, reference.day);
  final entryDay = DateTime(local.year, local.month, local.day);
  if (!entryDay.isBefore(today)) {
    return 'Today';
  }
  if (diff.inDays < 2) {
    return 'Yesterday';
  }
  return 'Older';
}

List<Object> buildActivityListItems(
  List<ActivityLogEntry> entries, {
  DateTime? now,
}) {
  final items = <Object>[];
  final reference = now ?? DateTime.now();
  String? lastBucket;

  for (final entry in entries) {
    final bucket = activityBucketLabel(entry.date, now: reference);
    if (bucket != lastBucket) {
      items.add(bucket);
      lastBucket = bucket;
    }
    items.add(entry);
  }

  return items;
}

String activityTimeAgo(DateTime date, {bool compact = true}) {
  final diff = DateTime.now().difference(date);
  if (compact) {
    if (diff.inMinutes < 1) return 'now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m';
    if (diff.inHours < 24) return '${diff.inHours}h';
    if (diff.inDays < 7) return '${diff.inDays}d';
    return '${date.month}/${date.day}';
  }

  if (diff.inDays > 0) return '${diff.inDays}d ago';
  if (diff.inHours > 0) return '${diff.inHours}h ago';
  if (diff.inMinutes > 0) return '${diff.inMinutes}m ago';
  return 'just now';
}

(Color, Widget) activitySeverityIndicator(String severity, ThemeData theme) {
  switch (severity.toLowerCase()) {
    case 'error':
      return (
        theme.colorScheme.error,
        Icon(Icons.error_outline, size: 15, color: theme.colorScheme.error),
      );
    case 'warning':
    case 'warn':
      return (
        Colors.orange,
        const Icon(Icons.warning_amber, size: 15, color: Colors.orange),
      );
    case 'information':
      return (
        theme.colorScheme.primary,
        Icon(Icons.info_outline, size: 15, color: theme.colorScheme.primary),
      );
    default:
      return (
        theme.colorScheme.outlineVariant,
        Icon(
          Icons.info_outline,
          size: 15,
          color: theme.colorScheme.onSurfaceVariant,
        ),
      );
  }
}