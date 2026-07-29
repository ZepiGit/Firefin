import 'package:flutter/material.dart';

const _kGradientColors = [
  Color(0xFF0a0a0a),
  Color(0xFF1a1a2e),
  Color(0xFF16213e),
];

const _kCardColor = Color(0xCC111528);
const _kCardBorderColor = Color(0x33FFFFFF);
const _kCardRadius = 20.0;
const _kCardMaxWidth = 700.0;

class LoginScaffold extends StatelessWidget {
  final double maxWidth;
  final Widget child;
  final Widget? header;
  final Widget? footer;

  const LoginScaffold({
    super.key,
    this.maxWidth = _kCardMaxWidth,
    this.header,
    this.footer,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: _kGradientColors,
          ),
        ),
        child: SafeArea(
          child: Align(
            alignment: const Alignment(0, -0.6),
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
              child: ConstrainedBox(
                constraints: BoxConstraints(maxWidth: maxWidth),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (header != null) header!,
                    Container(
                      decoration: BoxDecoration(
                        color: _kCardColor,
                        borderRadius: BorderRadius.circular(_kCardRadius),
                        border: Border.all(color: _kCardBorderColor),
                      ),
                      padding: const EdgeInsets.all(32),
                      child: child,
                    ),
                    if (footer != null) footer!,
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
