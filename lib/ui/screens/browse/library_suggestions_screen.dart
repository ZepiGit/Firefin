import 'package:flutter/material.dart';

class LibrarySuggestionsScreen extends StatelessWidget {
  final String libraryId;

  const LibrarySuggestionsScreen({super.key, required this.libraryId});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Suggestions')),
      body: const Center(child: Text('Suggested items will appear here')),
    );
  }
}
