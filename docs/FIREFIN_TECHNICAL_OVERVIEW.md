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

After successful Jellyfin password sign-in, Firefin probes Moonbase and automatically establishes an enabled, unauthenticated Seerr session using the transient username and password through the same-origin bridge. Existing Seerr sessions are reused. No password, Seerr cookie or Seerr API key is persisted; optional integration failures do not block Jellyfin sign-in. An older Jellyfin session created before this change needs one fresh password sign-in to use the automatic bridge; Requests retains a manual fallback.

## Playback profile

The native profile negotiates up to 1920x1080 within a 4,000,000 bit/s total streaming budget, including audio. 720p and 480p settings remain selectable. Stereo audio is the target profile.

The 1080p ceiling is the client's negotiation limit. The API-22 x86 emulator uses a debug-compatible baseline H.264 profile.

Media reads fail after 30 seconds without data, so a stalled connection reaches the retry and error path instead of buffering forever. The natural end, a fatal error, a track change that needs the server and leaving the player each finish the playback session once; a retry starts a new one. Subtitles are chosen from the server's list in every play method, so external subtitle files work during direct play.

Artwork requests use bounded poster, landscape and backdrop sizes. Jellyfin artwork and public TMDB artwork use separate caches; private cache keys include the account context. Container contents (seasons, collections, folders) load in pages of 60 as the remote focus approaches the end.

## Build and verification

The standard local verification command is:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
```

Release `2.7.0` was installed and checked by the maintainer on a Fire TV Stick 2nd generation. Version `2.7.0` has local evidence of 119 JVM tests, strict debug/release lint with 0 errors and 0 warnings, both APK verifiers and an API-22 x86 emulator tour of every screen (`docs/screenshots/`). Instrumented tests on the same emulator cover the login screen, sign-in over HTTP, favorites, paging through 125 episodes and sign-out against a local fixture server. Unit tests pin the Jellyfin route contracts for 10.8, 10.9, 10.10, 10.11 and 12.3.

Signed releases are built only by the protected release workflow, which requires signing credentials stored outside this repository and verifies every APK against the pinned production certificate.

## Browsing and playback features

Home section presets and subtitle preferences are stored per server and
user. A missing home order defaults to resume, next-up, favorites and latest;
previously saved orders remain unchanged. The latest group respects
`Users/Me` library exclusions. Library sort, filter and genre are list
dialogs whose buttons show the current value; a change resets pagination.
Card-menu mutations update matching visible cards.

Episode playback reuses one ExoPlayer and credential snapshot. Every attempt
finishes its reporter once; switching awaits the pending terminal report
before negotiating and starting the next session. Automatic advances use a
ten-second countdown and require confirmation after three unattended
switches. Any remote input resets the unattended counter. Pausing cancels a
countdown and returning leaves the choice to the user.

Jellyfin media segments expose manual skip controls for Intro, Recap,
Preview and Commercial. Missing routes and metadata leave playback usable;
Outro is intentionally ignored. Absolute segment times are converted into
the player's timeline before seeking, including restarted TS transcodes.

Quick Connect uses an address-bound transport for initiation, polling and
final authentication, with a five-minute deadline. Its code and secret live
only in the attempt; cancellation prevents late completion from saving a
session. It does not open the Seerr password bridge, so Requests may ask for
a separate sign-in. The existing HTTP confirmation and TLS policy apply.

## Interface tokens

Layouts reference only the tokens in `values/colors.xml`, `values/dimens.xml`
and the styles in `values/themes.xml`: a type scale from 36 to 14 sp (nothing
smaller), spacing from 4 to 32 dp plus a 48 dp overscan page margin, headers
padded below the overscan band, 48 dp controls and 52 dp inputs. Focus is
static: a 3 dp accent stroke with a lighter surface on cards, inputs, dialog
rows and row titles, an accent fill on buttons. Posters are 150 by 225 dp in
five grid columns. Lists use `TvRecyclerView`, which keeps LEFT/RIGHT inside
the list. Empty and error states come from `layout/view_state.xml` with a
focusable Reload action. With the player controller visible, UP enters the
Audio, Subtitles and Video bar and DOWN returns to the play button.

## Known boundaries

Out of scope for the slim native target:

- Live TV/DVR
- Offline downloads
- Native music, book and photo readers
- DLNA
- Administrative workflows
- Plugin synchronization
- Seerr people, administrator and 4K workflows

These boundaries are intentional and are kept visible so that “compatible” does not imply feature parity with other clients.
