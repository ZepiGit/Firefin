import 'package:flutter/material.dart';

import '../../widgets/navigation_layout.dart';

class CollectionScreen extends StatelessWidget {
  final String collectionId;

  const CollectionScreen({super.key, required this.collectionId});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: NavigationLayout(
        showBackButton: true,
        child: const Center(child: Text('Collection items will appear here')),
      ),
    );
  }
}
