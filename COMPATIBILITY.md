# Compatibility

| Device | Fire OS / Android | Status |
|---|---|---|
| Fire TV Stick 2nd Generation (`AFTT`) | Fire OS 5 / Android 5.1 (API 22) | Primary target |
| Fire TV Stick Basic Edition (`AFTT`, `LY73PR`) | Fire OS 5 / Android 5.1 (API 22) | Primary target |
| Fire TV Stick 1st Generation | Fire OS 5 / Android 5.1 | Experimental |
| Newer 64-bit Fire TV devices | Fire OS 6+ | Not the intended target |

## Verified device (r20 baseline)

- Model reported by Android: `AFTT`
- Fire OS: `5.2.9.5`
- Android: `5.1.1` / API 22
- Build: `288.6.8.8_user_688806320`
- Display: `1920 × 1080`
- Input: Fire TV remote over D-pad/OK

## On-device test status

The **`1.1.0-firetv32-r20`** candidate was installed as an in-place update on the
verified AFTT device. Checked behavior:

- package install, reboot, direct activity start
- retained login and settings after the update
- Jellyfin home rows, posters, library tiles, static backdrops
- D-pad focus and OK/select in home, detail, and settings
- cyan focus on settings category tiles
- Media Bar disabled/locked; percentage sliders Left/Right-only
- Seerr under Integrations when configured
- Player Play/Pause, seek, Audio, Subtitles via D-pad
- playback with server transcoding at the legacy ceiling; subtitle selector
- no false server-unavailable banner while online

**`1.1.0-firetv32-r21`** (waves A / B / C0 + fixes) is the current code line on
branch `firetv32-r21-perf`. It targets the same AFTT profile. **Physical AFTT
smoke for r21 is still pending** — do not treat r21 as lag-verified until that
run is recorded.

This is not certification for other Fire TV models. Long-duration playback,
codec/subtitle combinations, and standby recovery should be reported per device.

## Playback profile

H.264 direct play up to 1080p where the device allows; server transcoding for
formats the legacy stick cannot play reliably. Legacy ceiling: **1280×720** and
**4 Mbit/s**. HEVC (many x265 releases) and PGS/ASS subtitles normally force
transcoding.

Network quality, transcoder config, subtitle formats, and source bitrate affect
playback independently of the client.
