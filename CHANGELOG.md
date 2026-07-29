# Changelog

## 1.1.0-firetv32-r1

- Base updated to the public Moonfin Core 1.1.0 source.
- Added Android 5.1 / API 21 compatibility layer and ARMv7-only packaging.
- Added Fire TV launcher, banner, landscape mode and remote navigation.
- Corrected Jellyfin 10.11 playback device-profile width/height properties.
- Added visible playback-preparation failures instead of a black 0:00 player.
- Added explicit OK/select, Enter and gamepad A activation for media cards.
- Added explicit OK/select activation and stable focus keys for Play, Audio,
  Subtitles and the remaining detail actions.
- Added high-contrast cyan focus styling for TV settings and changed percentage
  sliders to Left/Right-only value adjustment.
- Prevented the detail action bar from overlapping metadata and episode rows.
- Disabled Media Bar and automatic media previews on legacy Fire TV hardware.
- Kept posters, covers and static backdrops enabled while reducing Fire TV
  image sizes and removing animated backdrop transitions.
- Added a bounded Fire OS 5 image loader using the legacy byte decoder.
- Normalized Jellyfin and Emby base URLs to prevent double-slash artwork
  requests when the configured server URL ends with `/`.
- Suppressed the unreliable Fire OS 5 server-ping warning while the device has
  active network connectivity.
- Restored the Seerr toolbar/settings entry when the integration is configured.
- Added conservative Fire TV playback and user-interface defaults.
- Added dynamic package version reporting.
- Replaced the previous test signing identity with a newly generated public
  release identity. The private key is not part of the source distribution.
