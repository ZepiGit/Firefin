import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:get_it/get_it.dart';
import 'package:go_router/go_router.dart';

import '../../../auth/repositories/session_repository.dart';
import '../../../data/services/plugin_sync_service.dart';
import '../../../di/providers.dart';
import '../../../preference/user_preferences.dart';
import '../../navigation/destinations.dart';
import '../admin/providers/admin_status_providers.dart';
import '../../../util/platform_detection.dart';
import 'customization_entries.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  late final PluginSyncService _pluginSync;

  @override
  void initState() {
    super.initState();
    _pluginSync = GetIt.instance<PluginSyncService>();
    _pluginSync.addListener(_onPluginSyncChanged);
  }

  void _onPluginSyncChanged() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _pluginSync.removeListener(_onPluginSyncChanged);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final showSeerrIntegration = _pluginSync.pluginAvailable;
    final isAdmin = ref.watch(isAdminProvider);
    final adminBadgeCount = isAdmin
        ? ref.watch(adminNotificationSummaryProvider).valueOrNull?.count ?? 0
        : 0;
    final theme = Theme.of(context);
    final accountEntries = <_SettingsEntry>[
      _SettingsEntry(
        icon: Icons.manage_accounts,
        title: 'Authentication',
        subtitle: 'Auto login, server management',
        onTap: () => context.push(Destinations.settingsAuth),
      ),
      _SettingsEntry(
        icon: Icons.pin,
        title: 'PIN Code',
        subtitle: 'Set up PIN code protection',
        onTap: () => context.push(Destinations.settingsPinCode),
      ),
      _SettingsEntry(
        icon: Icons.child_care,
        title: 'Parental Controls',
        subtitle: 'Content rating restrictions',
        onTap: () => context.push(Destinations.settingsParental),
      ),
    ];

    final customizationEntries =
        buildCustomizationEntries(isMobile: PlatformDetection.isMobile)
            .map(
              (entry) => _SettingsEntry(
                icon: entry.icon,
                title: entry.title,
                subtitle: entry.subtitle,
                onTap: () => context.push(entry.destination),
              ),
            )
            .toList();

    final playbackEntries = <_SettingsEntry>[
      _SettingsEntry(
        icon: Icons.play_circle_fill,
        title: 'Playback',
        subtitle: 'Bitrate, resolution, behavior',
        onTap: () => context.push(Destinations.settingsPlayback),
      ),
      _SettingsEntry(
        icon: Icons.subtitles,
        title: 'Subtitles',
        subtitle: 'Language, size, appearance',
        onTap: () => context.push(Destinations.settingsSubtitles),
      ),
      _SettingsEntry(
        icon: Icons.download,
        title: 'Downloads',
        subtitle: 'Quality, storage',
        onTap: () => context.push(Destinations.settingsDownloads),
      ),
    ];

    final moonfinEntries = <_SettingsEntry>[
      _SettingsEntry(
        iconBuilder: (size, color) => Image.asset(
          'assets/icons/moonfin.png',
          width: size,
          height: size,
          color: color,
          fit: BoxFit.contain,
        ),
        title: 'Plugin',
        subtitle: 'Server sync and plugin status',
        onTap: () => context.push(Destinations.settingsPlugin),
      ),
      if (showSeerrIntegration)
        _SettingsEntry(
          iconBuilder: (size, color) => Image.asset(
            'assets/icons/seerr.png',
            width: size,
            height: size,
            color: color,
            fit: BoxFit.contain,
          ),
          title: 'Seerr',
          subtitle: 'Media request integration',
          onTap: () => context.push(Destinations.settingsSeerr),
        ),
    ];

    final otherEntries = <_SettingsEntry>[
      _SettingsEntry(
        icon: Icons.swap_horiz,
        title: 'Switch Server',
        onTap: () => context.go(Destinations.serverSelect),
      ),
      _SettingsEntry(
        icon: Icons.logout,
        title: 'Sign Out',
        onTap: () async {
          await GetIt.instance<SessionRepository>().destroyCurrentSession();
          if (context.mounted) context.go(Destinations.serverSelect);
        },
      ),
      _SettingsEntry(
        icon: Icons.info,
        title: 'About',
        subtitle: 'Version, licenses',
        onTap: () => context.push(Destinations.settingsAbout),
      ),
    ];

    final sections = <_SettingsSectionData>[
      _SettingsSectionData(
        icon: Icons.manage_accounts,
        title: 'Account',
        subtitle: 'Sign-in and security',
        entries: accountEntries,
      ),
      if (isAdmin)
        _SettingsSectionData(
          icon: Icons.admin_panel_settings,
          title: 'Administration',
          subtitle: 'Server settings, users, libraries',
          entries: const [],
          badgeCount: adminBadgeCount,
          onTap: () => context.push(Destinations.admin),
        ),
      _SettingsSectionData(
        icon: Icons.brush,
        title: 'Customization',
        subtitle: 'Theme and layout',
        entries: customizationEntries,
        onTap: () => context.push(Destinations.settingsCustomization),
      ),
      _SettingsSectionData(
        icon: Icons.play_circle,
        title: 'Playback',
        subtitle: 'Video and subtitles',
        entries: playbackEntries,
      ),
      _SettingsSectionData(
        iconBuilder: (size, color) => Image.asset(
          'assets/icons/moonfin.png',
          width: size,
          height: size,
          color: color,
          fit: BoxFit.contain,
        ),
        title: 'Integrations',
        subtitle: 'Plugin and requests',
        entries: moonfinEntries,
      ),
    ];

    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(12, 12, 12, 24),
        children: [
          Container(
            margin: const EdgeInsets.only(bottom: 14),
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(20),
              gradient: LinearGradient(
                colors: [
                  theme.colorScheme.primaryContainer,
                  theme.colorScheme.secondaryContainer,
                ],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
            ),
            child: Row(
              children: [
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: theme.colorScheme.surface.withValues(alpha: 0.45),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: const Icon(Icons.tune),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Customize account, playback, and interface behavior',
                    style: theme.textTheme.bodySmall,
                  ),
                ),
              ],
            ),
          ),
          LayoutBuilder(
            builder: (context, constraints) {
              final width = constraints.maxWidth;
              final columns = width >= 1500
                  ? 5
                  : width >= 1180
                  ? 4
                  : width >= 860
                  ? 3
                  : width >= 360
                  ? 2
                  : 1;
              final cardWidth = (width - (columns - 1) * 10) / columns;
              final cardScale = columns >= 4
                  ? 0.64
                  : columns == 3
                  ? 0.72
                  : 0.82;
              final cardHeight = (cardWidth * cardScale).clamp(136.0, 196.0);
              final compactCard = cardHeight < 164;

              return GridView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: sections.length,
                gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: columns,
                  mainAxisSpacing: 10,
                  crossAxisSpacing: 10,
                  mainAxisExtent: cardHeight,
                ),
                itemBuilder: (context, index) {
                  final section = sections[index];
                  return _SettingsSectionCard(
                    section: section,
                    compact: compactCard,
                    onTap: () {
                      if (section.onTap != null) {
                        section.onTap!();
                      } else {
                        Navigator.of(context).push(
                          MaterialPageRoute<void>(
                            builder: (_) =>
                                _SectionDetailScreen(section: section),
                          ),
                        );
                      }
                    },
                  );
                },
              );
            },
          ),
          const SizedBox(height: 16),
          Padding(
            padding: const EdgeInsets.fromLTRB(4, 0, 4, 8),
            child: Text(
              'Other',
              style: theme.textTheme.titleMedium?.copyWith(
                color: theme.colorScheme.primary,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
          _SettingsListCard(entries: otherEntries),
        ],
      ),
    );
  }
}

