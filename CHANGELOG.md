# Changelog

The native Firefin line and the legacy Flutter line have separate histories.
Native entries come first; the legacy Moonfin FireTV32 entries below are
preserved unchanged as history.

## Native Firefin

### 0.2.2-firefin (versionCode 3001002), unreleased

Not released: no signed APK exists and no AFTT hardware result is recorded.
The tested implementation snapshot is `c9da933aa2a331c760325b97e777b51be7d4980f`.
Later documentation-only and ancestry-merge commits do not change its app code.
CI results are per-SHA records in GitHub Actions, not inferred from local tests.

Verified locally on the implementation snapshot: 60 JVM unit tests, API22
instrumentation login-focus test, strict debug/release lint (0 errors; current
reports contain 118 warnings), R8 release APK (unsigned), and the manual API22
x86 flow record in `docs/FIREFIN_MIGRATION.md`. No AFTT hardware or signed
release is claimed.

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
and parental controls, a signed release, and an AFTT hardware run remain open.
The native Seerr subset (Moonbase detection, discovery, search, detail, status,
season selection and confirmation) is implemented; people/4K/admin and live
request submission are intentionally not claimed. See
[docs/FIREFIN_MIGRATION.md](docs/FIREFIN_MIGRATION.md).

### Earlier native work (superseded claims)

Earlier notes claimed clean lint, "all review findings fixed", protected
signing, bounded disk decode, and completed API 22 validation. Those claims
were not acceptance evidence and are withdrawn. The earlier figures of 18 or
21 unit tests and the 5m25s CI run describe older trees, not the current one
(60 unit tests).

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
