import 'dart:convert';

import 'package:drift/drift.dart';

part 'offline_database.g.dart';

extension DownloadedItemMetadata on DownloadedItem {
  Map<String, dynamic> get parsedMetadata {
    try {
      final raw = jsonDecode(metadataJson);
      if (raw is Map<String, dynamic>) return raw;
    } catch (_) {}
    return const {};
  }
}

class DownloadedItems extends Table {
  TextColumn get itemId => text()();
  TextColumn get serverId => text()();
  TextColumn get type => text()();
  TextColumn get name => text()();
  TextColumn get localFilePath => text().nullable()();
  TextColumn get metadataJson => text()();
  TextColumn get posterPath => text().nullable()();
  TextColumn get backdropPath => text().nullable()();
  TextColumn get logoPath => text().nullable()();
  TextColumn get thumbPath => text().nullable()();
  IntColumn get downloadStatus => integer()();
  RealColumn get downloadProgress => real().withDefault(const Constant(0.0))();
  TextColumn get errorMessage => text().nullable()();
  IntColumn get fileSizeBytes => integer().withDefault(const Constant(0))();
  IntColumn get playbackPositionTicks => integer().withDefault(const Constant(0))();
  BoolColumn get progressSynced => boolean().withDefault(const Constant(true))();
  DateTimeColumn get downloadedAt => dateTime().nullable()();
  TextColumn get qualityPreset => text().withDefault(const Constant('original'))();
  TextColumn get seriesId => text().nullable()();
  TextColumn get seasonId => text().nullable()();
  TextColumn get seriesName => text().nullable()();
  TextColumn get seasonName => text().nullable()();
  IntColumn get indexNumber => integer().nullable()();
  IntColumn get parentIndexNumber => integer().nullable()();

  @override
  Set<Column> get primaryKey => {itemId, serverId};
}

@DriftDatabase(tables: [DownloadedItems])
class OfflineDatabase extends _$OfflineDatabase {
  OfflineDatabase(super.e);

  @override
  int get schemaVersion => 1;

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (m) => m.createAll(),
    onUpgrade: (m, from, to) async {},
  );
}
