import 'package:flutter_test/flutter_test.dart';

import 'package:moonfin/app.dart';

// Pumping the full MoonfinApp requires platform channels (connectivity,
// secure storage) that are unavailable in the plain widget-test environment;
// that smoke coverage is handled on device/emulator instead. The app title is
// an active product identifier and pinned here.
void main() {
  test('App display name is Firefin', () {
    expect(kAppName, 'Firefin');
  });
}
