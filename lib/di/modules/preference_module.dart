import 'package:get_it/get_it.dart';
import 'package:jellyfin_preference/jellyfin_preference.dart';

import '../../preference/user_preferences.dart';

final _getIt = GetIt.instance;

void registerPreferenceModule(PreferenceStore store) {
  _getIt.registerSingleton(store);
  _getIt.registerLazySingleton(() => UserPreferences(store));
}
