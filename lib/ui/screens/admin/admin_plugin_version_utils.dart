import 'package:server_core/server_core.dart';

int compareVersionStrings(String left, String right) {
  final leftParts = RegExp(r'\d+')
      .allMatches(left)
      .map((m) => int.tryParse(m.group(0) ?? '') ?? 0)
      .toList();
  final rightParts = RegExp(r'\d+')
      .allMatches(right)
      .map((m) => int.tryParse(m.group(0) ?? '') ?? 0)
      .toList();

  final maxLen = leftParts.length > rightParts.length
      ? leftParts.length
      : rightParts.length;

  for (var i = 0; i < maxLen; i++) {
    final a = i < leftParts.length ? leftParts[i] : 0;
    final b = i < rightParts.length ? rightParts[i] : 0;
    if (a != b) {
      return a.compareTo(b);
    }
  }

  return 0;
}

String? latestVersionAfter(
  String currentVersion,
  Iterable<VersionInfo> versions,
) {
  String? latest;
  for (final version in versions) {
    final candidate = version.version.trim();
    if (candidate.isEmpty) {
      continue;
    }
    if (compareVersionStrings(candidate, currentVersion) > 0) {
      if (latest == null || compareVersionStrings(candidate, latest) > 0) {
        latest = candidate;
      }
    }
  }

  return latest;
}

VersionInfo? latestVersionInfoAfter(
  String currentVersion,
  Iterable<VersionInfo> versions,
) {
  final latest = latestVersionAfter(currentVersion, versions);
  if (latest == null) {
    return null;
  }

  for (final version in versions) {
    if (version.version == latest) {
      return version;
    }
  }

  return null;
}
