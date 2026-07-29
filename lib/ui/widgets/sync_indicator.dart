import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/services/sync_service.dart';
import '../../di/providers.dart';

class SyncIndicator extends ConsumerWidget {
  const SyncIndicator({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final syncState = ref.watch(syncStateProvider);
    return switch (syncState) {
      SyncState.idle => const SizedBox.shrink(),
      SyncState.syncing => const _AnimatedSyncIcon(),
      SyncState.done => const Icon(Icons.cloud_done, color: Colors.green, size: 18),
      SyncState.error => const Icon(Icons.sync_problem, color: Colors.orange, size: 18),
    };
  }
}

class _AnimatedSyncIcon extends StatefulWidget {
  const _AnimatedSyncIcon();

  @override
  State<_AnimatedSyncIcon> createState() => _AnimatedSyncIconState();
}

class _AnimatedSyncIconState extends State<_AnimatedSyncIcon>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 1),
    )..repeat();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return RotationTransition(
      turns: _controller,
      child: const Icon(Icons.sync, color: Colors.white70, size: 18),
    );
  }
}
