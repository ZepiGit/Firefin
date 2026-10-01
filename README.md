# Firefin

[![License: GPL v2](https://img.shields.io/badge/license-GPL--2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Fire%20OS%205-orange)](COMPATIBILITY.md)
[![Architecture](https://img.shields.io/badge/architecture-ARMv7-lightgrey)](COMPATIBILITY.md)

**Firefin** is an independent native Kotlin Jellyfin client for legacy Fire TV
devices, originally derived from Moonfin Core. Not affiliated with or supported
by Moonfin, Jellyfin, Emby, or Amazon.

**Primary target:** Fire TV Stick 2nd Generation / Basic Edition (`AFTT`,
`LY73PR`), Fire OS 5.2.8.0 (Android 5.1 / **API 22**), ARMv7, 1 GB RAM.

> **Status: in development, not release-ready, not feature-equal to the legacy
> app.** No signed release exists. No run on real AFTT hardware has been
> recorded. See [Verification status](#verification-status) and the acceptance
> ledger in [docs/FIREFIN_MIGRATION.md](docs/FIREFIN_MIGRATION.md).

| Field | Native app (this line) | Legacy app (history, validation only) |
|---|---|---|
| Package | `zepigit.firefin.app` | `org.moonfin.firetv32` |
| Version | `0.2.0-firefin` / versionCode `3001000` (`version.properties`) | `1.1.0-firetv32-r21` / `3000028` |
| SDK | minSdk 21, targetSdk 34, compileSdk 35 | minSdk 21 |
| UI | Native Android Views/XML, Media3 ExoPlayer, OkHttp | Flutter + media_kit |
| Language of UI strings | German only at present | English |

The native version code is deliberately above the legacy `3000028` build line.

## Verification status

Evidence is tied to the current `firefin/dev` HEAD (see
[docs/FIREFIN_MIGRATION.md](docs/FIREFIN_MIGRATION.md) for the exact SHA and
CI run IDs). Nothing below is a claim about a published release.

Verified locally (one clean build of the current working tree):

- 39 JVM unit tests pass (`:app:testDebugUnitTest`), including regression
  tests for transport isolation, session-report ordering and the playback
  timeline contract.
- Strict lint (`abortOnError = true`): 0 errors, 58 warnings.
- R8-minified release APK builds (unsigned).
- API 22 stock x86 emulator: debug APK installs and launches, package ID is
  `zepigit.firefin.app`, `LoginActivity` is shown, no `FATAL` in logcat.
  Login against a server and playback were not part of this smoke.

Not verified / absent:

- Physical AFTT (ARMv7) device: no install, remote, playback or memory result.
  An x86 emulator smoke is not hardware evidence.
- A signed release APK: none built, no production key or pinned certificate
  fingerprint is recorded in this repository.
- CI on the current `firefin/dev` HEAD (runs are linked in the migration ledger).

## Feature parity (open)

The native app covers password login, a home screen (continue watching, next
up, latest for up to four movie/show/mixed libraries), library grids, details
with episodes, search, favorite/watched toggles, Media3 playback with D-pad
control, remote session control and basic settings (image cache, logout).
The following legacy features are **not ported**: Quick Connect (and Emby
Connect, server switching), all library types beyond movies/shows/mixed, Live
TV, Seerr, downloads/offline, music, books/comics/photos, DLNA, the admin
suite, plugin synchronization, parental/PIN and most settings. Until each is
ported or explicitly retired with evidence, no Flutter code is removed. Track
progress in [docs/FIREFIN_MIGRATION.md](docs/FIREFIN_MIGRATION.md).

## Install note (important)

The native Firefin app uses a **new application ID**. It is a different
Android app, not an in-place update of the old one:

- The old Moonfin FireTV32 app (`org.moonfin.firetv32`) can stay installed
  side by side; it is not uninstalled or wiped automatically.
- Firefin starts with a fresh login; tokens and settings from the old app are
  **not** migrated automatically.

## Build (native app)

Requires JDK 17 and Android SDK platform 35 with build-tools 35.0.0; the Gradle
wrapper (8.10.2) is included. See [BUILDING.md](BUILDING.md) for the full
procedure, artifact names, APK verification and the protected release flow.

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
```

Outputs: `app/build/outputs/apk/debug/app-debug.apk` (debug-signed, testing
only) and `app/build/outputs/apk/release/app-release-unsigned.apk` (R8, unsigned
validation artifact, not a release). A signed release is produced only by the
manual, protected `Firefin signed release` workflow with `FIREFIN_*` secrets;
there is no debug-key fallback for releases.

## Security

Default platform TLS trust, no certificate or hostname bypass, no automatic
redirect following to other origins, tokens sent in a header only. Details and
limits: [SECURITY.md](SECURITY.md).

## Origin and licensing

Firefin descends from the Moonfin FireTV32 backport of
[Moonfin-Client/Moonfin-Core](https://github.com/Moonfin-Client/Moonfin-Core)
(GPL-2.0). The repaired git history preserves the upstream commits and their
authors; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md),
[LICENSE](LICENSE) and [docs/FIREFIN_MIGRATION.md](docs/FIREFIN_MIGRATION.md).

Bugs and support belong to Firefin, not to Moonfin.
