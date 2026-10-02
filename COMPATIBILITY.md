# Compatibility

## Targets

| Device | Fire OS / Android | Status for native Firefin |
|---|---|---|
| Fire TV Stick 2nd Generation (`AFTT`) | Fire OS 5 / Android 5.1 (API 22) | Primary target |
| Fire TV Stick Basic Edition (`AFTT`, `LY73PR`) | Fire OS 5 / Android 5.1 (API 22) | 
| Fire TV Stick 1st Generation | Fire OS 5 / Android 5.1 | 
| Newer 64-bit Fire TV devices | Fire OS 6+ | Might work I guess |

Native app: minSdk 21, targetSdk 34, `zepigit.firefin.app`, `0.2.4-firefin`
(versionCode `3001004`). Landscape only; touchscreen and Leanback are declared
optional; Leanback launcher and TV banner are present.

## Native verification status

| Evidence | Result |
|---|---|
| Tested implementation snapshot `c9da933aa2a331c760325b97e777b51be7d4980f` | 60 unit tests pass; strict debug/release lint 0 errors; Debug/Release APKs build; the CI release build is unsigned by design, signed APKs come only from the protected release workflow |
| API 22 stock x86 emulator | Login, Moonfin-like Home/Library/Detail/Search/Settings, Seerr discovery/search/detail/season confirmation, TMDB artwork, baseline 480p playback up to 1080p, resume, D-pad controller, audio dialog and cleanup exercised |
| Physical AFTT (ARMv7) | **Absent.** No hardware decoder/memory/thermal result |
| Signed release APK | Release was built, signed and verified against the pinned production certificate by the protected release workflow (run 37055114680).
| Final commit CI | Consult the exact commit's GitHub Actions record; local results are not CI evidence |


## Native playback profile (as implemented in `DeviceProfile`)

- Streaming limit: 4 Mbit/s total (`MaxStreamingBitrate` 4 000 000, video and
  audio together); video 1920x1080 maximum; stereo audio maximum. The 1080p
  option is a negotiated ceiling, not a physical AFTT hardware guarantee.
- Direct play: H.264 in mp4/mkv/mov with AAC/MP3/AC-3 audio; audio-only
  AAC/MP3.
- Transcoding: server-side HLS (`ts`, H.264 + AAC), which gives Media3 a
  seekable timeline; resume position is applied once at start. HEVC and other
  codecs the profile does not list are left to the server to transcode.
- Subtitles: external SRT/VTT delivery is wired when Jellyfin supplies a
  delivery URL; embedded audio/subtitle source selection is negotiated through
  PlaybackInfo. Additional formats remain outside the slim target scope.
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
and 960 px with RGB_565 sampling. At most two global decode/fetch workers;
private Jellyfin and public TMDB caches are separately bounded, account-keyed
for private content and clearable in Settings. 


## Remote control

Player, controller hidden: Left/Right and Rewind/Fast Forward seek 10 s;
Center/Up/Down/Menu show the controller; Play/Pause toggles playback. Controller
visible: Menu hides it, Up opens the audio-track dialog, Down the subtitle
dialog, other keys navigate the controller. Back hides the visible controller first; a subsequent Back leaves the player.


## Legacy Flutter app (history only)

`org.moonfin.firetv32`, `1.1.0-firetv32-r21`. Verified device for the r20
baseline: `AFTT`, Fire OS `5.2.9.5`, Android `5.1.1` (API 22), build
`288.6.8.8_user_688806320`, 1920x1080, Fire TV remote.

The **r20** candidate was installed as an in-place update on that device and
checked for: install/reboot/direct start, retained login, Jellyfin home rows
and artwork, D-pad focus/select on home, detail and settings, Media Bar locked
off, Seerr under Integrations, player Play/Pause/seek/Audio/Subtitles, server
transcoding at the legacy 720p / 4 Mbit/s ceiling, and no false server-unavailable
banner.
