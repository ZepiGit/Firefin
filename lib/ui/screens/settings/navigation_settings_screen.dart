import 'package:flutter/material.dart';
import 'package:get_it/get_it.dart';
import 'package:server_core/server_core.dart';

import '../../../data/services/plugin_sync_service.dart';
import '../../../preference/preference_constants.dart';
import '../../../preference/user_preferences.dart';
import '../../widgets/navigation_layout.dart';
import '../../widgets/settings/preference_tiles.dart';

class NavigationSettingsScreen extends StatefulWidget {
  const NavigationSettingsScreen({super.key});

  @override
  State<NavigationSettingsScreen> createState() =>
      _NavigationSettingsScreenState();
}

class _NavigationSettingsScreenState extends State<NavigationSettingsScreen> {
  final _prefs = GetIt.instance<UserPreferences>();

  void _pushSync() {
    final syncService = GetIt.instance<PluginSyncService>();
    if (syncService.pluginAvailable) {
      final client = GetIt.instance<MediaServerClient>();
      syncService.pushSettings(client);
    }
  }

  @override
  Widget build(BuildContext context) {
    final navbarPosition = _prefs.get(UserPreferences.navbarPosition);

    return Scaffold(
      appBar: AppBar(title: const Text('Navigation')),
      body: ListView(
        children: [
          ListTile(
            leading: const Icon(Icons.view_sidebar),
            title: const Text('Navigation Style'),
            subtitle: Text(
              navbarPosition == NavbarPosition.top
                  ? 'Top Bar'
                  : 'Left Sidebar',
            ),
            onTap: () {
              final newPos = navbarPosition == NavbarPosition.top
                  ? NavbarPosition.left
                  : NavbarPosition.top;
              _prefs.set(UserPreferences.navbarPosition, newPos);
              _pushSync();
              NavigationLayout.positionNotifier.value = newPos;
              setState(() {});
            },
          ),
          const Divider(),
          SwitchPreferenceTile(
            preference: UserPreferences.showShuffleButton,
            title: 'Show Shuffle Button',
            icon: Icons.shuffle,
            onChanged: _pushSync,
          ),
          SwitchPreferenceTile(
            preference: UserPreferences.showGenresButton,
            title: 'Show Genres Button',
            icon: Icons.category,
            onChanged: _pushSync,
          ),
          SwitchPreferenceTile(
            preference: UserPreferences.showFavoritesButton,
            title: 'Show Favorites Button',
            icon: Icons.favorite,
            onChanged: _pushSync,
          ),
          SwitchPreferenceTile(
            preference: UserPreferences.showLibrariesInToolbar,
            title: 'Show Libraries in Toolbar',
            iconBuilder: (size, color) => Image.asset(
              'assets/icons/clapperboard.png',
              width: size,
              height: size,
              color: color,
              fit: BoxFit.contain,
            ),
            onChanged: _pushSync,
          ),
          const Divider(),
          SliderPreferenceTile(
            preference: UserPreferences.navbarOpacity,
            title: 'Navbar Opacity',
            icon: Icons.opacity,
            min: 0,
            max: 100,
            divisions: 20,
            labelOf: (v) => '$v%',
            onChangeEnd: _pushSync,
          ),
          StringPickerPreferenceTile(
            preference: UserPreferences.navbarColor,
            title: 'Navbar Color',
            icon: Icons.color_lens,
            options: const {
              'gray': 'Gray',
              'black': 'Black',
              'dark_blue': 'Dark Blue',
              'purple': 'Purple',
              'teal': 'Teal',
              'navy': 'Navy',
              'charcoal': 'Charcoal',
              'brown': 'Brown',
              'dark_red': 'Dark Red',
              'dark_green': 'Dark Green',
              'slate': 'Slate',
              'indigo': 'Indigo',
            },
            onChanged: _pushSync,
          ),
        ],
      ),
    );
  }
}
