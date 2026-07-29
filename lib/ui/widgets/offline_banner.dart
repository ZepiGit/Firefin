import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../di/providers.dart';
import '../../util/platform_detection.dart';
import '../navigation/app_router.dart';
import '../navigation/destinations.dart';

class OfflineBanner extends ConsumerWidget {
  const OfflineBanner({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isOnline = ref.watch(isOnlineProvider);
    final serverReachable = ref.watch(activeServerReachableProvider);

    // The dedicated Fire OS 5 build can load authenticated content even when
    // Jellyfin's separate ping endpoint fails on the legacy TLS stack. Treat
    // successful network connectivity as authoritative here so that this
    // known false negative never reserves an orange banner above the TV UI.
    if (PlatformDetection.isAndroid && PlatformDetection.isTV && isOnline) {
      return const SizedBox.shrink();
    }

    if (isOnline && serverReachable) return const SizedBox.shrink();

    final isServerUnavailable = isOnline && !serverReachable;
    final bannerText = isServerUnavailable
        ? 'Connected to the internet, but the current server is unavailable.'
        : 'You are offline. Only downloaded content is available.';
    final actionLabel = isServerUnavailable ? 'Switch Server' : 'Saved Media';
    final action = isServerUnavailable
        ? () => appRouter.go(Destinations.serverSelect)
        : () => appRouter.go(Destinations.downloads);

    return SafeArea(
      bottom: false,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        color: Colors.orange.shade900.withValues(alpha: 0.9),
        child: Row(
          children: [
            const Icon(Icons.cloud_off, color: Colors.white, size: 18),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                bannerText,
                style: const TextStyle(color: Colors.white, fontSize: 13),
              ),
            ),
            TextButton(
              onPressed: action,
              style: TextButton.styleFrom(
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(horizontal: 12),
              ),
              child: Text(actionLabel),
            ),
          ],
        ),
      ),
    );
  }
}
