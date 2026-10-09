# Changelog

The native Firefin line and the legacy Flutter line have separate histories.
Native entries come first; the legacy Moonfin FireTV32 entries below are
preserved unchanged as history.

## Native Firefin

### Unreleased

- Supporting build, compatibility and attribution guides now live in `docs/`;
  legacy Flutter build helpers live in `scripts/legacy/`. CI and relative links
  use the new paths. Redundant standalone legacy version documents were removed;
  legacy source and changelog history remain.
- Replaced the old presentation images with seven current Android API-22
  captures in `docs/screenshots/`, including login, settings and search. These
  show the local unreleased build, not physical Fire TV validation.
- Home places Media requests between Libraries and Play random, with the
  Moonfin-derived Seerr crescent instead of a chat bubble. Remote focus follows
  that order in both directions and shows the focused action's name.
- Login uses a light-gray Firefin wordmark beside the unchanged colored mark.
  Persistent server/user/password labels, larger field text, explicit focus
  order and keyboard Done improve TV sign-in. Password view state is not saved.
- Settings includes `github.com/ZepiGit`; devices without a browser show the
  full address and offer Copy link.
- A successful Jellyfin password sign-in also connects an enabled Moonbase/Seerr
  integration with the transient credentials. No password, Seerr cookie or API
  key is stored. Optional failures do not block Jellyfin sign-in; the bridge
  step is bounded to ten seconds. Existing sessions need one fresh password
  sign-in. The manual fallback has labeled, consistently styled fields and
  keyboard Done support.

### 0.2.5-firefin (versionCode 3001005)

Published on 2026-10-03 as a signed GitHub release (tag `firefin-v0.2.5-firefin`)
by the protected release workflow (run 37095893231) from commit
`7bf3345a21f2731cf25dbfbcb38e7a4cd95d7f78`. No AFTT hardware result is recorded.

Sign-in and connection:

- A wrong user name or password is reported as such. Sign-in errors are now
  classified by type instead of message text: rejected credentials, server error
  with HTTP status, untrusted certificate, unknown host, unreachable server and
  invalid address, each with an English and German message.
