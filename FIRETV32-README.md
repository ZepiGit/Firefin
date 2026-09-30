# Moonfin FireTV32 – Unofficial Community Build

An unofficial Moonfin Core 1.1.0 build for older 32-bit Fire TV devices.
The primary targets are the Fire TV Stick 2nd Generation and Fire TV Stick
Basic Edition (`AFTT`, commonly sold as `LY73PR`) running Fire OS 5.

This repository is a community-maintained compatibility fork. It is not
affiliated with, endorsed by or supported by the upstream Moonfin project,
Amazon, Jellyfin or Emby.

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

The FireTV32 build applies conservative defaults for stable navigation on
legacy hardware:

- Media Bar disabled
- inline trailer and episode video playback previews disabled
- preview audio disabled
- posters, covers, thumbnails and static focus previews enabled
- animated card enlargement disabled by default
- posters, covers and static backdrops enabled at reduced resolution
- cinema mode disabled by default
- legacy-device transcodes capped at 1280 x 720 and 4 Mbit/s
- bitmap and ASS subtitles are transcoded instead of direct-played

The Media Bar remains disabled internally on Android TV in this compatibility
build even if an imported settings profile tries to enable it.

## Fixes through r21

- Decode-size aware legacy network images downsample on decode to near
  on-screen size (device pixels), reducing RAM pressure while scrolling.
- Tighter TV image memory budget (~40 / 32 MiB) and image host concurrency
  capped at 2 connections to limit decode storms.
- Performance defaults r4 re-force Media Bar, trailer/episode/preview audio,
  and card focus expansion OFF after updates or preference imports; static
  posters, covers and backdrops stay enabled at reduced resolution (posters
  ~320 px, backdrops ~960 px on AFTT).
- Jellyfin 10.11 device-profile fields use `Width` and `Height`, avoiding the
  HTTP 400 playback error caused by the obsolete `VideoWidth` and
  `VideoHeight` values.
- Jellyfin and Emby base URLs are normalized, so a server address entered with
  a trailing slash cannot create invalid `//Items/.../Images/...` requests.
- Artwork uses a bounded Fire OS 5 image transport and the legacy byte decoder.
  Posters, library art and static backdrops remain enabled.
- Failed playback preparation now displays an error message instead of opening
  a black player at 0:00.
- Media cards, detail actions and settings entries explicitly handle Fire TV
  OK/select, Enter and gamepad A events.
- The player uses a deterministic D-pad path from Play/Pause to the seek bar
  and then to Subtitles/Audio. Up/Down no longer seeks, and the control timeout
  is refreshed while the remote is in use.
- Fire OS 5 playback avoids the asynchronous media-player lock that could
  leave playback or transport controls unresponsive.
- Legacy transcode limits are preserved in Jellyfin and Emby resolver URLs and
  at the playback API boundary.
- TV settings use a high-contrast cyan focus state. Percentage sliders react
  only to Left/Right; Up/Down continues navigation.
- The detail action row reserves its own vertical area and no longer overlaps
  metadata or episode content.
- Seerr remains available in the main toolbar and under Integrations when it is
  configured.
- The Fire OS 5 build suppresses a known false server-unavailable warning
  caused by the legacy TLS stack while the device is online.
- App and server screens report the installed package version dynamically.
- Release signing is loaded only from external environment variables or a
  private local properties file.

## Install

Enable ADB debugging on the Fire TV, then run:

```text
adb connect FIRE_TV_IP:5555
adb install Moonfin_FireTV32_Unofficial_1.1.0-r21.apk
```

An older build signed with another certificate cannot be updated in place.
Remove that package first; uninstalling also removes its local app data:

```text
adb uninstall org.moonfin.firetv32
adb install Moonfin_FireTV32_Unofficial_1.1.0-r21.apk
```

## Build

See [BUILDING.md](BUILDING.md). Release keystores and passwords must never be
committed, archived with the source or attached to a GitHub release.

## Compatibility and support

See [COMPATIBILITY.md](COMPATIBILITY.md) for the device matrix and
[SECURITY.md](SECURITY.md) before publishing or rotating a signing key.
Problems should include the Fire TV model, Fire OS version, server version,
reproduction steps and a sanitized ADB log.

## Upstream and license

The project is derived from
[Moonfin Core](https://github.com/Moonfin-Client/Moonfin-Core). Source and
modifications are distributed under the GNU General Public License version 2;
see [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
