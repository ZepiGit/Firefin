import 'package:flutter/material.dart';
import 'package:get_it/get_it.dart';
import 'package:moonfin/preference/user_preferences.dart';

mixin FocusStateMixin<T extends StatefulWidget> on State<T> {
  bool _focused = false;
  bool _hovered = false;

  bool get focused => _focused;
  bool get hovered => _hovered;
  bool get showFocusBorder => _hovered || _focused;
  
  Color get focusColor => Color(
    GetIt.instance<UserPreferences>().get(UserPreferences.focusColor).colorValue,
  );

  bool get cardFocusExpansion =>
      GetIt.instance<UserPreferences>().get(UserPreferences.cardFocusExpansion);

  void setFocused(bool value) => setState(() => _focused = value);
  void setHovered(bool value) => setState(() => _hovered = value);
}
