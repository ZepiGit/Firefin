import 'package:flutter/material.dart';

class ItemListScreen extends StatelessWidget {
  final String itemId;

  const ItemListScreen({super.key, required this.itemId});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Track List')),
      body: const Center(child: Text('Item list will appear here')),
    );
  }
}
