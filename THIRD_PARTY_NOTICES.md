# Third-party notices

This file records provenance and dependencies. It is not legal advice and makes
no claim beyond the license files and upstream metadata named here. The only
license text stored in this repository is [LICENSE](LICENSE) (GNU General
Public License version 2).

## Upstream attribution (Moonfin)

Firefin descends from the Moonfin FireTV32 backport of Moonfin Core 1.1.0:

- Project: Moonfin Core
- Upstream commit: `2563c9d8ef1dc191b1e1fbf8d5027c88a27b45a1`
- Upstream repository: https://github.com/Moonfin-Client/Moonfin-Core
- Project license: GNU General Public License version 2

The repaired git history preserves the upstream commits and their authors (the
replayed SHAs and committer metadata are reconstructed; see
[docs/FIREFIN_MIGRATION.md](docs/FIREFIN_MIGRATION.md)). Firefin is an
independent project and is not affiliated with or endorsed by Moonfin.

The native Kotlin app in `app/` is new code written for Firefin. The legacy
Flutter sources (`lib/`, `packages/`, `android/`) are Moonfin-derived and remain
in the tree until native parity is proven. Historical text that mentions
"Moonfin FireTV32" refers to that legacy line.

## Native app dependencies

Declared in `app/build.gradle.kts` and resolved from Google Maven and Maven
Central. They are not vendored in this repository, and no per-dependency license
inventory of the built APK has been produced yet. Copyright and license terms
remain with their respective authors; consult each artifact's published POM and
license files.

Runtime (packaged in the APK, plus their transitive dependencies such as Kotlin
standard library and Okio):

| Dependency | Version |
|---|---|
| `androidx.appcompat:appcompat` | 1.6.1 |
| `androidx.recyclerview:recyclerview` | 1.2.1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.8.1 |
| `androidx.media3:media3-exoplayer`, `-ui`, `-exoplayer-hls`, `-datasource-okhttp` | 1.8.1 |
| `com.squareup.okhttp3:okhttp` | 4.12.0 |
| `com.squareup.okhttp3:okhttp-tls` | 4.12.0 |
| `org.conscrypt:conscrypt-android` | 2.5.2 |

Test only (not packaged): `junit` 4.13.2, `okhttp3:mockwebserver` 4.12.0,
`kotlinx-coroutines-test` 1.8.1, `org.json:json` 20231013, AndroidX Test
`core` 1.5.0, `ext:junit` 1.1.5, `runner` 1.5.2 and `rules` 1.5.0; the
instrumented tests also use `okhttp3:mockwebserver` 4.12.0.
The release APK packages Conscrypt's `libconscrypt_jni.so` for ARMv7; debug
also packages x86 for the API-22 emulator.

Build tooling: Android Gradle Plugin 8.7.3, Kotlin Gradle plugin 2.0.21,
Gradle 8.10.2.

Native (`.so`) libraries: the current release APK packages Conscrypt's
`lib/armeabi-v7a/libconscrypt_jni.so`; debug additionally packages `lib/x86/`
for the API-22 emulator. This is a point-in-time listing; the verifier rejects
unexpected release ABIs and all Flutter/mpv artifacts.
The legacy Flutter build bundled the Flutter engine and media_kit/mpv
libraries; those must not appear in the native APK.

Bundled public certificate: `app/src/main/res/raw/isrgrootx1.pem` is the
ISRG Root X1 certificate from Let's Encrypt, added next to the platform trust
anchors for the Jellyfin and TMDB clients because Android 5.1 does not ship it.
It is neither a trust bypass nor a certificate pin. The certificate SHA-256 is recorded
in the release audit artifact, not in a secret-bearing file.

Toolbar vector icons in `app/src/main/res/drawable/ic_*.xml` are based on
Material Icons path data and are used under the Apache License 2.0. The
corresponding notice is available at https://github.com/google/material-design-icons.
The Firefin launcher icon, transparent mark, wordmark and TV banner are
project artwork supplied for this migration; they are not copied from a
third-party library.

## Legacy Flutter app dependencies

The legacy app includes Flutter and third-party Dart, Android and native
libraries. Their terms remain with their authors. Dependency versions are
recorded in `pubspec.lock`; Android and local package dependencies are declared
in `pubspec.yaml` and the `packages/` directory.

## Trademarks

Jellyfin is a trademark of its respective project. Amazon, Fire TV and Fire OS
are trademarks of Amazon.com, Inc. or its affiliates. This community build is
not endorsed by those projects or companies.
