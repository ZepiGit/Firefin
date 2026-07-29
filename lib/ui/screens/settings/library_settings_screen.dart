import 'package:flutter/material.dart';
import 'package:get_it/get_it.dart';
import 'package:server_core/server_core.dart' hide ImageType;

import '../../../data/models/aggregated_library.dart';
import '../../../data/repositories/user_views_repository.dart';
import '../../../preference/preference_constants.dart';
import '../../../preference/user_preferences.dart';
import '../../widgets/settings/preference_tiles.dart';

class LibrarySettingsScreen extends StatefulWidget {
  const LibrarySettingsScreen({super.key});

  @override
  State<LibrarySettingsScreen> createState() => _LibrarySettingsScreenState();
}

class _LibrarySettingsScreenState extends State<LibrarySettingsScreen> {
  final _viewsRepo = GetIt.instance<UserViewsRepository>();

  List<AggregatedLibrary>? _libraries;
  UserConfiguration? _config;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final results = await Future.wait([
        _viewsRepo.getAllViews(),
        _viewsRepo.getUserConfiguration(),
      ]);
      if (!mounted) return;
      setState(() {
        _libraries = results[0] as List<AggregatedLibrary>;
        _config = results[1] as UserConfiguration;
        _isLoading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _isLoading = false);
    }
  }

  Future<void> _toggleExclude(String libraryId, bool hidden, {required bool isLatest}) async {
    final config = _config;
    if (config == null) return;

    final source = isLatest ? config.latestItemsExcludes : config.myMediaExcludes;
    final excludes = List<String>.from(source);
    if (hidden) {
      if (!excludes.contains(libraryId)) excludes.add(libraryId);
    } else {
      excludes.remove(libraryId);
    }

    final updated = isLatest
        ? config.copyWith(latestItemsExcludes: excludes)
        : config.copyWith(myMediaExcludes: excludes);
    setState(() => _config = updated);
    try {
      await _viewsRepo.updateUserConfiguration(updated);
    } catch (_) {
      if (!mounted) return;
      setState(() => _config = config);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Library Display')),
      body: ListView(
        children: [
          EnumPreferenceTile<PosterSize>(
            preference: UserPreferences.posterSize,
            title: 'Poster Size',
            icon: Icons.photo_size_select_large,
            labelOf: (v) => switch (v) {
              PosterSize.small => 'Small',
              PosterSize.medium => 'Medium',
              PosterSize.large => 'Large',
              PosterSize.extraLarge => 'Extra Large',
            },
          ),
          EnumPreferenceTile<ImageType>(
            preference: UserPreferences.homeRowsUniversalImageType,
            title: 'Image Type',
            icon: Icons.image,
            labelOf: (v) => switch (v) {
              ImageType.poster => 'Poster',
              ImageType.thumb => 'Thumbnail',
              ImageType.banner => 'Banner',
            },
          ),
          SwitchPreferenceTile(
            preference: UserPreferences.homeRowsUniversalOverride,
            title: 'Override Per-Library Settings',
            subtitle: 'Apply image type to all libraries',
            icon: Icons.layers,
          ),
          SwitchPreferenceTile(
            preference: UserPreferences.enableMultiServerLibraries,
            title: 'Multi-Server Libraries',
            subtitle: 'Show libraries from all connected servers',
            icon: Icons.dns,
          ),
          SwitchPreferenceTile(
            preference: UserPreferences.enableFolderView,
            title: 'Enable Folder View',
            subtitle: 'Show folder browsing option',
            icon: Icons.folder,
          ),
          const Divider(),
          if (_isLoading)
            const Padding(
              padding: EdgeInsets.all(16),
              child: Center(child: CircularProgressIndicator()),
            )
          else if (_libraries != null && _config != null)
            ..._buildLibraryVisibilityTiles(),
        ],
      ),
    );
  }

  List<Widget> _buildLibraryVisibilityTiles() {
    final config = _config!;
    final libraries = _libraries!;

    return [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
        child: Text(
          'Library Visibility',
          style: Theme.of(context).textTheme.titleSmall?.copyWith(
                color: Theme.of(context).colorScheme.primary,
              ),
        ),
      ),
      for (final lib in libraries) ...[
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
          child: Text(lib.name, style: Theme.of(context).textTheme.titleMedium),
        ),
        SwitchListTile(
          secondary: const Icon(Icons.visibility),
          title: const Text('Show in navigation'),
          value: !config.myMediaExcludes.contains(lib.id),
          onChanged: (v) => _toggleExclude(lib.id, !v, isLatest: false),
        ),
        SwitchListTile(
          secondary: const Icon(Icons.new_releases),
          title: const Text('Show in latest media'),
          value: !config.latestItemsExcludes.contains(lib.id),
          onChanged: (v) => _toggleExclude(lib.id, !v, isLatest: true),
        ),
      ],
    ];
  }
}
