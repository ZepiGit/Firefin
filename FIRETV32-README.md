# Moonfin FireTV32 – Unofficial Community Build

Unofficial Moonfin Core **1.1.0** build for older **32-bit Fire TV** devices.
Primary targets: Fire TV Stick 2nd Generation and Basic Edition (`AFTT` /
`LY73PR`) on **Fire OS 5** (Android 5.1 / API 22).

Community-maintained compatibility fork. Not affiliated with Moonfin, Amazon,
Jellyfin, or Emby.

## Release

- Release: `1.1.0-firetv32-r21`
- Android version code: `3000028`
- Package: `org.moonfin.firetv32`
- Minimum Android API: 21
- Architecture: ARMv7 / `armeabi-v7a` only
- Upstream base: Moonfin Core tag `1.1.0`, commit
  `2563c9d8ef1dc191b1e1fbf8d5027c88a27b45a1`
- License: GNU GPL v2

## Fire TV performance profile

Conservative defaults for legacy hardware:

- Media Bar, inline trailer/episode previews, preview audio: disabled
- Posters, covers, thumbnails, static focus previews: enabled (reduced size)
- Animated card enlargement and cinema mode: off by default
- Transcodes capped at 1280×720 and 4 Mbit/s
- Bitmap and ASS subtitles transcoded instead of direct-played

Media Bar stays disabled on Android TV in this build even if an imported
settings profile tries to enable it. Wave A also applies a lean read-time
ceiling (blur 0, previews/expansion off) without rewriting stored prefs.

## r21 changes (waves A / B / C0 + fixes)

**Base**

- Decode-size aware legacy network images (Skia downsamples near on-screen size)
- Tighter TV image memory budget (~40 / 32 MiB); image host concurrency ≤ 2
- Performance defaults r4 re-force heavy features OFF after updates/imports
- Artwork: posters ~320 px, landscape ~640, backdrops ~960 on AFTT

**Wave A — lean runtime**

- Lean-TV preference overrides at get-time (not persisted)
- Home pinned info: no `BackdropFilter` on lean TV
- Appearance: blur / focus-expansion locked on lean TV

**Wave B — image policy**

- Central size policy with decode buckets and width-only / height-only axes
- Home, Search, Detail, Library, Favorites, genres, Media Bar aligned

**C0 — scroll isolation**

- Home scroll offset via `ValueNotifier`; content rows not rebuilt per tick

**Fixes**

- Sync profile export uses stored values so lean ceilings never push remotely
- Fixed poster server class 320; library thumbs/banners landscape 640
- Pinned InfoArea as `ValueListenableBuilder.child`

**Carried from earlier releases**

- Jellyfin 10.11 device-profile `Width`/`Height` (not obsolete VideoWidth/Height)
- Normalized Jellyfin/Emby base URLs (no `//Items/.../Images/...`)
- Bounded Fire OS 5 image transport + legacy byte decoder
- Visible playback-prep errors; OK/select/Enter/gamepad A on cards and actions
- Deterministic player D-pad; Fire OS 5 sync media-player path
- High-contrast cyan focus; Left/Right-only percentage sliders
- Detail action row reserved; Seerr when configured; false online-ping suppressed
- Dynamic package version; release signing from env / private local properties only

## Install

```text
adb connect FIRE_TV_IP:5555
adb install Moonfin_FireTV32_Unofficial_1.1.0-r21.apk
```

Other certificate → uninstall first (clears local data):

```text
adb uninstall org.moonfin.firetv32
adb install Moonfin_FireTV32_Unofficial_1.1.0-r21.apk
```

## Build

See [BUILDING.md](BUILDING.md). Never commit keystores, passwords, or attach
them to a GitHub release.

## Compatibility and support

See [COMPATIBILITY.md](COMPATIBILITY.md) and [SECURITY.md](SECURITY.md).
Bug reports: Fire TV model, Fire OS version, server version, steps, sanitized
ADB log. **No lag claim** for r21 until measured on device (r20 was the last
on-device verified baseline).

## Upstream and license

Derived from [Moonfin Core](https://github.com/Moonfin-Client/Moonfin-Core).
GPL-2.0 — see [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
