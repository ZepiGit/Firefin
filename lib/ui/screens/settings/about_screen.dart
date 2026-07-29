import 'package:flutter/material.dart';
import 'package:get_it/get_it.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../data/services/app_update_service.dart';
import '../../../preference/user_preferences.dart';
import '../../../util/platform_detection.dart';
import '../../widgets/app_version_text.dart';
import '../../widgets/settings/preference_tiles.dart';

class AboutScreen extends StatelessWidget {
  const AboutScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('About')),
      body: ListView(
        children: [
          const SizedBox(height: 32),
          Center(
            child: Image.asset('assets/images/logo_and_text.png', height: 80),
          ),
          const SizedBox(height: 4),
          const Center(child: AppVersionText()),
          const SizedBox(height: 24),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.description),
            title: const Text('Open Source Licenses'),
            onTap: () async {
              final version = await loadAppVersion();
              if (!context.mounted) return;
              showLicensePage(
                context: context,
                applicationName: 'Moonfin FireTV32 Unofficial',
                applicationVersion: version,
                applicationIcon: Padding(
                  padding: const EdgeInsets.all(8.0),
                  child: Image.asset(
                    'assets/images/logo_and_text.png',
                    height: 48,
                  ),
                ),
              );
            },
          ),
          ListTile(
            leading: const Icon(Icons.code),
            title: const Text('Source Code'),
            subtitle: const Text(
              'https://github.com/Moonfin-Client/Moonfin-Core',
            ),
            onTap: () => launchUrl(
              Uri.parse('https://github.com/Moonfin-Client/Moonfin-Core'),
            ),
          ),
          if (PlatformDetection.isDesktop) ...[
            const Divider(),
            ListTile(
              leading: const Icon(Icons.system_update_alt),
              title: const Text('Check for Updates Now'),
              subtitle: const Text(
                'Checks latest desktop release for this platform',
              ),
              onTap: () async {
                final messenger = ScaffoldMessenger.of(context);
                final result = await GetIt.instance<AppUpdateService>()
                    .checkForUpdateNowDetailed();
                if (!context.mounted) {
                  return;
                }

                messenger.clearSnackBars();
                final update = result.update;
                if (update == null) {
                  final message = switch (result.status) {
                    DesktopUpdateCheckStatus.upToDate => 'You are up to date.',
                    DesktopUpdateCheckStatus.checkFailed =>
                      'Could not check for updates right now.',
                    DesktopUpdateCheckStatus.noMatchingAsset =>
                      'No compatible update package found for this platform.',
                    DesktopUpdateCheckStatus.unsupportedPlatform =>
                      'Update checks are not supported on this platform.',
                    DesktopUpdateCheckStatus.disabledByPreference =>
                      'Update notifications are disabled.',
                    DesktopUpdateCheckStatus.rateLimited =>
                      'Please wait before checking again.',
                    DesktopUpdateCheckStatus.alreadyNotified =>
                      'Latest update was already shown.',
                    DesktopUpdateCheckStatus.updateAvailable =>
                      'Update available.',
                  };
                  messenger.showSnackBar(
                    SnackBar(
                      content: Text(message),
                      duration: const Duration(seconds: 4),
                    ),
                  );
                  return;
                }

                messenger.showSnackBar(
                  SnackBar(
                    content: Text('Update available: v${update.version}'),
                    duration: const Duration(seconds: 10),
                    action: SnackBarAction(
                      label: 'Download',
                      onPressed: () {
                        launchUrl(
                          update.downloadUri,
                          mode: LaunchMode.externalApplication,
                        );
                      },
                    ),
                  ),
                );
              },
            ),
            SwitchPreferenceTile(
              preference: UserPreferences.updateNotificationsEnabled,
              title: 'Update Notifications',
              subtitle: 'Show when updates are available',
              icon: Icons.system_update,
            ),
          ],
        ],
      ),
    );
  }
}
