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
- The interface is currently German only
- Debug emulator ABIs: ARMv7 and x86
- Release ABI: ARMv7

## Service compatibility

### Jellyfin Server

Jellyfin is the primary service. Firefin uses it for authentication, libraries, library search, media metadata, playback negotiation, resume state, watched state, audio tracks, subtitles and remote session controls.

The app uses origin- and path-bound requests, header-based authentication and bounded response handling. It does not use a global certificate or hostname bypass.

### Moonbase / Seerr

Moonbase/Seerr is optional. Firefin detects the configured Moonbase integration and provides discovery, combined search, title details, TV season selection, request history and a confirmation-based request flow.

The Jellyfin login never forwards the Jellyfin password to Seerr automatically. Seerr sign-in is a separate user action in the Requests area.

## Playback profile

The native profile negotiates up to 1920x1080 within a 4,000,000 bit/s total streaming budget, including audio. 720p, 480p and lower settings remain selectable. Stereo audio is the target profile.

The 1080p ceiling is a client negotiation limit, not a physical hardware guarantee. The API-22 x86 emulator uses a debug-compatible baseline H.264 profile; physical ARMv7 decoder, thermal and sustained playback behavior still require AFTT validation.

Artwork requests use bounded poster, landscape and backdrop sizes. Jellyfin artwork and public TMDB artwork use separate caches; private cache keys include the account context.

## Build and verification

The standard local verification command is:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
```

The implementation snapshot has local evidence of 60 JVM tests, strict debug/release lint with 0 errors, APK verification and debug/unsigned-release assembly. The API-22 x86 emulator was used for login, navigation, search, Seerr flows, artwork, playback controls and resume checks.

A production-signed release is not available yet. The release workflow requires protected signing credentials outside this repository.

## Known boundaries

The following are not claimed complete for the slim native target:

- Physical AFTT/ARMv7 validation
- Production signing and public release distribution
- Live TV/DVR
- Offline downloads
- Native music, book and photo readers
- DLNA
- Administrative workflows
- Plugin synchronization
- Jellyfin Quick Connect sign-in
- Seerr people, administrator and 4K workflows

These boundaries are intentional and are kept visible so that “compatible” does not imply unsupported feature parity.
