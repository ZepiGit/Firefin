import 'package:flutter/material.dart';

class StillWatchingScreen extends StatelessWidget {
  final String itemId;

  const StillWatchingScreen({super.key, required this.itemId});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: const Center(
        child: Text(
          'Still Watching?',
          style: TextStyle(color: Colors.white, fontSize: 24),
        ),
      ),
    );
  }
}
