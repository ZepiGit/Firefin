import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:jellyfin_preference/jellyfin_preference.dart';

/// Utility for PIN code hashing and verification.
///
/// Uses SHA-256 to store and verify user PINs securely.
/// PIN preferences are scoped per-user via prefixed keys.
class PinCodeUtil {
  final PreferenceStore _store;
  final String _userId;

  PinCodeUtil(this._store, this._userId);

  String get _pinHashKey => 'user_pin_hash_$_userId';
  String get _pinEnabledKey => 'user_pin_enabled_$_userId';

  /// Whether the user has a PIN enabled.
  bool get isPinEnabled => _store.getBool(_pinEnabledKey) ?? false;

  /// Hash a PIN string using SHA-256.
  static String hashPin(String pin) {
    final bytes = utf8.encode(pin);
    return sha256.convert(bytes).toString();
  }

  /// Verify a PIN against the stored hash.
  bool verifyPin(String pin) {
    final storedHash = _store.getString(_pinHashKey);
    if (storedHash == null || storedHash.isEmpty) return false;
    return hashPin(pin) == storedHash;
  }

  /// Set a new PIN (hashes and stores it).
  Future<void> setPin(String pin) async {
    await _store.setString(_pinHashKey, hashPin(pin));
    await _store.setBool(_pinEnabledKey, true);
  }

  /// Remove the PIN and disable it.
  Future<void> removePin() async {
    await _store.remove(_pinHashKey);
    await _store.setBool(_pinEnabledKey, false);
  }

  /// Enable or disable PIN without changing the hash.
  Future<void> setPinEnabled(bool enabled) async {
    await _store.setBool(_pinEnabledKey, enabled);
  }
}
