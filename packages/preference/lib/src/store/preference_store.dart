import 'package:shared_preferences/shared_preferences.dart';

import '../preference_base.dart';
import '../preference_enum.dart';
import '../migration/migration_context.dart';

class PreferenceStore {
  SharedPreferences? _prefs;

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
  }

  SharedPreferences get _requirePrefs {
    if (_prefs == null) {
      throw StateError('PreferenceStore not initialized. Call init() first.');
    }
    return _prefs!;
  }

  T get<T>(Preference<T> preference) {
    final key = preference.key;
    final defaultValue = preference.defaultValue;

    if (preference is EnumPreference) {
      return _getEnumDynamic(preference as EnumPreference) as T;
    }

    switch (defaultValue) {
      case int _:
        return (_requirePrefs.getInt(key) ?? defaultValue) as T;
      case double _:
        return (_requirePrefs.getDouble(key) ?? defaultValue) as T;
      case bool _:
        return (_requirePrefs.getBool(key) ?? defaultValue) as T;
      case String _:
        return (_requirePrefs.getString(key) ?? defaultValue) as T;
      case List<String> _:
        return (_requirePrefs.getStringList(key) ?? defaultValue) as T;
      default:
        throw ArgumentError('Unsupported preference type: ${defaultValue.runtimeType}');
    }
  }

  Future<void> set<T>(Preference<T> preference, T value) async {
    final key = preference.key;

    if (preference is EnumPreference) {
      await _requirePrefs.setString(key, (value as Enum).name);
      return;
    }

    switch (value) {
      case int v:
        await _requirePrefs.setInt(key, v);
      case double v:
        await _requirePrefs.setDouble(key, v);
      case bool v:
        await _requirePrefs.setBool(key, v);
      case String v:
        await _requirePrefs.setString(key, v);
      case List<String> v:
        await _requirePrefs.setStringList(key, v);
      default:
        throw ArgumentError('Unsupported preference type: ${value.runtimeType}');
    }
  }

  Future<void> delete<T>(Preference<T> preference) =>
      _requirePrefs.remove(preference.key);

  Future<void> reset<T>(Preference<T> preference) => set(preference, preference.defaultValue);

  dynamic _getEnumDynamic(EnumPreference<dynamic> preference) {
    final stored = _requirePrefs.getString(preference.key);
    if (stored == null || stored.isEmpty) return preference.defaultValue;
    for (final v in preference.values) {
      if ((v as Enum).name == stored) return v;
    }
    return preference.defaultValue;
  }

  String? getString(String key) => _requirePrefs.getString(key);
  Future<bool> setString(String key, String value) =>
      _requirePrefs.setString(key, value);

  int? getInt(String key) => _requirePrefs.getInt(key);
  Future<bool> setInt(String key, int value) =>
      _requirePrefs.setInt(key, value);

  bool? getBool(String key) => _requirePrefs.getBool(key);
  Future<bool> setBool(String key, bool value) =>
      _requirePrefs.setBool(key, value);

  double? getDouble(String key) => _requirePrefs.getDouble(key);
  Future<bool> setDouble(String key, double value) =>
      _requirePrefs.setDouble(key, value);

  List<String>? getStringList(String key) => _requirePrefs.getStringList(key);
  Future<bool> setStringList(String key, List<String> value) =>
      _requirePrefs.setStringList(key, value);

  bool containsKey(String key) => _requirePrefs.containsKey(key);

  Future<bool> remove(String key) => _requirePrefs.remove(key);
  Future<bool> clear() => _requirePrefs.clear();

  static final _versionKey = 'store_version';

  Future<void> runMigrations(
    void Function(MigrationContext context) body,
  ) async {
    final context = MigrationContext();
    body(context);
    final currentVersion = _requirePrefs.getInt(_versionKey) ?? -1;
    final newVersion = await context.applyMigrations(currentVersion, this);
    await _requirePrefs.setInt(_versionKey, newVersion);
  }
}
