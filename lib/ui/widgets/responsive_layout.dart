import 'package:flutter/material.dart';

import '../../util/platform_detection.dart';

class ResponsiveLayout extends StatelessWidget {
  final Widget mobileBody;
  final Widget? desktopBody;
  final Widget? tvBody;

  const ResponsiveLayout({
    super.key,
    required this.mobileBody,
    this.desktopBody,
    this.tvBody,
  });

  @override
  Widget build(BuildContext context) {
    if (PlatformDetection.useLeanbackUi && tvBody != null) {
      return tvBody!;
    }

    if (PlatformDetection.useDesktopUi && desktopBody != null) {
      return desktopBody!;
    }

    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth >= 1200 && desktopBody != null) {
          return desktopBody!;
        }
        return mobileBody;
      },
    );
  }
}
