# Firefin

[![License: GPL v2](https://img.shields.io/badge/license-GPL--2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Fire%20OS%205-orange)](COMPATIBILITY.md)
[![Architecture](https://img.shields.io/badge/architecture-ARMv7-lightgrey)](COMPATIBILITY.md)

**Firefin** is an independent native Kotlin Jellyfin client for legacy Fire TV
devices, originally derived from Moonfin Core. Not affiliated with or supported
by Moonfin, Jellyfin, Emby, or Amazon.

**Primary target:** Fire TV Stick 2nd Generation / Basic Edition (`AFTT`,
`LY73PR`), Fire OS 5.2.8.0 (Android 5.1 / **API 22**), ARMv7, 1 GB RAM.

> **Migration status:** the repository is being converted from the Moonfin
> FireTV32 Flutter backport into a native Kotlin application. The native app
> (`app/`) already builds and covers core flows (login, home, libraries,
> details, playback, search, settings, remote session control) with the same
> conservative Fire-TV limits (720p / 4 Mbit/s incl. audio, H.264 direct play,
> 320/640/960 artwork classes). Larger feature areas of the legacy Flutter app
> (Live TV, Seerr, downloads, music, books, DLNA) are **not yet ported** —
> this branch is not yet advertised as feature-equal. Progress is tracked in
> [docs/FIREFIN_MIGRATION.md](docs/FIREFIN_MIGRATION.md).

| Field | Native app (in development) | Legacy app (until removal) |
|---|---|---|
| Package | `zepigit.firefin.app` | `org.moonfin.firetv32` |
| Display name | Firefin | Firefin (was "Moonfin Fire TV") |
| Min SDK | 21 (runtime verified against API 22) | 21 |
| UI | Native Android views/XML, Media3 player | Flutter + media_kit |

## Install note (important)

The native Firefin app uses a **new application ID**. It is a different
Android app, not an in-place update of the old one:

- The old Moonfin FireTV32 app (`org.moonfin.firetv32`) can stay installed
  side by side; it is not uninstalled or wiped automatically.
- Firefin starts with a fresh login; tokens and settings from the old app are
  **not** migrated automatically.

## Build (native app)

Requires JDK 17 and the Android SDK (see [BUILDING.md](BUILDING.md)):

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug
./gradlew --no-daemon :app:lintDebug :app:assembleRelease
```

Release builds require the `FIREFIN_*` signing secrets (or fall back to debug
signing for local/CI test builds — never publish those).

## Origin and licensing

Firefin descends from the Moonfin FireTV32 backport of
[Moonfin-Client/Moonfin-Core](https://github.com/Moonfin-Client/Moonfin-Core)
(GPL-2.0). The repaired git history preserves the upstream commits and their
authors; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md),
[LICENSE](LICENSE) and [docs/FIREFIN_MIGRATION.md](docs/FIREFIN_MIGRATION.md).

Bugs and support belong to Firefin, not to Moonfin.
