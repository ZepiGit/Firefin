# Building Firefin

This document covers the native Kotlin app (`app/`). The legacy Flutter build
is kept at the end as history; it is validation-only and is never published.

Build status, in short: a clean local build of the current working tree passed
29 unit tests, strict lint (0 errors, 58 warnings) and an R8 release build, and
the debug APK installed and launched on an API 22 stock x86 emulator. There is
no signed release and no AFTT hardware result. The commit carrying these fixes
is pending (working tree on top of `8d53ab583`).

## Toolchain (native)

| Item | Value |
|---|---|
| JDK | 17 |
| Gradle | 8.10.2 via the checked-in wrapper (`./gradlew`) |
| Android Gradle Plugin / Kotlin | 8.7.3 / 2.0.21 |
| Android SDK | `platforms;android-35`, `build-tools;35.0.0` |
| App SDK levels | minSdk 21, targetSdk 34, compileSdk 35 |
| Application ID | `zepigit.firefin.app` |
| Version | `versionName=0.2.0-firefin`, `versionCode=3001000` (from `version.properties`) |

`version.properties` is the single source of version identity. Keep the native
version code above the legacy `3000028`.

Dependencies are pinned by version in `app/build.gradle.kts`. There is no
Gradle lockfile or dependency-verification metadata yet; CI only validates the
Gradle wrapper checksum.

## Build and verify locally

Same command CI runs:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
```

Lint is strict: `abortOnError = true`, `checkReleaseBuilds = true`. A lint error
fails the build; do not rely on a green exit code from older configurations.

| Artifact | Path | Meaning |
|---|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | Debug-signed, for testing only |
| Unsigned release APK | `app/build/outputs/apk/release/app-release-unsigned.apk` | R8-minified and resource-shrunk validation artifact; **not installable until signed, not a release** |
| Signed release APK | `app/build/outputs/apk/release/app-release.apk` | Only exists when built with `-Pfirefin.productionSigning=true` (see below) |
| Reports | `app/build/reports/`, `app/build/test-results/` | Lint (HTML/XML/text) and unit test results |

Unsigned builds do not need any `FIREFIN_*` setting.

### APK verification

`scripts/verify-native-apk.py` fails closed. It needs Python 3 and the build
tools. It checks package `zepigit.firefin.app`, minSdk 21, label `Firefin`, a
non-empty application banner, a Leanback launcher activity, `zipalign -c 4`,
absence of Flutter/mpv artifacts, and that any native libraries are
`armeabi-v7a` only. With `--signed` it also requires valid v1 and v2 signatures
(and, with `--certificate-sha256`, an exact certificate match); with `--release`
it rejects debuggable builds; `--expect-version-name` / `--expect-version-code`
pin the version identity.

```bash
BT="$ANDROID_HOME/build-tools/35.0.0"
python3 scripts/verify-native-apk.py app/build/outputs/apk/debug/app-debug.apk \
  --build-tools "$BT" --signed --output app/build/debug-verification.json
python3 scripts/verify-native-apk.py app/build/outputs/apk/release/app-release-unsigned.apk \
  --build-tools "$BT" --release \
  --expect-version-name "$(awk -F= '/^versionName=/{print $2}' version.properties)" \
  --expect-version-code "$(awk -F= '/^versionCode=/{print $2}' version.properties)" \
  --output app/build/release-verification.json
```

The unsigned APK is verified without `--signed` because it has no signature by
design.

### API 22 emulator smoke (manual)

Run on a stock API 22 x86 system image (no Google APIs needed). This is a
manual check, not part of CI, and it is not a substitute for an AFTT run:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -n zepigit.firefin.app/.ui.HomeActivity
adb shell dumpsys activity activities | grep -i resumed   # expect LoginActivity when logged out
adb logcat -d | grep -E "FATAL|AndroidRuntime"            # expect no output
```

The recorded smoke covered install, launch, package identity, `LoginActivity`
and absence of `FATAL`. It did not cover login, browsing, playback or memory.

## CI (`Native Android CI`, `.github/workflows/android.yml`)

