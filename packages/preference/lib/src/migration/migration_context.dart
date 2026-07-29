import '../store/preference_store.dart';

typedef MigrationBody = Future<void> Function(PreferenceStore store);

class _Migration {
  final int toVersion;
  final MigrationBody body;
  const _Migration(this.toVersion, this.body);
}

class MigrationContext {
  final List<_Migration> _migrations = [];
  int _highVersion = -1;

  void migration(int toVersion, MigrationBody body) {
    if (toVersion > _highVersion) _highVersion = toVersion;
    _migrations.add(_Migration(toVersion, body));
  }

  Future<int> applyMigrations(int currentVersion, PreferenceStore store) async {
    if (_highVersion == -1) return currentVersion;
    if (currentVersion >= _highVersion) return currentVersion;
    if (currentVersion == -1) return _highVersion;

    final pending = _migrations
        .where((m) => m.toVersion > currentVersion)
        .toList()
      ..sort((a, b) => a.toVersion.compareTo(b.toVersion));

    for (final m in pending) {
      await m.body(store);
    }
    return _highVersion;
  }
}
