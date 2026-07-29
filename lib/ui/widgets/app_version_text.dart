import 'package:flutter/material.dart';
import 'package:package_info_plus/package_info_plus.dart';

Future<String>? _appVersionFuture;

Future<String> loadAppVersion() {
  return _appVersionFuture ??= PackageInfo.fromPlatform()
      .then((info) => info.version.trim())
      .then((version) => version.isEmpty ? 'unknown' : version)
      .catchError((_) => 'unknown');
}

class AppVersionText extends StatelessWidget {
  const AppVersionText({
    super.key,
    this.prefix = 'Version ',
    this.style,
    this.textAlign,
  });

  final String prefix;
  final TextStyle? style;
  final TextAlign? textAlign;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<String>(
      future: loadAppVersion(),
      builder: (context, snapshot) {
        final version = snapshot.data ?? '…';
        return Text('$prefix$version', style: style, textAlign: textAlign);
      },
    );
  }
}
