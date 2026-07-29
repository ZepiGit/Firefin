import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get_it/get_it.dart';
import 'package:jellyfin_preference/jellyfin_preference.dart';

import '../../../preference/user_preferences.dart';
import '../../../util/platform_detection.dart';
import 'preference_binding.dart';

class SwitchPreferenceTile extends StatefulWidget {
  final Preference<bool> preference;
  final String title;
  final String? subtitle;
  final IconData? icon;
  final Widget Function(double size, Color color)? iconBuilder;
  final VoidCallback? onChanged;
  final bool enabled;

  const SwitchPreferenceTile({
    super.key,
    required this.preference,
    required this.title,
    this.subtitle,
    this.icon,
    this.iconBuilder,
    this.onChanged,
    this.enabled = true,
  });

  @override
  State<SwitchPreferenceTile> createState() => _SwitchPreferenceTileState();
}

class _SwitchPreferenceTileState extends State<SwitchPreferenceTile> {
  late final PreferenceBinding<bool> _binding;

  @override
  void initState() {
    super.initState();
    _binding = PreferenceBinding(
      GetIt.instance<PreferenceStore>(),
      widget.preference,
    );
  }

  @override
  void dispose() {
    _binding.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<bool>(
      valueListenable: _binding,
      builder: (context, value, _) {
        void update(bool nextValue) {
          if (!widget.enabled) return;
          _binding.value = nextValue;
          widget.onChanged?.call();
        }

        final secondary = widget.iconBuilder != null
            ? widget.iconBuilder!(
                24,
                Theme.of(context).iconTheme.color ?? Colors.white,
              )
            : widget.icon != null
            ? Icon(widget.icon)
            : null;

        if (!PlatformDetection.useLeanbackUi) {
          return SwitchListTile(
            secondary: secondary,
            title: Text(widget.title),
            subtitle: widget.subtitle != null ? Text(widget.subtitle!) : null,
            value: value,
            onChanged: widget.enabled ? update : null,
          );
        }

        return _TvPreferenceSurface(
          enabled: widget.enabled,
          onActivate: () => update(!value),
          semanticLabel: widget.title,
          child: ListTile(
            enabled: widget.enabled,
            leading: secondary,
            title: Text(widget.title),
            subtitle: widget.subtitle != null ? Text(widget.subtitle!) : null,
            trailing: ExcludeFocus(
              child: IgnorePointer(
                child: Switch(
                  value: value,
                  onChanged: widget.enabled ? (_) {} : null,
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}

class EnumPreferenceTile<T extends Enum> extends StatefulWidget {
  final EnumPreference<T> preference;
  final String title;
  final IconData? icon;
  final String Function(T value) labelOf;

  const EnumPreferenceTile({
    super.key,
    required this.preference,
    required this.title,
    required this.labelOf,
    this.icon,
  });

  @override
  State<EnumPreferenceTile<T>> createState() => _EnumPreferenceTileState<T>();
}

class _EnumPreferenceTileState<T extends Enum>
    extends State<EnumPreferenceTile<T>> {
  late final PreferenceBinding<T> _binding;

  @override
  void initState() {
    super.initState();
    _binding = PreferenceBinding(
      GetIt.instance<PreferenceStore>(),
      widget.preference,
    );
  }

  @override
  void dispose() {
    _binding.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<T>(
      valueListenable: _binding,
      builder: (context, value, _) {
        final tile = ListTile(
          leading: widget.icon != null ? Icon(widget.icon) : null,
          title: Text(widget.title),
          subtitle: Text(widget.labelOf(value)),
          trailing: PlatformDetection.useLeanbackUi
              ? const Icon(Icons.chevron_right)
              : null,
          onTap: PlatformDetection.useLeanbackUi
              ? null
              : () => _showPicker(context, value),
        );

        if (!PlatformDetection.useLeanbackUi) return tile;
        return _TvPreferenceSurface(
          onActivate: () => _showPicker(context, value),
          semanticLabel: widget.title,
          child: tile,
        );
      },
    );
  }

  void _showPicker(BuildContext context, T current) {
    showDialog(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: Text(widget.title),
        children: widget.preference.values.map((v) {
          void select() {
            _binding.value = v;
            Navigator.pop(ctx);
          }

          if (PlatformDetection.useLeanbackUi) {
            return _TvPreferenceSurface(
              onActivate: select,
              semanticLabel: widget.labelOf(v),
              child: ListTile(
                title: Text(widget.labelOf(v)),
                trailing: Icon(
                  v == current
                      ? Icons.radio_button_checked
                      : Icons.radio_button_unchecked,
                ),
              ),
            );
          }

          return RadioListTile<T>(
            title: Text(widget.labelOf(v)),
            value: v,
            groupValue: current,
            onChanged: (_) => select(),
          );
        }).toList(),
      ),
    );
  }
}

class SliderPreferenceTile extends StatefulWidget {
  final Preference<int> preference;
  final String title;
  final IconData? icon;
  final double min;
  final double max;
  final int? divisions;
  final String Function(int value)? labelOf;
  final VoidCallback? onChangeEnd;

  const SliderPreferenceTile({
    super.key,
    required this.preference,
    required this.title,
    this.icon,
    required this.min,
    required this.max,
    this.divisions,
    this.labelOf,
    this.onChangeEnd,
  });

  @override
  State<SliderPreferenceTile> createState() => _SliderPreferenceTileState();
}

class _SliderPreferenceTileState extends State<SliderPreferenceTile> {
  late final PreferenceBinding<int> _binding;

  @override
  void initState() {
    super.initState();
    _binding = PreferenceBinding(
      GetIt.instance<PreferenceStore>(),
      widget.preference,
    );
  }

  @override
  void dispose() {
    _binding.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<int>(
      valueListenable: _binding,
      builder: (context, value, _) {
        final slider = Slider(
          value: value.toDouble().clamp(widget.min, widget.max),
          min: widget.min,
          max: widget.max,
          divisions: widget.divisions,
          label: widget.labelOf?.call(value) ?? value.toString(),
          onChanged: (v) => _binding.value = v.round(),
          onChangeEnd: (_) => widget.onChangeEnd?.call(),
        );
        final tile = ListTile(
          leading: widget.icon != null ? Icon(widget.icon) : null,
          title: Text(widget.title),
          subtitle: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (widget.labelOf != null) Text(widget.labelOf!(value)),
              if (PlatformDetection.useLeanbackUi)
                ExcludeFocus(child: IgnorePointer(child: slider))
              else
                slider,
            ],
          ),
        );

        if (!PlatformDetection.useLeanbackUi) return tile;

        void changeBy(int direction) {
          final step = widget.divisions != null && widget.divisions! > 0
              ? (widget.max - widget.min) / widget.divisions!
              : 1.0;
          final next = (value + direction * step)
              .round()
              .clamp(widget.min.round(), widget.max.round())
              .toInt();
          if (next == value) return;
          _binding.value = next;
          widget.onChangeEnd?.call();
        }

        return _TvPreferenceSurface(
          onLeft: () => changeBy(-1),
          onRight: () => changeBy(1),
          semanticLabel: widget.title,
          child: tile,
        );
      },
    );
  }
}

class DoubleSliderPreferenceTile extends StatefulWidget {
  final Preference<double> preference;
  final String title;
  final IconData? icon;
  final double min;
  final double max;
  final int? divisions;
  final String Function(double value)? labelOf;
  final VoidCallback? onChangeEnd;

  const DoubleSliderPreferenceTile({
    super.key,
    required this.preference,
    required this.title,
    this.icon,
    required this.min,
    required this.max,
    this.divisions,
    this.labelOf,
    this.onChangeEnd,
  });

  @override
  State<DoubleSliderPreferenceTile> createState() =>
      _DoubleSliderPreferenceTileState();
}

class _DoubleSliderPreferenceTileState
    extends State<DoubleSliderPreferenceTile> {
  late final PreferenceBinding<double> _binding;

  @override
  void initState() {
    super.initState();
    _binding = PreferenceBinding(
      GetIt.instance<PreferenceStore>(),
      widget.preference,
    );
  }

  @override
  void dispose() {
    _binding.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<double>(
      valueListenable: _binding,
      builder: (context, value, _) {
        final slider = Slider(
          value: value.clamp(widget.min, widget.max),
          min: widget.min,
          max: widget.max,
          divisions: widget.divisions,
          label: widget.labelOf?.call(value) ?? value.toString(),
          onChanged: (next) => _binding.value = next,
          onChangeEnd: (_) => widget.onChangeEnd?.call(),
        );
        final tile = ListTile(
          leading: widget.icon != null ? Icon(widget.icon) : null,
          title: Text(widget.title),
          subtitle: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (widget.labelOf != null) Text(widget.labelOf!(value)),
              if (PlatformDetection.useLeanbackUi)
                ExcludeFocus(child: IgnorePointer(child: slider))
              else
                slider,
            ],
          ),
        );

        if (!PlatformDetection.useLeanbackUi) return tile;

        void changeBy(int direction) {
          final step = widget.divisions != null && widget.divisions! > 0
              ? (widget.max - widget.min) / widget.divisions!
              : 1.0;
          final next = (value + direction * step).clamp(widget.min, widget.max);
          if ((next - value).abs() < 0.000001) return;
          _binding.value = next;
          widget.onChangeEnd?.call();
        }

        return _TvPreferenceSurface(
          onLeft: () => changeBy(-1),
          onRight: () => changeBy(1),
          semanticLabel: widget.title,
          child: tile,
        );
      },
    );
  }
}

class StringPickerPreferenceTile extends StatefulWidget {
  final Preference<String> preference;
  final String title;
  final IconData? icon;
  final Map<String, String> options;
  final VoidCallback? onChanged;

  const StringPickerPreferenceTile({
    super.key,
    required this.preference,
    required this.title,
    this.icon,
    required this.options,
    this.onChanged,
  });

  @override
  State<StringPickerPreferenceTile> createState() =>
      _StringPickerPreferenceTileState();
}

class _StringPickerPreferenceTileState
    extends State<StringPickerPreferenceTile> {
  late final PreferenceBinding<String> _binding;

  @override
  void initState() {
    super.initState();
    _binding = PreferenceBinding(
      GetIt.instance<PreferenceStore>(),
      widget.preference,
    );
  }

  @override
  void dispose() {
    _binding.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<String>(
      valueListenable: _binding,
      builder: (context, value, _) {
        final tile = ListTile(
          leading: widget.icon != null ? Icon(widget.icon) : null,
          title: Text(widget.title),
          subtitle: Text(widget.options[value] ?? value),
          trailing: PlatformDetection.useLeanbackUi
              ? const Icon(Icons.chevron_right)
              : null,
          onTap: PlatformDetection.useLeanbackUi
              ? null
              : () => _showPicker(context, value),
        );

        if (!PlatformDetection.useLeanbackUi) return tile;
        return _TvPreferenceSurface(
          onActivate: () => _showPicker(context, value),
          semanticLabel: widget.title,
          child: tile,
        );
      },
    );
  }

  void _showPicker(BuildContext context, String current) {
    showDialog(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: Text(widget.title),
        children: widget.options.entries.map((e) {
          void select() {
            _binding.value = e.key;
            widget.onChanged?.call();
            Navigator.pop(ctx);
          }

          if (PlatformDetection.useLeanbackUi) {
            return _TvPreferenceSurface(
              onActivate: select,
              semanticLabel: e.value,
              child: ListTile(
                title: Text(e.value),
                trailing: Icon(
                  e.key == current
                      ? Icons.radio_button_checked
                      : Icons.radio_button_unchecked,
                ),
              ),
            );
          }

          return RadioListTile<String>(
            title: Text(e.value),
            value: e.key,
            groupValue: current,
            onChanged: (_) => select(),
          );
        }).toList(),
      ),
    );
  }
}

class _TvPreferenceSurface extends StatefulWidget {
  final Widget child;
  final VoidCallback? onActivate;
  final VoidCallback? onLeft;
  final VoidCallback? onRight;
  final String semanticLabel;
  final bool enabled;

  const _TvPreferenceSurface({
    required this.child,
    required this.semanticLabel,
    this.onActivate,
    this.onLeft,
    this.onRight,
    this.enabled = true,
  });

  @override
  State<_TvPreferenceSurface> createState() => _TvPreferenceSurfaceState();
}

class _TvPreferenceSurfaceState extends State<_TvPreferenceSurface> {
  bool _focused = false;
  bool _hovered = false;

  bool get _highlighted => widget.enabled && (_focused || _hovered);

  KeyEventResult _handleKey(FocusNode node, KeyEvent event) {
    if (!widget.enabled || event is! KeyDownEvent) {
      return KeyEventResult.ignored;
    }

    final key = event.logicalKey;
    if (key == LogicalKeyboardKey.select ||
        key == LogicalKeyboardKey.enter ||
        key == LogicalKeyboardKey.gameButtonA) {
      widget.onActivate?.call();
      return widget.onActivate != null
          ? KeyEventResult.handled
          : KeyEventResult.ignored;
    }
    if (key == LogicalKeyboardKey.arrowLeft && widget.onLeft != null) {
      widget.onLeft!();
      return KeyEventResult.handled;
    }
    if (key == LogicalKeyboardKey.arrowRight && widget.onRight != null) {
      widget.onRight!();
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

    return Semantics(
      button: widget.onActivate != null,
      enabled: widget.enabled,
      label: widget.semanticLabel,
      child: MouseRegion(
        cursor: widget.enabled
            ? SystemMouseCursors.click
            : SystemMouseCursors.basic,
        onEnter: (_) => setState(() => _hovered = true),
        onExit: (_) => setState(() => _hovered = false),
        child: Focus(
          canRequestFocus: widget.enabled,
          onFocusChange: (value) => setState(() => _focused = value),
          onKeyEvent: _handleKey,
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: widget.enabled ? widget.onActivate : null,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 100),
              curve: Curves.easeOut,
              margin: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
              decoration: BoxDecoration(
                color: _highlighted
                    ? focusColor.withValues(alpha: 0.36)
                    : Colors.transparent,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: _highlighted ? focusColor : Colors.transparent,
                  width: _highlighted ? 4 : 1,
                ),
                boxShadow: _highlighted
                    ? [
                        BoxShadow(
                          color: focusColor.withValues(alpha: 0.35),
                          blurRadius: 14,
                          spreadRadius: 2,
                        ),
                      ]
                    : const [],
              ),
              foregroundDecoration: widget.enabled
                  ? null
                  : BoxDecoration(
                      color: theme.colorScheme.surface.withValues(alpha: 0.38),
                      borderRadius: BorderRadius.circular(12),
                    ),
              child: widget.child,
            ),
          ),
        ),
      ),
    );
  }
}
