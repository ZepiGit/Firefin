# Moonfin FireTV32 Unofficial 1.1.0-r20

This community release targets Fire TV Stick 2nd Generation and Basic Edition
devices running Fire OS 5. It contains only 32-bit ARM (`armeabi-v7a`) native
code and uses package name `org.moonfin.firetv32`.

## Highlights

- stable Fire OS 5 playback with a 720p / 4 Mbit/s legacy transcode ceiling
- deterministic D-pad navigation for Play/Pause, seek, Subtitles and Audio
- Left/Right-only seeking; Up/Down changes player control rows
- 12-second player control window refreshed by remote input
- high-contrast settings focus and remote-friendly sliders
- stable detail scrolling without toolbar/content overlap
- Media Bar and inline video previews disabled for responsive navigation
- posters, covers, thumbnails and static backdrops remain enabled
- Seerr remains available when configured

## Verified target

- Fire TV model: AFTT / LY73PR
- Fire OS: 5.2.9.5
- Android: 5.1.1 / API 22
- APK: versionCode `3000027`, versionName `1.1.0-firetv32-r20`

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
