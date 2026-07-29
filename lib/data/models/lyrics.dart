class LyricLine {
  final Duration start;
  final String text;

  const LyricLine({required this.start, required this.text});

  factory LyricLine.fromJson(Map<String, dynamic> json) {
    final ticks = json['Start'] as int? ?? 0;
    return LyricLine(
      start: Duration(microseconds: ticks ~/ 10),
      text: json['Text'] as String? ?? '',
    );
  }
}

class LyricsData {
  final List<LyricLine> lines;
  final bool isSynced;

  const LyricsData({required this.lines, required this.isSynced});

  factory LyricsData.fromJson(Map<String, dynamic> json) {
    final rawLines = json['Lyrics'] as List? ?? [];
    final lines = rawLines
        .map((e) => LyricLine.fromJson(e as Map<String, dynamic>))
        .toList();
    final synced = lines.any((l) => l.start > Duration.zero);
    return LyricsData(lines: lines, isSynced: synced);
  }

  static const empty = LyricsData(lines: [], isSynced: false);

  bool get isEmpty => lines.isEmpty;
  bool get isNotEmpty => lines.isNotEmpty;
}
