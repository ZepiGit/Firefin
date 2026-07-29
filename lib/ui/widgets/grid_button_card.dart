import 'package:flutter/material.dart';
import 'package:jellyfin_design/jellyfin_design.dart';
import '../mixins/focus_state_mixin.dart';

class GridButtonCard extends StatefulWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final double width;
  final double height;
  final Color? focusColor;
  final bool cardFocusExpansion;

  const GridButtonCard({
    super.key,
    required this.icon,
    required this.label,
    required this.onTap,
    this.width = 160,
    this.height = 120,
    this.focusColor,
    this.cardFocusExpansion = true,
  });

  @override
  State<GridButtonCard> createState() => _GridButtonCardState();
}

class _GridButtonCardState extends State<GridButtonCard> with FocusStateMixin {

  @override
  Widget build(BuildContext context) {
    final focusedBackground = widget.focusColor ?? AppColorScheme.buttonFocused;
    final focusedForeground =
      ThemeData.estimateBrightnessForColor(focusedBackground) == Brightness.dark
        ? Colors.white
        : Colors.black;
    final color = showFocusBorder
      ? focusedBackground
      : AppColorScheme.buttonNormal;
    final foregroundColor = showFocusBorder
      ? focusedForeground
      : AppColorScheme.onButtonNormal;
    final scale = widget.cardFocusExpansion && showFocusBorder ? 1.05 : 1.0;

    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setHovered(true),
      onExit: (_) => setHovered(false),
      child: Focus(
        onFocusChange: (f) => setFocused(f),
        child: GestureDetector(
          onTap: widget.onTap,
          child: AnimatedScale(
            scale: scale,
            duration: const Duration(milliseconds: 150),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              width: widget.width,
              height: widget.height,
              decoration: BoxDecoration(
                color: color,
                borderRadius: BorderRadius.circular(AppSpacing.spaceSm),
                border: showFocusBorder
                    ? Border.all(
                        color: foregroundColor.withValues(alpha: 0.85),
                        width: 2,
                      )
                    : null,
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    widget.icon,
                    size: 36,
                    color: foregroundColor,
                  ),
                  const SizedBox(height: AppSpacing.spaceSm),
                  Text(
                    widget.label,
                    style: TextStyle(
                      color: foregroundColor,
                      fontSize: 14,
                      fontWeight: FontWeight.w500,
                    ),
                    textAlign: TextAlign.center,
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
