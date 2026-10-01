# Compatibility

## Targets

| Device | Fire OS / Android | Status for native Firefin |
|---|---|---|
| Fire TV Stick 2nd Generation (`AFTT`) | Fire OS 5 / Android 5.1 (API 22) | Primary target, **not yet run on hardware** |
| Fire TV Stick Basic Edition (`AFTT`, `LY73PR`) | Fire OS 5 / Android 5.1 (API 22) | Primary target, **not yet run on hardware** |
| Fire TV Stick 1st Generation | Fire OS 5 / Android 5.1 | Untested |
| Newer 64-bit Fire TV devices | Fire OS 6+ | Not the intended target; untested |

Native app: minSdk 21, targetSdk 34, `zepigit.firefin.app`, `0.2.0-firefin`
(versionCode `3001000`). Landscape only; touchscreen and Leanback are declared
optional; Leanback launcher and TV banner are present.

## Native verification status

| Evidence | Result |
|---|---|
| Clean local build, current working tree (on top of `8d53ab583`, commit pending) | 29 unit tests pass; strict lint 0 errors / 58 warnings; R8 release APK builds (unsigned) |
| API 22 stock x86 emulator, manual install/launch smoke | Debug APK installs, package `zepigit.firefin.app`, `LoginActivity` shown, no `FATAL` in logcat |
| Physical AFTT (ARMv7) | **Absent.** No install, D-pad, playback, memory or thermal result |
| Signed release APK | **Absent.** None built; not published |
| Login, browsing, playback on API 22 (emulator or device) | Not exercised |

The emulator is x86; it does not validate ARMv7 codecs, the 1 GB memory limit
or real remote hardware. Treat Fire OS behavior as unproven until an AFTT run is
recorded.

## Native playback profile (as implemented in `DeviceProfile`)

- Streaming limit: 4 Mbit/s total (`MaxStreamingBitrate` 4 000 000, video and
  audio together); video 1280x720 maximum; stereo audio maximum.
- Direct play: H.264 in mp4/mkv/mov with AAC/MP3/AC-3 audio; audio-only
  AAC/MP3.
- Transcoding: server-side HLS (`ts`, H.264 + AAC), which gives Media3 a
  seekable timeline; resume position is applied once at start. HEVC and other
  codecs the profile does not list are left to the server to transcode.
- Subtitles: the profile declares external SRT and VTT only, but the player
  does not side-load external subtitle files; it only offers text tracks that
  Media3 exposes from the stream. Subtitle behavior is a known gap and
  unverified.
- Absent by design on this target: Media Bar, inline previews, blur, card
  focus expansion, Google Cast, Picture-in-Picture.
- Device limits are applied at runtime and are not meant to be stored or
  synced as user preferences (`EffectiveDevicePreferences`). End-to-end plugin
  sync is not implemented, so that isolation is only type-level for now.

Server transcoder configuration, network quality and source bitrate also
affect playback independently of the client.

## Native artwork limits

Server requests use fixed classes: posters 320 px wide (max 480 high),
landscape/thumb 640, backdrop 960. Decode buckets are 160, 240, 320, 480, 640
and 960 px with RGB_565 sampling. At most two concurrent network fetches;
memory cache capped at the smaller of 32 MiB and one eighth of the heap; disk
cache capped at 64 MiB (clearable in Settings). These are implemented limits;
cold/warm-cache stress behavior has not been measured on a 1 GB device.

## Remote control (implemented, not hardware-verified)

Player, controller hidden: Left/Right and Rewind/Fast Forward seek 10 s;
Center/Up/Down/Menu show the controller; Play/Pause toggles playback. Controller
visible: Menu hides it, Up opens the audio-track dialog, Down the subtitle
dialog, other keys navigate the controller. Back leaves the player.

## Legacy Flutter app (history only)

`org.moonfin.firetv32`, `1.1.0-firetv32-r21`. Verified device for the r20
baseline: `AFTT`, Fire OS `5.2.9.5`, Android `5.1.1` (API 22), build
`288.6.8.8_user_688806320`, 1920x1080, Fire TV remote.

The **r20** candidate was installed as an in-place update on that device and
checked for: install/reboot/direct start, retained login, Jellyfin home rows
and artwork, D-pad focus/select on home, detail and settings, Media Bar locked
off, Seerr under Integrations, player Play/Pause/seek/Audio/Subtitles, server
transcoding at the 720p / 4 Mbit/s ceiling, and no false server-unavailable
banner.

**r21** (waves A/B/C0) was never smoke-tested on hardware; it is not
lag-verified. The legacy app is not certification for other Fire TV models.
