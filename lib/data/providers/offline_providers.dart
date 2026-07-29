import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:get_it/get_it.dart';

import '../database/offline_database.dart';
import '../repositories/offline_repository.dart';

OfflineRepository get _repo => GetIt.instance<OfflineRepository>();

final downloadedMoviesProvider = StreamProvider<List<DownloadedItem>>((ref) {
  return _repo.watchItems(type: 'Movie', onlyCompleted: true);
});

final downloadedSeriesProvider = StreamProvider<List<DownloadedItem>>((ref) {
  return _repo.watchDownloadedSeries();
});

final downloadedAudioProvider = StreamProvider<List<DownloadedItem>>((ref) {
  return _repo.watchItems(type: 'Audio', onlyCompleted: true);
});

final downloadedAudioBooksProvider = StreamProvider<List<DownloadedItem>>((ref) {
  return _repo.watchItems(type: 'AudioBook', onlyCompleted: true);
});

final downloadedBooksProvider = StreamProvider<List<DownloadedItem>>((ref) {
  return _repo.watchItems(type: 'Book', onlyCompleted: true);
});

final downloadedEpisodesProvider =
    StreamProvider.family<List<DownloadedItem>, String>((ref, seriesId) {
  return _repo.watchSeriesEpisodes(seriesId);
});

final downloadedSeasonEpisodesProvider =
    StreamProvider.family<List<DownloadedItem>, String>((ref, seasonId) {
  return _repo.watchSeasonEpisodes(seasonId);
});

final storageUsedProvider = StreamProvider<int>((ref) {
  return _repo.watchTotalStorageUsed();
});

final downloadedItemProvider =
    StreamProvider.family<DownloadedItem?, String>((ref, itemId) {
  return _repo.watchItem(itemId);
});
