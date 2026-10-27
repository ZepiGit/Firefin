# Building Firefin

This document covers the native Kotlin app (`app/`). The legacy Flutter build
is kept at the end as history; it is validation-only and is never published.

Build status for `2.7.0`: 119 JVM unit tests, strict lint with 0 errors and
0 warnings (deliberate TV decisions are recorded in `app/lint.xml`), an R8
release build and both APK verifiers pass locally; CI repeats the same steps
for every commit. Signed APKs come only from the protected release workflow.

## Toolchain (native)

| Item | Value |
|---|---|
| JDK | 17 |
| Gradle | 8.10.2 via the checked-in wrapper (`./gradlew`) |
| Android Gradle Plugin / Kotlin | 8.7.3 / 2.0.21 |
| Android SDK | `platforms;android-35`, `build-tools;35.0.0` |
| App SDK levels | minSdk 21, targetSdk 34, compileSdk 35 |
| Application ID | `zepigit.firefin.app` |
| Version | `versionName=2.6.0`, `versionCode=3001006` (from `version.properties`) |

`version.properties` is the single source of version identity. Keep the native
version code above the legacy `3000028`. The next signed release is titled
`Firefin-v2.6.0` and uses tag `firefin-v2.6.0`. The older build results above
remain historical evidence; publication status is recorded by GitHub Actions.

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
absence of Flutter/mpv artifacts, and that debug native libraries are
`armeabi-v7a`/`x86` while release native libraries are `armeabi-v7a` only.
With `--signed` it also requires valid v1 and v2 signatures
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
manual check, not part of CI:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -n zepigit.firefin.app/.ui.HomeActivity
adb shell dumpsys activity activities | grep -i resumed   # expect LoginActivity when logged out
adb logcat -d | grep -E "FATAL|AndroidRuntime"            # expect no output
```

Instrumented tests run on the same emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

They check the login screen and run the app's client against a local fixture
server on the device: sign-in over HTTP, setting and clearing a favorite,
paging through 125 episodes, sign-out and the prefilled sign-in screen.

## CI (`Native Android CI`, `.github/workflows/android.yml`)

Runs on pull requests, pushes to `main` and `firefin/**`, and manually. Steps:
actionlint, Gradle wrapper validation, SDK install, the Gradle command above,
APK verification (debug with `--signed`; unsigned release with `--release` and
the version pinned from `version.properties`), a check that no machine-local
`local.properties` is tracked, then artifact upload. Missing files fail the
upload (`if-no-files-found: error`).

Artifacts: `firefin-candidate-<sha>` (both APKs and verification JSON) and
`firefin-reports-<sha>` (test and lint reports). The candidate APKs are
validation artifacts, not releases. Native CI results are recorded per commit
in GitHub Actions.

## Protected release (`Firefin signed release`, `.github/workflows/release.yml`)

Manual (`workflow_dispatch`) only. Every signed release is produced with it.

Inputs:

| Input | Requirement |
|---|---|
| `tag` | Existing tag `firefin-v<versionName>`, e.g. `firefin-v0.2.5-firefin`; must equal `firefin-v` plus `versionName` in `version.properties` |
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
release. See [SECURITY.md](../SECURITY.md) for key handling rules.

## Legacy Flutter app (history)

The Moonfin-derived Flutter app (`org.moonfin.firetv32`, `1.1.0-firetv32-r21`)
was removed from this repository on 2026-10-26; its sources, the
`scripts/legacy` helpers and the validation-only workflow are available in the
git history up to commit `086af52`. The earlier community releases stay on the
releases page as history.