- An address typed without a scheme is completed to `https://`. If that fails
  with a TLS error, the message suggests entering the address with `http://`
  (Jellyfin's default port 8096 serves plain HTTP); there is no silent fallback.
- An `http://` address asks for confirmation on every sign-in. Cleartext is now
  also permitted on Android 9 and newer, where it was blocked by the platform
  default before.
- Jellyfin connections (REST, media and playback snapshots) trust ISRG Root X1
  (Let's Encrypt) in addition to the platform certificate store, which on
  Android 5.1 does not contain it. Chain and hostname validation are unchanged.
- Signing out revokes the access token on the server (best effort) and keeps the
  server address and user name to prefill the next sign-in.

Playback:

- A media read that receives no data for 30 seconds now fails into Media3's
  retry and error handling instead of buffering indefinitely; long streams are
  not limited in total duration.
- The natural end of playback closes the player and reports the stop once. A
  fatal playback error ends the session and offers *Retry* (new negotiation at
  the same position, new session) or *Back*, with a localized reason instead of
  the Media3 error code.
- Subtitles are chosen from the server's list during direct play too, so
  external subtitle files can be switched on, changed and switched off. The
  change renegotiates the stream at the current position and keeps the
  play/pause state; audio tracks of a direct-played file still switch locally.
- The screen is kept on only while playback is wanted, so the Fire TV
  screensaver can start on a paused player. Holding a seek key on a non-HLS
  transcode no longer restarts the transcode on every key repeat.

Browsing:

- Removing a favorite or watched mark sends `DELETE` to the item resource.
  Before, the app sent `POST …/Delete`, which is not the route Jellyfin's API
  defines for removing a mark (that is `DELETE` on the same path).
- Season, collection and folder contents load in pages of 60 as the focus nears
  the end, instead of stopping after 60 entries. A failed page keeps the loaded
  entries and is requested again on the next focus move.
- Episodes show their number (`E04` in a season, `S03E04` elsewhere). Series
  offer a *Continue* button for the next episode.
- The home preview shows the description for *Recently added* items. Home rows
  load concurrently.
- The library list shows only library types Firefin can play (movies, shows,
  home videos, collections, mixed); search no longer lists music albums.
- Settings show their current value. Library sort names are localized.
- Seerr *My requests* loads title details with up to four concurrent requests.

Maintenance:

- Session-expired, server-status, oversized-response, unplayable-source and
  incomplete sign-in errors are shown in the device language.
- The artwork disk cache trims least recently used files first, and an
  out-of-memory error while decoding one image no longer stops the shared
  decode workers.
- The unused `WAKE_LOCK` permission was removed.
- `scripts/verify-native-apk.py` uses explicit checks instead of `assert`, so
  it also fails closed under `python -O`.
- Issue and pull request templates describe Firefin and Fire TV devices instead
  of the Moonfin desktop and mobile platforms; GitHub's language statistics
  exclude the legacy Flutter tree.

Verification: 83 JVM unit tests; on the API 22 x86 emulator two instrumented
tests (login screen, and sign-in over HTTP, mark/unmark, paging through 125
episodes, sign-out and prefilled sign-in against a local fixture server) and a
manual check of the sign-in error and cleartext dialog. Playback was not
exercised on the emulator for this version, and there is still no result from
physical AFTT hardware.

### 0.2.4-firefin (versionCode 3001004)

Published on 2026-10-02 as a signed GitHub release (tag `firefin-v0.2.4-firefin`)
by the protected release workflow (run 37067878548) from commit
`503678d519950d74c4cfb6d3d93a08150716a3af`. No AFTT hardware result is recorded.

- The interface now defaults to English. The previous German texts are kept as a
  German translation (`values-de`) and are used on devices set to German.
- User-visible text in the screens and layouts moved from Kotlin code into
  string resources. Seerr and Moonbase error messages carry a string resource and
  are shown in the device language.
- Not localized and still English: errors from the Jellyfin transport and playback
  negotiation (for example "Server request failed (HTTP 500)"), the server
  address validation messages on the sign-in screen, the Jellyfin sort key shown
  in the library title, the Media3 error code in the player error toast, and
  internal guard messages that the UI prevents from being reached.
- Added a JVM test that keeps the English and German resource files in sync
  (same keys, format placeholders and no edge whitespace that the resource
  compiler would drop).

### 0.2.3-firefin (versionCode 3001003)

Published on 2026-10-02 as a signed GitHub release (tag `firefin-v0.2.3-firefin`)
by the protected release workflow from commit
`e9760aa62e71f5ddb1b7a7f806b0d26c0726c987`; the APK was checked against the
pinned production signing certificate. No AFTT hardware result is recorded.
The tested implementation snapshot is `c9da933aa2a331c760325b97e777b51be7d4980f`.
Later documentation-only and ancestry-merge commits do not change its app code.
CI results are per-SHA records in GitHub Actions, not inferred from local tests.

Verified locally on the implementation snapshot: 60 JVM unit tests, API22
instrumentation login-focus test, strict debug/release lint (0 errors; current
reports contain 118 warnings), R8 release APK (unsigned CI build), and the manual
API22 x86 flow record in `docs/FIREFIN_MIGRATION.md`. The published release APK
was built and verified by the protected release workflow. No AFTT hardware
result is claimed.

#### Native implementation scope (tested snapshot `c9da933aa2a331c760325b97e777b51be7d4980f`)

- Transport: `ServerTransport` now exposes a REST client with bounded call and
  read timeouts and a separate media client without call/read timeouts, used
  by Media3 and the image loader. Both stay origin-bound and header-authenticated.
- Session reporting: `SessionReporter` owns its coroutine scope, reports the
  actual play method (`DirectPlay` or `Transcode`), stays ordered
  (Playing, Progress, Stopped) and drains terminal stop/cleanup when the player is destroyed.
- UI lifecycle: scopes are cancelled in `onDestroy` (login, detail, search,
  remote); cancellation is no longer shown as an error toast; Home reloads on
  resume and shows an empty state; Detail refreshes in place after
  favorite/watched changes instead of relaunching and no longer re-queries
  episodes on resume; the Play button is hidden for series; the focus overlay
  of the home row title is a background so it renders while focused.

#### Committed after the `e32fcfbf1` baseline (`36af9208f`, `8d53ab583`)

- Strict native pipeline: lint fails the build, APK identity/alignment/signature
  verifier (`scripts/verify-native-apk.py`), fail-closed artifact upload,
  tracked machine-local `local.properties` removed and ignored.
- Separate manual, protected `Firefin signed release` workflow (main only,
  tag and full SHA, successful CI for that SHA, required reviewers, required
  `FIREFIN_*` signing inputs and pinned certificate, checksums, source archive,
  no overwrite). The legacy Flutter workflow is validation-only and never
  publishes.
- Origin-bound, cancellable `ServerTransport`: default TLS trust, no automatic
  cross-origin redirects, `Authorization` header instead of query tokens.
- HLS transcoding profile with an absolute resume timeline; ordered session
  events with media-source reporting; disk-cache sampling for artwork.
- `version.properties` (single version source), native version raised above the
  legacy build line.

#### Still open for the slim native target

Quick Connect code polling, all library types beyond movies/shows/mixed, Live
TV, downloads, music, books/comics/photos, DLNA, admin, plugin sync, settings
and parental controls, and an AFTT hardware run remain open.
The native Seerr subset (Moonbase detection, discovery, search, detail, status,
season selection and confirmation) is implemented; people/4K/admin and live
request submission are intentionally not claimed. See
[docs/FIREFIN_MIGRATION.md](docs/FIREFIN_MIGRATION.md).

### Earlier native work (superseded claims)

Earlier notes claimed clean lint, "all review findings fixed", protected
signing, bounded disk decode, and completed API 22 validation. Those claims
were not acceptance evidence and are withdrawn. The earlier figures of 18 or
21 unit tests and the 5m25s CI run describe older trees, not the current one;
each version entry above names its own test count.

## Legacy Flutter line (history)

### 1.1.0-firetv32-r21

#### Base (decode / cache / defaults)

- Decode-size aware `LegacyFireTvNetworkImage` with optional device-pixel
  `cacheWidth`/`cacheHeight` passed to `instantiateImageCodec`.
- Reduced TV `ImageCache` to ~40 entries / 32 MiB.
- Capped legacy Fire TV image Dio/HttpClient `maxConnectionsPerHost` at 2.
- `firetv32_performance_defaults_r4` re-forces Media Bar, trailer/episode/
  preview audio, and card focus expansion OFF without wiping unrelated prefs.
- Version bump to `1.1.0+3000028` / `1.1.0-firetv32-r21`.

#### Wave A: lean AFTT runtime

- Lean-TV preference ceiling at read time: blur 0, Media Bar / previews / card
  expansion off; values are not written back to storage.
- Home pinned info overlay: no `BackdropFilter` on lean TV (opaque scrim only).
- Appearance settings: focus-expansion and blur controls locked on lean TV;
  backdrop toggle remains user-editable.

#### Wave B: TV image policy

- Central `TvImageSizePolicy`: poster maxWidth **320**, landscape/thumb **640**,
  backdrop **960**; decode buckets 160-960; BoxFit.cover uses width-only
  (portrait) or height-only (landscape).
- MediaCard, Home, Search, Detail, Library, Favorites, All Genres, Media Bar,
  and BackgroundService aligned to policy classes (not "320 everywhere").

#### C0: home scroll isolation

- Home `_scrollOffset` moved to `ValueNotifier`; list rows/cards no longer
  rebuild on every scroll tick. Opacity consumers use `ValueListenableBuilder`.

#### Fixes (post A/B/C0)

- Plugin sync reads `getStored` so lean ceilings never push to other devices.
- Lean poster server class fixed at 320 (no variable URL fragmentation).
- Library thumbs/banners use landscape 640; pinned InfoArea passed as
  `ValueListenableBuilder.child`.

### 1.1.0-firetv32-r20

- Stabilized Fire OS 5 playback by using synchronous media-player commands and
  a direct native pause recovery path without the blocked player mutex.
- Added a Fire TV transcode ceiling of 1280 x 720 and 4 Mbit/s across device
  profiles, Jellyfin/Emby resolvers and playback request serialization.
- Added regression coverage for the legacy Fire TV transcode limits.
- Made the player D-pad path deterministic: Play/Pause, seek bar, then
  Subtitles/Audio. Only Left/Right seeks; Up/Down changes control rows.
- Increased the Fire TV player-control timeout and refresh it on remote input.
- Corrected player button activation and high-contrast focus presentation.
- Added stable detail scrolling and delayed toolbar focus until the content has
  actually reached the top.
- Prevented the top toolbar from overlapping detail content.
- Kept static artwork and thumbnails enabled while disabling resource-heavy
  inline video previews and the Media Bar on legacy Fire TV hardware.
- Retained Seerr in the toolbar and Integrations settings when configured.
- Added high-contrast TV settings surfaces and Left/Right-only value sliders.

### 1.1.0-firetv32-r1

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
