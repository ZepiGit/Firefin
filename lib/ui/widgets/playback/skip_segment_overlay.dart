import 'dart:async';

import 'package:flutter/material.dart';
import 'package:jellyfin_design/jellyfin_design.dart';

import '../../../data/models/media_segment.dart';

class SkipSegmentOverlay extends StatefulWidget {
  final MediaSegment segment;
  final VoidCallback onSkip;
  final VoidCallback onDismiss;

  const SkipSegmentOverlay({
    super.key,
    required this.segment,
    required this.onSkip,
    required this.onDismiss,
  });

  @override
  State<SkipSegmentOverlay> createState() => _SkipSegmentOverlayState();
}

class _SkipSegmentOverlayState extends State<SkipSegmentOverlay> {
  Timer? _autoHide;

  @override
  void initState() {
    super.initState();
    _autoHide = Timer(const Duration(seconds: 8), widget.onDismiss);
  }

  @override
  void dispose() {
    _autoHide?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Positioned(
      right: 24,
      bottom: 120,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: widget.onSkip,
          borderRadius: BorderRadius.circular(8),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
            decoration: BoxDecoration(
              color: AppColorScheme.surface.withValues(alpha: 0.9),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: AppColorScheme.accent, width: 1.5),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  'Skip ${widget.segment.type.displayName}',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(width: 8),
                const Icon(
                  Icons.skip_next_rounded,
                  color: AppColorScheme.accent,
                  size: 22,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
