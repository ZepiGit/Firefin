# Moonfin FireTV32 Unofficial 1.1.0-r21

Community release for **Fire TV Stick 2nd Generation / Basic Edition** on
**Fire OS 5** (Android 5.1 / API 22). ARMv7 (`armeabi-v7a`) only · package
`org.moonfin.firetv32`.

Builds on **r20** (stable playback, D-pad, 720p/4 Mbit/s ceiling) with
performance waves **A / B / C0** and follow-up fixes.

## Highlights

| Area | Change |
|---|---|
| **r21 base** | Decode-size images; TV cache ~40 / 32 MiB; image HTTP concurrency 2; performance defaults r4 |
| **Wave A** | Lean AFTT runtime — blur 0, no Home BackdropFilter; Media Bar / previews / card expansion off at read time |
| **Wave B** | Image policy — width-only decode, buckets; poster **320** / landscape **640** / backdrop **960** |
| **C0** | Home scroll offset isolated; rows/cards do not rebuild every scroll tick |
| **Fixes** | Sync uses stored prefs (lean never pushes); fixed poster class 320; library landscape 640; pinned InfoArea as builder child |

Static posters, covers, and backdrops stay enabled at reduced resolution.
Media Bar and inline video previews remain disabled for AFTT.

## Target

- Fire TV model: AFTT / LY73PR
- Fire OS: 5.2.9.5 · Android 5.1.1 / API 22
- APK: versionCode `3000028`, versionName `1.1.0-firetv32-r21`

1st-gen Fire TV is experimental. 64-bit Fire TV models are not the intended
target.

## Limits

- **No lag / responsiveness claim** for r21 until measured on a physical AFTT
  device. On-device verification so far covers the **r20** baseline.
- Performance still depends on Wi-Fi, transcoder, artwork, and codecs.

## Signing

The public source archive has no keystore, signing properties, or passwords.
Different certificates cannot update this package in place; uninstalling also
clears local app data.

See `FIRETV32-README.md`, `COMPATIBILITY.md`, `BUILDING.md`, `SECURITY.md`, and
`THIRD_PARTY_NOTICES.md`.
