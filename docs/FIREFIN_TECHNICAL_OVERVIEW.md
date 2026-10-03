# Firefin technical overview

This document keeps implementation and verification details separate from the public project introduction in the root README.

## Application profile

- Application ID and namespace: `zepigit.firefin.app`
- Native Android Views/XML UI with RecyclerView
- Media3 playback and OkHttp transport
- Minimum Android version: API 21
- Target SDK: 34
- Compile SDK: 35
- Primary device profile: Fire OS 5 / Android API 22 / ARMv7
- Interface languages: English (default) and German, selected from the device language through Android string resources; a JVM test keeps both resource files in sync
- Debug emulator ABIs: ARMv7 and x86
- Release ABI: ARMv7

## Service compatibility

### Jellyfin Server

Jellyfin is the primary service. Firefin uses it for authentication, libraries, library search, media metadata, playback negotiation, resume state, watched state, audio tracks, subtitles and remote session controls.

The app uses origin- and path-bound requests, header-based authentication and bounded response handling. TLS uses the platform trust store plus the bundled ISRG Root X1 (Let's Encrypt) anchor, which Android 5.1 lacks; there is no global certificate or hostname bypass. Plain `http://` servers are accepted after a confirmation on every sign-in.

### Moonbase / Seerr

Moonbase/Seerr is optional. Moonbase must be installed with the Jellyfin server; it is the bridge through which Firefin reaches Seerr. Firefin detects the configured integration and provides discovery, combined search, title details, TV season selection, request history and a confirmation-based request flow.

The Jellyfin login never forwards the Jellyfin password to Seerr automatically. Seerr sign-in is a separate user action in the Requests area.

## Playback profile

The native profile negotiates up to 1920x1080 within a 4,000,000 bit/s total streaming budget, including audio. 720p and 480p settings remain selectable. Stereo audio is the target profile.

The 1080p ceiling is a client negotiation limit, not a physical hardware guarantee. The API-22 x86 emulator uses a debug-compatible baseline H.264 profile; physical ARMv7 decoder, thermal and sustained playback behavior still require AFTT validation.

Media reads fail after 30 seconds without data, so a stalled connection reaches the retry and error path instead of buffering forever. The natural end, a fatal error, a track change that needs the server and leaving the player each finish the playback session once; a retry starts a new one. Subtitles are chosen from the server's list in every play method, so external subtitle files work during direct play.

Artwork requests use bounded poster, landscape and backdrop sizes. Jellyfin artwork and public TMDB artwork use separate caches; private cache keys include the account context. Container contents (seasons, collections, folders) load in pages of 60 as the remote focus approaches the end.

## Build and verification

The standard local verification command is:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
```

Version `0.2.5-firefin` has local evidence of 83 JVM tests, strict debug/release lint with 0 errors, APK verification, debug/unsigned-release assembly and two instrumented tests on the API-22 x86 emulator. Earlier emulator records cover login, navigation, search, Seerr flows, artwork, playback controls and resume; see the [migration ledger](FIREFIN_MIGRATION.md).

Signed releases are built only by the protected release workflow, which requires signing credentials stored outside this repository. Releases `0.2.3-firefin`, `0.2.4-firefin` and `0.2.5-firefin` were built and verified against the pinned production certificate this way. None has been validated on physical AFTT hardware.

## Known boundaries

The following are not claimed complete for the slim native target:

- Physical AFTT/ARMv7 validation
- Live TV/DVR
- Offline downloads
- Native music, book and photo readers
- DLNA
- Administrative workflows
- Plugin synchronization
- Jellyfin Quick Connect sign-in
- Seerr people, administrator and 4K workflows

These boundaries are intentional and are kept visible so that “compatible” does not imply unsupported feature parity.
