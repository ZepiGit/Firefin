abstract class AdminLiveTvApi {
  Future<List<Map<String, dynamic>>> getTunerHosts();
  Future<Map<String, dynamic>> addTunerHost(Map<String, dynamic> tunerInfo);
  Future<void> removeTunerHost(String id);
  Future<void> resetTuner(String tunerId);
  Future<List<Map<String, dynamic>>> discoverTuners();

  Future<List<Map<String, dynamic>>> getListingProviders();
  Future<Map<String, dynamic>> addListingProvider(Map<String, dynamic> providerInfo);
  Future<void> removeListingProvider(String id);

  Future<void> setChannelMappings(Map<String, dynamic> mappings);

  Future<Map<String, dynamic>> getLiveTvConfiguration();
  Future<void> updateLiveTvConfiguration(Map<String, dynamic> config);
}
