enum CastTargetKind {
  jellyfinSession,
  googleCast,
  airPlay,
  dlna,
}

class CastTarget {
  final String id;
  final CastTargetKind kind;
  final String title;
  final String subtitle;

  const CastTarget({
    required this.id,
    required this.kind,
    required this.title,
    this.subtitle = '',
  });
}
