import 'package:flutter/material.dart';

class NextUpScreen extends StatelessWidget {
  final String itemId;

  const NextUpScreen({super.key, required this.itemId});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: const Center(
        child: Text(
          'Next Up',
          style: TextStyle(color: Colors.white, fontSize: 24),
        ),
      ),
    );
  }
}
