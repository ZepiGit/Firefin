# Moonfin FireTV32 Unofficial 1.1.0-r21

This community release targets Fire TV Stick 2nd Generation and Basic Edition
devices running Fire OS 5. It contains only 32-bit ARM (`armeabi-v7a`) native
code and uses package name `org.moonfin.firetv32`.

## Highlights (lag-focused)

- Decode-size aware legacy network images: Skia downsamples posters near
  on-screen size (device pixels = logical × DPR) instead of retaining full
  server bitmaps
- Tighter TV in-memory image cache (~40 entries / 32 MiB)
- Performance defaults r4 re-forces Media Bar, trailer/episode/preview audio,
  and card focus expansion OFF after updates or preference imports
- Smaller TV artwork requests: library posters maxWidth 320; static backdrops
  request/cache around 960 px wide on AFTT
- Image HTTP concurrency capped at 2 connections per host on the Fire TV
  image path to reduce decode storms during fast D-pad scrolling

## Verified target

- Fire TV model: AFTT / LY73PR
- Fire OS: 5.2.9.5
- Android: 5.1.1 / API 22
- APK: versionCode `3000028`, versionName `1.1.0-firetv32-r21`

The Fire TV Stick 1st Generation remains experimental. Newer 64-bit Fire TV
models are not the intended target for this build.

## Publication and signing

The public source archive does not contain a keystore, signing properties or
passwords. Public maintainers should generate and protect their own release
key. APKs signed by a different certificate cannot update this package in
place; uninstalling first also removes local app data.

See `FIRETV32-README.md`, `COMPATIBILITY.md`, `BUILDING.md`, `SECURITY.md` and
`THIRD_PARTY_NOTICES.md` for complete installation, testing and licensing
information.
