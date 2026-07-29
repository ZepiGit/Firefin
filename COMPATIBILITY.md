# Compatibility

| Device | Fire OS / Android | Status |
|---|---|---|
| Fire TV Stick 2nd Generation (`AFTT`) | Fire OS 5 / Android 5.1 | Primary target |
| Fire TV Stick Basic Edition (`AFTT`, `LY73PR`) | Fire OS 5 / Android 5.1 | Primary target |
| Fire TV Stick 1st Generation | Fire OS 5 / Android 5.1 | Experimental |
| Newer 64-bit Fire TV devices | Fire OS 6+ | Not the intended target |

## Verified device

- Model reported by Android: `AFTT`
- Fire OS: `5.2.9.5`
- Android: `5.1.1`
- Build: `288.6.8.8_user_688806320`
- Display: `1920 × 1080`
- Input: Fire TV remote over D-pad/OK

## Verified application behavior

The `1.1.0-firetv32-r20` candidate was installed as an in-place update on the
verified device. The following behavior was checked on the physical Fire TV:

- package installation, reboot and direct activity start
- retained login and settings after the update
- Jellyfin home rows, posters, library tiles and static backdrops
- D-pad focus and OK/select activation in the home, detail and settings UI
- clearly visible cyan focus on settings category tiles
- Media Bar disabled and locked for the Fire TV profile
- percentage slider changes with Left/Right and leaves the value unchanged
  when navigating with Up/Down
- Seerr entry visible under Integrations
- Player Play/Pause, seek, Audio and Subtitles controls are reachable with the
  D-pad; Up/Down traverses the rows without seeking
- playback of `The Office` S2:E21 with server transcoding at the legacy-device
  ceiling and successful opening of the subtitle selector
- no false server-unavailable banner while online content is loading

This test does not constitute compatibility certification for other Fire TV
models. Long-duration playback, every codec/subtitle combination and standby
recovery should be reported separately per device.

## Playback profile

The compatibility profile advertises H.264 direct play up to 1080p and uses
server transcoding for formats the legacy device cannot play reliably. On the
legacy profile, transcoding is limited to 1280 x 720 and 4 Mbit/s. HEVC content
such as many x265 releases normally requires Jellyfin transcoding. PGS and ASS
subtitles also force transcoding in the default profile.

Network quality, server transcoder configuration, subtitle formats and source
bitrate can affect playback independently of the client.
