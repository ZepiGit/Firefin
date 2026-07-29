import 'package:flutter/foundation.dart';
import 'package:get_it/get_it.dart';
import 'package:jellyfin_preference/jellyfin_preference.dart';

import '../../../preference/user_preferences.dart';

class PreferenceBinding<T> extends ValueNotifier<T> {
  final PreferenceStore _store;
  final Preference<T> _preference;

  PreferenceBinding(this._store, this._preference) : super(_store.get(_preference));

  @override
  set value(T newValue) {
    super.value = newValue;
    _store.set(_preference, newValue);
    GetIt.instance<UserPreferences>().notifyPreferenceChanged();
  }

  void reset() {
    value = _preference.defaultValue;
  }
}
