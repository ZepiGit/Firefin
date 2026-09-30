# Moonfin FireTV32

[![License: GPL v2](https://img.shields.io/badge/license-GPL--2.0-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/ZepiGit/Moonfin-FireTV32?display_name=tag)](https://github.com/ZepiGit/Moonfin-FireTV32/releases/latest)
[![Platform](https://img.shields.io/badge/platform-Fire%20OS%205-orange)](COMPATIBILITY.md)
[![Architecture](https://img.shields.io/badge/architecture-ARMv7-lightgrey)](COMPATIBILITY.md)

An unofficial Moonfin Core 1.1.0 compatibility backport for 32-bit Fire TV
devices running Fire OS 5. The project gives still-capable legacy streaming
sticks a focused Jellyfin client with remote-friendly navigation and
conservative performance defaults.

The primary targets are the **Fire TV Stick 2nd Generation** and **Fire TV
Stick Basic Edition** (`AFTT`, commonly sold as `LY73PR`). First-generation
Fire TV hardware is experimental.

> This is an independent community project. It is not affiliated with,
> endorsed by, or supported by Moonfin, Amazon, Jellyfin, or Emby.

## Why this project exists

Current Android media clients increasingly require newer Android versions and
64-bit hardware. Fire OS 5 is based on Android 5.1, and its older WebView,
graphics stack, TLS implementation, and D-pad behavior expose compatibility
problems that do not occur on modern Android TV devices.

Moonfin FireTV32 preserves the modern Moonfin interface while adapting the app
for this legacy platform. The goal is not to turn the device into a general
Android tablet; it is to provide a reliable, living-room-first client for a
self-hosted media server.

## What it can do

- Browse Jellyfin libraries, collections, movies, series, seasons, and episodes
- Display posters, covers, thumbnails, and lightweight static backdrops
- Resume, restart, pause, seek, and stop playback with the Fire TV remote
- Select audio tracks and subtitles from the on-screen player controls
- Request server transcoding when a stream exceeds the legacy device profile
- Integrate Seerr as the media-request entry point when configured
- Retain Moonfin account, library, search, favorites, downloads, and
  customization surfaces where supported by the server and device
- Build and install as a separate package (`org.moonfin.firetv32`) without
  replacing an official Moonfin installation

Moonfin still contains broader upstream functionality, including Emby support,
but this backport is optimized and device-tested primarily with Jellyfin.

## Fire TV performance profile

Legacy hardware needs a different balance than current phones and televisions.
This build therefore uses the following defaults:

- Media Bar, inline trailer playback, preview audio, and cinema mode disabled
- Posters, covers, thumbnails, and static focus previews enabled
- Reduced artwork resolution and bounded image transport for Fire OS 5
- Animated card enlargement disabled by default
- Legacy-device video profile capped at 1280 x 720 and 4 Mbit/s
- Bitmap and ASS subtitles routed through server transcoding when required
- High-contrast cyan focus indicators for television settings
- Percentage sliders controlled only with Left/Right; Up/Down remains navigation
- Deterministic D-pad navigation between Play/Pause, seek, subtitles, and audio

These settings reduce background decoding and GPU pressure without removing the
artwork needed for a usable television interface.

## Current release

| Field | Value |
|---|---|
| Release | `1.1.0-firetv32-r21` |
| Version code | `3000028` |
| Package | `org.moonfin.firetv32` |
| Minimum Android API | 21 (Android 5.0) |
| Architecture | `armeabi-v7a` only |
| Upstream base | Moonfin Core 1.1.0 |
| License | GNU GPL v2 |

The release page contains the signed APK, the corresponding source archive,
SHA-256 checksums, and a device test report:

**[Download the latest release](https://github.com/ZepiGit/Moonfin-FireTV32/releases/latest)**

## Install

Enable **ADB debugging** and **Apps from Unknown Sources** on the Fire TV. Then
connect from a computer with Android Platform Tools:

```text
adb connect FIRE_TV_IP:5555
adb install Moonfin_FireTV32_Unofficial_1.1.0-r21.apk
```

If an older community build was signed with another certificate, Android cannot
update it in place. Removing the old package also removes its local app data:

```text
adb uninstall org.moonfin.firetv32
adb install Moonfin_FireTV32_Unofficial_1.1.0-r21.apk
```

Start the correct package explicitly if more than one Moonfin variant is
installed:

```text
adb shell am start -W -n org.moonfin.firetv32/org.moonfin.androidtv.MainActivity
```

Verify the APK before installation:

```text
certutil -hashfile Moonfin_FireTV32_Unofficial_1.1.0-r21.apk SHA256
```

Compare the result with the published `SHA256SUMS.txt` release asset.

## Documentation

| Document | Contents |
|---|---|
| [FireTV32 release guide](FIRETV32-README.md) | Technical release overview and fixes |
| [Building](BUILDING.md) | Reproducible Android build and signing workflow |
| [Compatibility](COMPATIBILITY.md) | Supported hardware and test status |
| [Release notes](RELEASE_NOTES_1.1.0-r21.md) | Changes and known limitations in r21 |
| [Changelog](CHANGELOG.md) | Development history |
| [Security](SECURITY.md) | Signing-key and vulnerability guidance |
| [Third-party notices](THIRD_PARTY_NOTICES.md) | Upstream attribution and dependencies |

## Build from source

The repository includes the Flutter application and the Fire OS compatibility
packages used by the release. A typical ARMv7 release build is:

```text
flutter pub get
flutter test
flutter analyze
flutter build apk --release --target-platform android-arm --split-per-abi
```

See [BUILDING.md](BUILDING.md) for the required toolchain, version values,
release-signing environment variables, and verification commands.

## Testing and diagnostics

The r21 build targets the same AFTT device profile as r20; r20 was exercised on a Fire TV Stick Basic Edition / 2nd Generation
class device (`AFTT`, Fire OS 5.2.9.5, Android 5.1.1). The test scope included
app start, library browsing, item selection, Jellyfin playback, transport
controls, seeking, subtitle/audio dialogs, settings focus, and crash/ANR review.

When reporting a problem, include:

- Fire TV model and Fire OS version
- Jellyfin or Emby server version
- Exact reproduction steps
- Whether the server selected Direct Play or Transcoding
- A sanitized ADB log without server URLs, access tokens, usernames, or passwords

Useful diagnostics:

```text
adb logcat -c
adb shell am start -W -n org.moonfin.firetv32/org.moonfin.androidtv.MainActivity
adb logcat -d > moonfin-firetv32-log.txt
```

## Project scope and limitations

- This is a community compatibility build, not an official Moonfin release.
- Only 32-bit ARM (`armeabi-v7a`) is packaged.
- DRM streaming services are outside the project's scope.
- Performance depends on artwork size, server transcoding speed, Wi-Fi quality,
  codec support, subtitle format, and the condition of the Fire TV device.
- Fire TV Stick 1st Generation support is experimental and not release-gated.
- No signing key or password is included in the repository or source archive.

## Contributing

Bug reports and focused pull requests are welcome. Please keep Fire OS 5 and
remote-only navigation in mind, preserve the `org.moonfin.firetv32` package
identity, and never commit real server credentials, logs containing access
tokens, or signing material.

## Upstream and license

This project is derived from
[Moonfin Core](https://github.com/Moonfin-Client/Moonfin-Core) and retains its
GNU General Public License version 2 terms. Source code and modifications are
distributed under [GPL-2.0](LICENSE). See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for attribution and bundled
components.