class _SettingsSectionData {
  final IconData? icon;
  final Widget Function(double size, Color color)? iconBuilder;
  final String title;
  final String subtitle;
  final List<_SettingsEntry> entries;
  final VoidCallback? onTap;
  final int badgeCount;

  const _SettingsSectionData({
    this.icon,
    this.iconBuilder,
    required this.title,
    required this.subtitle,
    required this.entries,
    this.onTap,
    this.badgeCount = 0,
  });
}

class _SettingsSectionCard extends StatefulWidget {
  final _SettingsSectionData section;
  final bool compact;
  final VoidCallback onTap;

  const _SettingsSectionCard({
    required this.section,
    this.compact = false,
    required this.onTap,
  });

  @override
  State<_SettingsSectionCard> createState() => _SettingsSectionCardState();
}

class _SettingsSectionCardState extends State<_SettingsSectionCard> {
  bool _focused = false;
  bool _hovered = false;

  bool get _highlighted => _focused || _hovered;

  KeyEventResult _handleKey(FocusNode node, KeyEvent event) {
    if (event is KeyDownEvent &&
        (event.logicalKey == LogicalKeyboardKey.select ||
            event.logicalKey == LogicalKeyboardKey.enter ||
            event.logicalKey == LogicalKeyboardKey.gameButtonA)) {
      widget.onTap();
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final focusColor = PlatformDetection.useLeanbackUi
        ? const Color(0xFF00C8FF)
        : Color(
            GetIt.instance<UserPreferences>()
                .get(UserPreferences.focusColor)
                .colorValue,
          );
    final cardPadding = widget.compact
        ? const EdgeInsets.fromLTRB(10, 9, 10, 8)
        : const EdgeInsets.fromLTRB(11, 11, 11, 9);
    final iconBoxSize = widget.compact ? 42.0 : 48.0;
    final iconSize = widget.compact ? 20.0 : 22.0;
    final iconRadius = widget.compact ? 12.0 : 14.0;
    final titleStyle = widget.compact
        ? theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)
        : theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700);
    final subtitleStyle = widget.compact
        ? theme.textTheme.bodySmall
        : theme.textTheme.bodyMedium;
    final optionsStyle = widget.compact
        ? theme.textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w700)
        : theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700);
    final arrowSize = widget.compact ? 24.0 : 26.0;

    return Semantics(
      button: true,
      label: widget.section.title,
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => setState(() => _hovered = true),
        onExit: (_) => setState(() => _hovered = false),
        child: Focus(
          onFocusChange: (value) => setState(() => _focused = value),
          onKeyEvent: _handleKey,
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: widget.onTap,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 100),
              curve: Curves.easeOut,
              padding: cardPadding,
              decoration: BoxDecoration(
                color: _highlighted
                    ? focusColor.withValues(alpha: 0.38)
                    : theme.colorScheme.surfaceContainerLow,
                borderRadius: BorderRadius.circular(20),
                border: Border.all(
                  color: _highlighted
                      ? focusColor
                      : theme.colorScheme.outlineVariant.withValues(
                          alpha: 0.65,
                        ),
                  width: _highlighted ? 4 : 1,
                ),
                boxShadow: _highlighted
                    ? [
                        BoxShadow(
                          color: focusColor.withValues(alpha: 0.4),
                          blurRadius: 18,
                          spreadRadius: 2,
                        ),
                      ]
                    : const [],
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: iconBoxSize,
                    height: iconBoxSize,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primary.withValues(alpha: 0.2),
                      borderRadius: BorderRadius.circular(iconRadius),
                    ),
                    child: Stack(
                      clipBehavior: Clip.none,
                      children: [
                        Center(
                          child: widget.section.iconBuilder != null
                              ? widget.section.iconBuilder!(
                                  iconSize,
                                  Colors.white,
                                )
                              : Icon(widget.section.icon, size: iconSize),
                        ),
                        if (widget.section.badgeCount > 0)
                          Positioned(
                            top: -4,
                            right: -4,
                            child: Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 5,
                                vertical: 1,
                              ),
                              decoration: BoxDecoration(
                                color: theme.colorScheme.error,
                                borderRadius: BorderRadius.circular(10),
                              ),
                              child: Text(
                                widget.section.badgeCount > 9
                                    ? '9+'
                                    : '${widget.section.badgeCount}',
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: theme.colorScheme.onError,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    widget.section.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: titleStyle,
                  ),
                  const SizedBox(height: 3),
                  Text(
                    widget.section.subtitle,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: subtitleStyle,
                  ),
                  const Spacer(),
                  Row(
                    children: [
                      if (widget.section.entries.isNotEmpty)
                        Text(
                          '${widget.section.entries.length} options',
                          style: optionsStyle,
                        ),
                      const Spacer(),
                      Icon(Icons.arrow_forward, size: arrowSize),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _SectionDetailScreen extends StatelessWidget {
  final _SettingsSectionData section;

  const _SectionDetailScreen({required this.section});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(section.title)),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(12, 12, 12, 24),
        children: [
          Container(
            decoration: BoxDecoration(
              color: theme.colorScheme.surfaceContainerLow,
              borderRadius: BorderRadius.circular(18),
              border: Border.all(
                color: theme.colorScheme.outlineVariant.withValues(alpha: 0.5),
              ),
            ),
            child: Column(
              children: [
                for (var i = 0; i < section.entries.length; i++) ...[
                  _SettingsEntryTile(entry: section.entries[i]),
                  if (i != section.entries.length - 1)
                    Divider(
                      height: 1,
                      indent: 70,
                      endIndent: 12,
                      color: theme.colorScheme.outlineVariant.withValues(
                        alpha: 0.35,
                      ),
                    ),
                ],
                const SizedBox(height: 6),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SettingsListCard extends StatelessWidget {
  final List<_SettingsEntry> entries;

  const _SettingsListCard({required this.entries});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: theme.colorScheme.outlineVariant.withValues(alpha: 0.5),
        ),
      ),
      child: Column(
        children: [
          for (var i = 0; i < entries.length; i++) ...[
            _SettingsEntryTile(entry: entries[i]),
            if (i != entries.length - 1)
              Divider(
                height: 1,
                indent: 70,
                endIndent: 12,
                color: theme.colorScheme.outlineVariant.withValues(alpha: 0.35),
              ),
          ],
          const SizedBox(height: 6),
        ],
      ),
    );
  }
}

class _SettingsEntry {
  final IconData? icon;
  final Widget Function(double size, Color color)? iconBuilder;
  final String title;
  final String? subtitle;
  final VoidCallback onTap;

  const _SettingsEntry({
    this.icon,
    this.iconBuilder,
    required this.title,
    required this.onTap,
    this.subtitle,
  });
}

class _SettingsEntryTile extends StatefulWidget {
  final _SettingsEntry entry;

  const _SettingsEntryTile({required this.entry});

  @override
  State<_SettingsEntryTile> createState() => _SettingsEntryTileState();
}

class _SettingsEntryTileState extends State<_SettingsEntryTile> {
  bool _focused = false;
  bool _hovered = false;

  KeyEventResult _handleKey(FocusNode node, KeyEvent event) {
    if (event is KeyDownEvent &&
        (event.logicalKey == LogicalKeyboardKey.select ||
            event.logicalKey == LogicalKeyboardKey.enter ||
            event.logicalKey == LogicalKeyboardKey.gameButtonA)) {
      widget.entry.onTap();
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final highlighted = _focused || _hovered;
    final focusColor = PlatformDetection.useLeanbackUi
        ? const Color(0xFF00C8FF)
        : Color(
            GetIt.instance<UserPreferences>()
                .get(UserPreferences.focusColor)
                .colorValue,
          );
    return Semantics(
      button: true,
      label: widget.entry.title,
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => setState(() => _hovered = true),
        onExit: (_) => setState(() => _hovered = false),
        child: Focus(
          onFocusChange: (value) => setState(() => _focused = value),
          onKeyEvent: _handleKey,
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: widget.entry.onTap,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 100),
              margin: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
              decoration: BoxDecoration(
                color: highlighted
                    ? focusColor.withValues(alpha: 0.36)
                    : Colors.transparent,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: highlighted ? focusColor : Colors.transparent,
                  width: highlighted ? 4 : 1,
                ),
              ),
              child: ListTile(
                minLeadingWidth: 40,
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 2,
                ),
                leading: Container(
                  width: 36,
                  height: 36,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(10),
                    color: theme.colorScheme.primaryContainer.withValues(
                      alpha: 0.45,
                    ),
                  ),
                  child: widget.entry.iconBuilder != null
                      ? widget.entry.iconBuilder!(20, Colors.white)
                      : Icon(widget.entry.icon, size: 20),
                ),
                title: Text(
                  widget.entry.title,
                  style: theme.textTheme.titleSmall?.copyWith(
                    fontWeight: highlighted ? FontWeight.w700 : null,
                  ),
                ),
                subtitle: widget.entry.subtitle != null
                    ? Text(
                        widget.entry.subtitle!,
                        style: theme.textTheme.bodySmall,
                      )
                    : null,
                trailing: const Icon(Icons.chevron_right),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
