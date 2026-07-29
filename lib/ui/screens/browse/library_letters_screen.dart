import 'package:flutter/material.dart';

class LibraryLettersScreen extends StatelessWidget {
  final String libraryId;

  const LibraryLettersScreen({super.key, required this.libraryId});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Browse by Letter')),
      body: const Center(child: Text('Alphabetical browse will appear here')),
    );
  }
}
