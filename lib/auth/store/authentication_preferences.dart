import 'package:jellyfin_preference/jellyfin_preference.dart';

import '../../preference/preference_constants.dart';

class AuthenticationPreferences {
  final PreferenceStore _store;

  AuthenticationPreferences(this._store);

  static final autoLoginUserBehavior = EnumPreference(
    key: 'pref_auto_login_behavior',
    defaultValue: UserSelectBehavior.lastUser,
    values: UserSelectBehavior.values,
  );

  static final autoLoginServerId = Preference(
    key: 'pref_auto_login_server_id',
    defaultValue: '',
  );

  static final autoLoginUserId = Preference(
    key: 'pref_auto_login_user_id',
    defaultValue: '',
  );

  static final lastServerId = Preference(
    key: 'pref_last_server_id',
    defaultValue: '',
  );

  static final lastUserId = Preference(
    key: 'pref_last_user_id',
    defaultValue: '',
  );

  static final alwaysAuthenticate = Preference(
    key: 'pref_always_authenticate',
    defaultValue: false,
  );

  static final sortBy = EnumPreference(
    key: 'pref_user_sort_by',
    defaultValue: UserSortBy.lastUsed,
    values: UserSortBy.values,
  );

  UserSelectBehavior get loginBehavior =>
      _store.get(autoLoginUserBehavior);

  String get savedAutoLoginServerId => _store.get(autoLoginServerId);
  String get savedAutoLoginUserId => _store.get(autoLoginUserId);
  String get savedLastServerId => _store.get(lastServerId);
  String get savedLastUserId => _store.get(lastUserId);
  bool get shouldAlwaysAuthenticate => _store.get(alwaysAuthenticate);
  UserSortBy get userSortBy => _store.get(sortBy);

  Future<void> setLastServerId(String id) =>
      _store.set(lastServerId, id);

  Future<void> setLastUserId(String id) =>
      _store.set(lastUserId, id);

  Future<void> setAutoLogin(String serverId, String userId) async {
    await _store.set(autoLoginServerId, serverId);
    await _store.set(autoLoginUserId, userId);
  }

  Future<void> clearAutoLogin() async {
    await _store.set(autoLoginServerId, '');
    await _store.set(autoLoginUserId, '');
  }
}

enum UserSortBy {
  lastUsed,
  alphabetical,
}
