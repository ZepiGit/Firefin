# Moonfin FireTV32

[![License: GPL v2](https://img.shields.io/badge/license-GPL--2.0-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/ZepiGit/Moonfin-FireTV32?display_name=tag)](https://github.com/ZepiGit/Moonfin-FireTV32/releases/latest)
[![Platform](https://img.shields.io/badge/platform-Fire%20OS%205-orange)](COMPATIBILITY.md)
[![Architecture](https://img.shields.io/badge/architecture-ARMv7-lightgrey)](COMPATIBILITY.md)

Unofficial Moonfin Core **1.1.0** backport for **32-bit Fire TV** on **Fire OS 5**
(Android 5.1 / **API 22**). A focused Jellyfin client with remote-friendly
navigation and conservative performance defaults — not a general Android tablet
app.

**Primary target:** Fire TV Stick 2nd Generation / Basic Edition (`AFTT`,
`LY73PR`). 1st-gen Fire TV is experimental. Newer 64-bit sticks are out of scope.

> Independent community project. Not affiliated with Moonfin, Amazon, Jellyfin,
> or Emby.

## Current line: r20 → r21

| Field | Value |
|---|---|
| Release | `1.1.0-firetv32-r21` |
| Version code | `3000028` |
| Package | `org.moonfin.firetv32` |
| Min SDK / arch | API 21 · `armeabi-v7a` only |
| Upstream | Moonfin Core 1.1.0 · GPL-2.0 |

**r21** builds on the r20 AFTT profile (playback, D-pad, 720p/4 Mbit/s ceiling)
with performance waves and follow-up fixes:

| Wave | Focus |
|---|---|
| **r21 base** | Decode-size images, tighter TV image cache (~40 / 32 MiB), HTTP concurrency 2, performance defaults r4 |
| **A** | Lean AFTT runtime — blur 0, no Home `BackdropFilter`, Media Bar / previews / card expansion forced off at read time (not written back) |
| **B** | TV image policy — width-only decode, size buckets, poster **320** / landscape **640** / backdrop **960** |
| **C0** | Home scroll offset isolated via `ValueNotifier` so rows/cards do not rebuild every scroll tick |
| **Fixes** | Sync exports use stored prefs (`getStored`) so lean ceilings never push to other devices; fixed poster class 320; library thumbs/banners 640; pinned InfoArea as builder `child` |

**[Download latest release](https://github.com/ZepiGit/Moonfin-FireTV32/releases/latest)**
(APK, source archive, SHA-256, device notes).

## Features (short)

- Browse Jellyfin libraries, movies, series, seasons, episodes
- Posters, covers, thumbnails, static backdrops (reduced resolution on AFTT)
- Remote playback: resume, seek, audio / subtitles; server transcode when needed
- Seerr entry when configured; separate package (`org.moonfin.firetv32`)
- High-contrast cyan focus; Left/Right-only percentage sliders

Optimized and device-tested primarily with **Jellyfin** (Emby remains upstream).

## Fire TV performance profile

- Media Bar, inline trailers, preview audio, cinema mode, card enlargement: **off**
- Static posters / covers / backdrops: **on** at reduced size (see waves B above)
- Transcode ceiling: **1280×720**, **4 Mbit/s**; bitmap/ASS subs via server when required
- Deterministic D-pad: Play/Pause → seek → Subtitles/Audio

## Install

Enable **ADB debugging** and **Apps from Unknown Sources**, then:

```text
adb connect FIRE_TV_IP:5555
adb install Moonfin_FireTV32_Unofficial_1.1.0-r21.apk
```

Different signing certificate → uninstall first (also clears local data):

```text
adb uninstall org.moonfin.firetv32
adb install Moonfin_FireTV32_Unofficial_1.1.0-r21.apk
```

```text
adb shell am start -W -n org.moonfin.firetv32/org.moonfin.androidtv.MainActivity
```

Verify SHA-256 against the release `SHA256SUMS.txt` before install.

## Build hints

```text
flutter pub get
flutter test
flutter analyze
flutter build apk --release --target-platform android-arm --split-per-abi
```

Toolchain, signing env vars, and validation: **[BUILDING.md](BUILDING.md)**.
Never commit keystores, passwords, or server tokens.

## Docs

| Document | Contents |
|---|---|
| [FireTV32 guide](FIRETV32-README.md) | Technical release overview |
| [Building](BUILDING.md) | Toolchain, signing, APK checks |
| [Compatibility](COMPATIBILITY.md) | Hardware matrix and test status |
| [Release notes r21](RELEASE_NOTES_1.1.0-r21.md) | r21 highlights and limits |
| [Changelog](CHANGELOG.md) | Development history |
| [Security](SECURITY.md) · [Third-party](THIRD_PARTY_NOTICES.md) | Keys and attribution |

## Known limits

- Community compatibility build — not an official Moonfin release
- **No lag / FPS claim** for r21 waves until measured on a physical AFTT device
- Device verification on AFTT so far covers the **r20** baseline; r21 is code-complete pending on-device smoke
- ARMv7 only; DRM streaming out of scope; 1st-gen Fire TV experimental
- Real-world speed depends on artwork, Wi-Fi, transcoder, codecs, and device condition
- No signing keys or secrets in this repository

## Contributing

Bug reports and focused PRs welcome. Keep Fire OS 5 and remote-only navigation
in mind; preserve `org.moonfin.firetv32`; never commit credentials, token-bearing
logs, or signing material.

## License

Derived from [Moonfin Core](https://github.com/Moonfin-Client/Moonfin-Core)
under [GPL-2.0](LICENSE). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
