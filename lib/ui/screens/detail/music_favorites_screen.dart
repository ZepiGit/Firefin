import 'package:flutter/material.dart';

import '../../widgets/navigation_layout.dart';

class MusicFavoritesScreen extends StatelessWidget {
  final String parentId;

  const MusicFavoritesScreen({super.key, required this.parentId});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: NavigationLayout(
        showBackButton: true,
        child: const Center(child: Text('Favorite tracks will appear here')),
      ),
    );
  }
}
