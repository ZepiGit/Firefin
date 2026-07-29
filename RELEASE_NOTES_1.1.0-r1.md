# Moonfin FireTV32 Unofficial 1.1.0-r1

This release targets Fire TV Stick 2nd Generation and Basic Edition devices
running Fire OS 5. It contains only 32-bit ARM native code.

## Highlights

- Jellyfin 10.11 playback profile compatibility
- reliable Fire TV OK/select activation on media cards and detail actions
- restored Jellyfin/Emby artwork with trailing-slash URL normalization
- high-contrast settings focus and remote-friendly slider navigation
- Seerr entry available in the toolbar and Integrations settings
- Media Bar and automatic previews disabled for responsive navigation
- posters, covers and static backdrops remain enabled
- conservative 15 Mbit/s default streaming cap
- clearer playback errors
- new release signing identity

Because the signing identity changed, builds signed with the previous test
certificate must be uninstalled before this release is installed. Local app
settings and login data are removed during uninstall.

See `FIRETV32-README.md`, `COMPATIBILITY.md` and `BUILDING.md` for complete
installation, device and build information.
