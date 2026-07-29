import 'dart:ui' as ui;

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

/// A logo display widget with a drop shadow that follows the image shape.
class LogoView extends StatelessWidget {
  final String? imageUrl;
  final ImageProvider? imageProvider;
  final double maxHeight;
  final double maxWidth;

  const LogoView({
    super.key,
    this.imageUrl,
    this.imageProvider,
    this.maxHeight = 80,
    this.maxWidth = 200,
  });

  ImageProvider? get _effectiveProvider {
    if (imageProvider != null) return imageProvider;
    if (imageUrl != null && imageUrl!.isNotEmpty) {
      return CachedNetworkImageProvider(imageUrl!);
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final provider = _effectiveProvider;
    if (provider == null) return const SizedBox.shrink();

    final image = Image(
      image: provider,
      fit: BoxFit.contain,
      errorBuilder: (_, __, ___) => const SizedBox.shrink(),
    );

    return ConstrainedBox(
      constraints: BoxConstraints(
        maxHeight: maxHeight,
        maxWidth: maxWidth,
      ),
      child: Stack(
        alignment: Alignment.center,
        children: [
          ImageFiltered(
            imageFilter: ui.ImageFilter.blur(
              sigmaX: 3,
              sigmaY: 3,
              tileMode: TileMode.decal,
            ),
            child: Image(
              image: provider,
              fit: BoxFit.contain,
              color: Colors.black.withValues(alpha: 0.6),
              colorBlendMode: BlendMode.srcATop,
              errorBuilder: (_, __, ___) => const SizedBox.shrink(),
            ),
          ),
          image,
        ],
      ),
    );
  }
}
