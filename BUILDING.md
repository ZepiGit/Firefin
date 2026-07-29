# Building Moonfin FireTV32

## Toolchain

- Flutter 3.32.8
- Dart 3.8.1
- JDK 17
- Android SDK Platform 36
- Android Build Tools 36.0.0
- Android NDK 27.0.12077973
- CMake 3.22.1

The checked-in Flutter metadata identifies the toolchain used for the release.
The build targets only `android-arm`.

## Signing

Create a private release key outside the repository:

```text
keytool -genkeypair -v -keystore moonfin-firetv32-release.keystore \
  -alias moonfin-firetv32 -keyalg RSA -keysize 4096 \
  -sigalg SHA256withRSA -validity 10000
```

Provide the signing data as environment variables:

```text
MOONFIN_KEYSTORE_FILE
MOONFIN_KEYSTORE_PASSWORD
MOONFIN_KEY_ALIAS
MOONFIN_KEY_PASSWORD
```

`android/keystore.properties.example` documents the alternative local file
format. Never commit the real properties file or keystore.

## Release build

```text
flutter clean
flutter pub get
flutter analyze lib test packages/playback_core packages/playback_jellyfin packages/playback_emby packages/server_core
flutter test test/legacy_firetv_transcode_limits_test.dart
flutter build apk --release --target-platform android-arm
```

The release APK must be renamed to:

```text
Moonfin_FireTV32_Unofficial_1.1.0-r20.apk
```

## Validation

Before publishing:

```text
aapt2 dump badging Moonfin_FireTV32_Unofficial_1.1.0-r20.apk
apksigner verify --verbose --print-certs Moonfin_FireTV32_Unofficial_1.1.0-r20.apk
zipalign -c -v 4 Moonfin_FireTV32_Unofficial_1.1.0-r20.apk
```

Verify that:

- package is `org.moonfin.firetv32`
- version is `1.1.0-firetv32-r20` (`3000027`)
- minimum SDK is 21
- a Leanback launcher and application banner are present
- touchscreen is not required
- only `armeabi-v7a` native libraries are included
- v1 and v2 APK signatures validate
- the certificate fingerprint matches the documented release fingerprint

Create SHA-256 hashes for the APK and the public source archive. The source
archive must exclude caches, generated build products, local SDK paths,
credentials, logs and all signing material.

The repository contains locally patched packages. Running `flutter analyze`
without paths also scans their vendored example and generated-test projects,
which are not part of the FireTV32 application build and may require their own
development dependencies. Release validation therefore analyzes `lib/` and
the FireTV32 regression tests explicitly. The release APK must still be built
from a clean tree as shown above.