Runs on pull requests, pushes to `main` and `firefin/**`, and manually. Steps:
actionlint, Gradle wrapper validation, SDK install, the Gradle command above,
APK verification (debug with `--signed`; unsigned release with `--release` and
the version pinned from `version.properties`), a check that no machine-local
`local.properties` is tracked, then artifact upload. Missing files fail the
upload (`if-no-files-found: error`).

Artifacts: `firefin-candidate-<sha>` (both APKs and verification JSON) and
`firefin-reports-<sha>` (test and lint reports). The candidate APKs are
validation artifacts, not releases. No CI run exists yet for the current
working tree.

## Protected release (`Firefin signed release`, `.github/workflows/release.yml`)

Manual (`workflow_dispatch`) only. No release has been produced with it.

Inputs:

| Input | Requirement |
|---|---|
| `tag` | Existing tag `firefin-v<versionName>`, e.g. `firefin-v0.2.0-firefin`; must equal `firefin-v` plus `versionName` in `version.properties` |
| `commit` | Full 40-character reviewed commit SHA; must be the tag's commit |

Gates, all enforced by the workflow:

- Dispatched from `refs/heads/main`; the commit must be an ancestor of `origin/main`.
- The tag must resolve to exactly that commit. The pre-publish recheck compares
  the tag ref's object SHA to the commit, so use a **lightweight** tag; an
  annotated tag does not satisfy that comparison.
- The job runs in the `firefin-release` environment, which must have required
  reviewers configured.
- A successful `android.yml` run must already exist for that exact SHA.
- Required secrets: `FIREFIN_KEYSTORE_BASE64`, `FIREFIN_KEYSTORE_PASSWORD`,
  `FIREFIN_KEY_ALIAS`, `FIREFIN_KEY_PASSWORD`. Required variable:
  `FIREFIN_CERTIFICATE_SHA256` (pinned signing certificate). Any missing value
  fails the job; there is no debug-key fallback.
- The release is built with `-Pfirefin.productionSigning=true`, which makes
  Gradle read `FIREFIN_KEYSTORE_FILE`, `FIREFIN_KEYSTORE_PASSWORD`,
  `FIREFIN_KEY_ALIAS` and `FIREFIN_KEY_PASSWORD` and fail if any is blank. The
  workflow decodes the keystore to a temporary file and deletes it on exit.
- The signed APK must pass `verify-native-apk.py --signed` with the pinned
  `--certificate-sha256` (v1 and v2 signatures).
- Publishing refuses to overwrite an existing release for the tag.

Outputs (workflow artifact `firefin-signed-<commit>` and the GitHub release):
`Firefin_<version>.apk`, `Firefin_<version>_source.zip` (`git archive`),
`SHA256SUMS.txt`, `verification.json`, `BUILD_INFO.txt`.

### Signing key handling

Create the key outside the repository and never commit it:

```text
keytool -genkeypair -v -keystore firefin-release.jks -alias firefin \
  -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000
```

For a local signed build, export the four `FIREFIN_*` variables above
(`FIREFIN_KEYSTORE_FILE` is the keystore path) and add
`-Pfirefin.productionSigning=true`. A locally signed APK is not a project
release. See [SECURITY.md](SECURITY.md) for key handling rules.

## Legacy Flutter build (history, validation only)

The Flutter sources (`lib/`, `packages/`, `android/`) remain until native
parity is proven. The workflow `Legacy Flutter baseline (validation only)`
(`build.yml`) runs `flutter test` and `./build-android.sh` and uploads
`legacy-validation-only-<sha>`; it never publishes. Package
`org.moonfin.firetv32`, version `1.1.0+3000028` (`1.1.0-firetv32-r21`),
Flutter 3.32.8, Dart 3.8.1, ARMv7 (`android-arm`) only. `build-android.sh`
writes `Moonfin_FireTV32_Unofficial_1.1.0-build3000028.apk`.

Historical r21 validation commands:

```text
flutter clean
flutter pub get
flutter analyze lib test packages/playback_core packages/playback_jellyfin packages/playback_emby packages/server_core
flutter test test/legacy_firetv_transcode_limits_test.dart test/image_api_url_test.dart test/legacy_firetv_network_image_test.dart
flutter build apk --release --target-platform android-arm
```

`flutter analyze` without paths also scans vendored example projects that are
not part of the app build, which is why paths are listed explicitly.
