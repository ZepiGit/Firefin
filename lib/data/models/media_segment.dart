enum MediaSegmentType {
  intro,
  outro,
  preview,
  recap,
  commercial,
  unknown;

  static MediaSegmentType fromServerString(String? value) => switch (value) {
    'Intro' => intro,
    'Outro' => outro,
    'Preview' => preview,
    'Recap' => recap,
    'Commercial' => commercial,
    _ => unknown,
  };

  String get displayName => switch (this) {
    intro => 'Intro',
    outro => 'Outro',
    preview => 'Preview',
    recap => 'Recap',
    commercial => 'Commercial',
    unknown => 'Unknown',
  };
}

class MediaSegment {
  final String id;
  final String itemId;
  final MediaSegmentType type;
  final Duration start;
  final Duration end;

  const MediaSegment({
    required this.id,
    required this.itemId,
    required this.type,
    required this.start,
    required this.end,
  });

  factory MediaSegment.fromJson(Map<String, dynamic> json) {
    final startTicks = json['StartTicks'] as int? ?? 0;
    final endTicks = json['EndTicks'] as int? ?? 0;
    return MediaSegment(
      id: json['Id'] as String? ?? '',
      itemId: json['ItemId'] as String? ?? '',
      type: MediaSegmentType.fromServerString(json['Type'] as String?),
      start: Duration(microseconds: startTicks ~/ 10),
      end: Duration(microseconds: endTicks ~/ 10),
    );
  }

  Duration get duration => end - start;
}
