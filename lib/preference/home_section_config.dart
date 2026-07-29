import 'dart:convert';
import 'preference_constants.dart';

class HomeSectionConfig {
  final HomeSectionType type;
  final bool enabled;
  final int order;

  const HomeSectionConfig({
    required this.type,
    this.enabled = true,
    this.order = 0,
  });

  factory HomeSectionConfig.fromJson(Map<String, dynamic> json) {
    final typeName = json['type'] as String? ?? 'none';
    return HomeSectionConfig(
      type: HomeSectionType.fromSerialized(typeName),
      enabled: json['enabled'] as bool? ?? true,
      order: json['order'] as int? ?? 0,
    );
  }

  Map<String, dynamic> toJson() => {
        'type': type.serializedName,
        'enabled': enabled,
        'order': order,
      };

  HomeSectionConfig copyWith({
    HomeSectionType? type,
    bool? enabled,
    int? order,
  }) =>
      HomeSectionConfig(
        type: type ?? this.type,
        enabled: enabled ?? this.enabled,
        order: order ?? this.order,
      );

  static List<HomeSectionConfig> defaults() => const [
      HomeSectionConfig(type: HomeSectionType.libraryTilesSmall, enabled: true, order: 0),
      HomeSectionConfig(type: HomeSectionType.resume, enabled: true, order: 1),
      HomeSectionConfig(type: HomeSectionType.nextUp, enabled: true, order: 2),
      HomeSectionConfig(type: HomeSectionType.latestMedia, enabled: true, order: 3),
        HomeSectionConfig(type: HomeSectionType.recentlyReleased, enabled: false, order: 4),
      HomeSectionConfig(type: HomeSectionType.liveTv, enabled: false, order: 5),
        HomeSectionConfig(type: HomeSectionType.libraryButtons, enabled: false, order: 6),
        HomeSectionConfig(type: HomeSectionType.resumeAudio, enabled: false, order: 7),
        HomeSectionConfig(type: HomeSectionType.resumeBook, enabled: false, order: 8),
        HomeSectionConfig(type: HomeSectionType.activeRecordings, enabled: false, order: 9),
        HomeSectionConfig(type: HomeSectionType.playlists, enabled: false, order: 10),
      ];

  static List<HomeSectionConfig> fromJsonString(String jsonString) {
    if (jsonString.isEmpty) return defaults();
    try {
      final list = jsonDecode(jsonString) as List;
      return list
          .map((e) => HomeSectionConfig.fromJson(e as Map<String, dynamic>))
          .toList();
    } catch (_) {
      return defaults();
    }
  }

  static String toJsonString(List<HomeSectionConfig> configs) =>
      jsonEncode(configs.map((c) => c.toJson()).toList());
}
