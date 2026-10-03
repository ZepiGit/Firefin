# Firefin migration: acceptance ledger

## Scope

Firefin continues this repository as a native Kotlin/Views client for AFTT /
LY73PR, Fire OS 5.2.8.0, Android API 22, ARMv7 and 1 GiB RAM, with minSdk 21.
Application ID, namespace and base package are `zepigit.firefin.app`. This
ledger records implementation decisions, verification evidence and remaining
external prerequisites.

**Status: incomplete; no AFTT hardware approval.** Earlier completion labels
were not acceptance evidence. This ledger supersedes the inaccurate earlier
claims of clean lint, all review findings fixed, protected signing, bounded disk
decode and completed API-22 validation. Releases `0.2.3-firefin` and
`0.2.4-firefin` were published by the protected workflow (see Gate 7); no AFTT
hardware approval is claimed. The Flutter sources stay in the repository while
active features lack native parity.

## Current verification snapshot

Native identity: `zepigit.firefin.app`, `0.2.5-firefin`, versionCode `3001005`
(`version.properties`; above the legacy `3000028`), minSdk 21, targetSdk 34.

Evidence is attributed to the version or commit it was produced on. A commit
cannot contain its own hash; CI results for each SHA are recorded in GitHub
Actions and the pull request. No CI or device result is inferred from local tests.

| Evidence | Status |
|---|---|
| `0.2.5-firefin` local build: 83 JVM unit tests, strict debug/release lint 0 errors (74 warnings), R8 release APK (unsigned), APK verifier for debug and release | Verified locally |
| `0.2.5-firefin` on the API 22 stock x86 emulator: instrumented login-screen test; instrumented sign-in over HTTP, favorite set/clear, paging through 125 episodes, sign-out and prefilled sign-in against a local fixture server; manual check of the sign-in error and cleartext dialog | Verified; playback not exercised for this version |
| Snapshot `c9da933aa2a331c760325b97e777b51be7d4980f` (before 0.2.4) on the API 22 stock x86 emulator: login, Moonfin-like Home/Library/Detail/Search/Settings, Seerr discovery/search/detail/season confirmation, TMDB artwork, 480p playback, resume, track dialog and cleanup | Verified manually; x86 codec evidence is not AFTT hardware evidence |
| CI (`android.yml`) | Per SHA in GitHub Actions |
| Physical AFTT (ARMv7) run | **Absent** |
| Signed release (protected `release.yml`, production key, pinned fingerprint) | Published for `0.2.3-firefin` (run 37055114680) and `0.2.4-firefin` (run 37067878548); not validated on AFTT hardware |
| API 22 playback/D-pad run of `0.2.5-firefin`; media redirect behavior on device (JVM test present); feature parity outside the implemented slim Seerr/Moonbase subset (Live TV, downloads, music, books, DLNA, admin, plugin sync, Seerr people/4K) | **Open** |

The old "18/21 tests" and "5m25s" CI figures describe earlier trees and are not
current evidence. The Seerr checks used password login; QuickConnect and
Jellyfin-SSO code flows are not implemented or advertised.

## Verified starting points and history

- Original main: `9f46e1e34469a0f5ac2da438bc3649fba08ea9c1`.
- Reconstructed history: `3af0e6b728ba336c30eb6c0dbd901b8c7b508682`.
  History reconstruction was checked for tree identity, ancestry and authors.
- Original upstream base: `2563c9d8ef1dc191b1e1fbf8d5027c88a27b45a1`;
  306 original upstream objects, one import delta, 20 legacy and eight original
  PR #1 commits. The squash commit is not applied twice. The separate books
  branch is preserved, not silently merged or discarded.
- Import root/delta tree: `79eb22b2fb506ccd67cf34c8d89cb0234199097f`.
  Final old-main/reconstructed tree: `94a6903d88e2e4089da8f6ecf60a67e6f9f6ac09`.
- Delta commit: `ca70c744f3e0d3d0a15b2501a73f35402d504638`.
  Legacy replay committer-date leakage was corrected and rechecked.
  Replayed SHAs and committer metadata are reconstructed, not original signatures.
- Baseline: `e32fcfbf17e526bee0ecf49d6d23ca4061e24582` (historical).
  Repaired-history CI evidence through `cacd4efc9` is historical.
- Backup bundle of that baseline, kept outside the repository by the
  maintainer: SHA256 `0f49c5ca64ef87b7a9dc02118ce9e6d99631fa59575f08611c894c06040a3469`.
- Published main is still the original lineage. Replacing it would need an
  explicit owner decision, fresh remote verification and an exact
  `--force-with-lease`. Existing tags and releases must not move.
- The repository was renamed in place, first to `ZepiGit/Firefin` and then to
  `ZepiGit/Firefin-Player-Android5.1-API22`; GitHub redirects the old names.

## Acceptance checklist

A file or green job is evidence of existence, not of correct behavior. OPEN means
unproven or incomplete; HISTORICAL means evidence applies only to its named SHA.

| Requirement / gate | Artifact or required evidence | Status |
|---|---|---|
| Gate 0: backup and feature inventory | Backup bundle (see above) and the feature matrix below | Partial |
| Gate 1: original upstream, import delta, per-commit attribution and final tree identity | repair-main 3af0e6b | Historical pass |
| Complete old-to-new mapping including eight PR commits | Regenerate mapping in this document against final frozen refs | OPEN |
| Gate 2: syntax accepted, real legacy jobs and build status | Runs 36852773811 (0 jobs), 36870222276 (legacy success) | Historical only |
| Gate 3: rename same repo, app identity, icons/banner, credits, install instructions | Gradle/manifest/APK; LICENSE/notices/docs; README installation section | Partial: identity, credits and install instructions present |
| Gate 4: pinned dependencies, locked/verified resolution, stock API22 fixture tests | Root :app; dependency graph, Lint, instrumentation on API22 | Partial: versions pinned, strict lint, API22 instrumented fixture-server flow (sign-in, marks, paging, sign-out); no lock/verification metadata, no instrumented playback |
| Gate 5A: URL/subpath/HTTPS, password login/logout/session and account isolation | data, auth screens, MockWebServer + UI tests | Password login, typed sign-in errors, cleartext confirmation, server-side logout, origin/subpath and account-safe snapshots verified; no QuickConnect code-polling or server switching claimed |
| Gate 5B: selected Home/library/series-detail/search/favorite/sort/page flows, focus/scroll | ui/ + feature matrix and API22 evidence | Partial: selected flows verified; favorite/watched removal uses `DELETE`; container children page past 60; music/live-TV/admin/full modern parity omitted by design |
| Gate 5C: source/track negotiation, HLS auth, resume/seek, reports and cleanup | playback/ + delayed-request/media fixtures and API22 smoke | Partial: HLS/transcode/resume/seek/audio dialog/terminal cleanup verified on `c9da933`; terminal session on end/error/teardown and retry covered by JVM tests; direct-play subtitle renegotiation implemented but not yet exercised on a device; next-episode prompts omitted |
| Gate 5D: deterministic player controls, remote/media keys, focus, audio focus and lifecycle | Instrumented D-Pad and suspend/resume tests | OPEN |
| Gate 5E: all other actually active AFTT features | Native path and tests for each matrix row | OPEN |
| Gate 6: 1080p/4 Mbit incl audio, lower limits, 320/640/960, bounded caches/decodes/queues | Serialized profile/URL tests; image cold/warm cache stress | JVM profile tests cover the 1080p/4M ceiling and 480p/1M setting; API22/AFTT negotiation and account-keyed image stress remain open |
| Gate 6: stored/effective preferences and end-to-end plugin sync | Preferences and protocol fixture tests; no unused-only type proof | OPEN |
| Gate 6: trust chain/hostname, API22 CA policy, redirect/token isolation, stalled media | Transport TLS, timeout and redirect negative tests | Partial: platform trust plus ISRG Root X1 for Jellyfin and TMDB; JVM tests for an added anchor, wrong hostname, unknown self-signed certificate, stalled and slow media bodies, REST/media redirects and header-only tokens; no API22 CA test on hardware |
| Gate 7: actionlint, wrapper/dependency verification, tests, strict debug/release lint, both builds | android.yml with mandatory reports/APKs | `0.2.5-firefin` local run: 83 tests, 0 lint errors, both APKs and verifier pass; CI per SHA; dependency-verification metadata open |
| Gate 7: separate protected signing, required secrets, stable cert, tag/full SHA, v1, checksums/source | release.yml, verifier, protected environment and actual run/release | DONE for 0.2.3 and 0.2.4: the protected `firefin-release` environment (required reviewer, `main` only), the production signing secrets and the pinned certificate fingerprint are configured; run 37055114680 published `firefin-v0.2.3-firefin` from `e9760aa62e71f5ddb1b7a7f806b0d26c0726c987`, run 37067878548 published `firefin-v0.2.4-firefin` from `503678d519950d74c4cfb6d3d93a08150716a3af`. Earlier attempts failed for setup reasons (SDK path, keystore secret content, annotated tag) and were corrected; the tags `firefin-v0.2.0-firefin`, `firefin-v0.2.1-firefin` and `firefin-v0.2.2-firefin` exist without a release |
| Gate 8: native-only cleanup after parity; updated public English docs/templates | README, BUILDING, COMPATIBILITY, CHANGELOG, SECURITY, notices/templates | Partial: English docs and issue/PR templates describe Firefin; `FIRETV32-README.md` and `RELEASE_NOTES_*` stay as legacy history; the UI is English by default with a German translation |

## Actual feature matrix

The baseline inventory found the following routes and implementation flows. A route
alone is not proof of Fire-TV reachability. Until a flow is ported or explicitly
retired with evidence, no potentially active area is removed as "out of scope".

| Flow | Legacy status / native status | Acceptance gap |
|---|---|---|
| Server list, discovery, saved users, password login, Quick Connect, Emby Connect | Legacy present; native password login only, last server and user name prefilled after sign-out | Switching, restore, invalid tokens, Quick Connect |
| Home resume/next-up/latest/libraries/favorites | Native partial: continue watching, next up, latest for movies/tvshows/mixed libraries; no favorites row; empty state implemented | All libraries and types, retry, refresh without lost focus |
| Movies, shows, seasons/episodes, folders, box sets, genres/letters/suggestions | Native partial recursive grid/details with paged children and episode numbers | Hierarchy, filters, stable sort and navigation |
| Search, favorites, watched-state | Native partial; marks set with `POST` and cleared with `DELETE` | Pagination and status refresh |
| Video direct play/remux/transcode, versions/tracks/chapters/quality/speed/aspect | Native partial | Chapters, versions, speed, aspect |
| Next-up/still-watching prompts, local/remote trailers | Legacy present; native offers a series *Continue* button, no prompts or trailers | Native flow + fixtures |
| Music albums/artists/songs/genres/playlists, queue, lyrics, background audio | Legacy present, native missing (music libraries hidden) | Native browsing/audio and lifecycle |
| Live TV channels/EPG/player/schedule/DVR/series timers | Legacy present, native missing | Native complete flows and cleanup |
| Seerr discovery/search/media/status/season selection/request dialog | Native slim Moonbase flow present; people/admin/4K omitted deliberately | Live discovery/search/detail/season confirmation verified; submission code is covered by mocks, live server submission is not claimed |
| Downloads/offline/storage management | Legacy present, native missing | Quota/cancellation/offline navigation/resume |
| Books/comics PDF/EPUB/CBZ/CBR/7z, photos/slideshows | Legacy present, native missing | Format support/security, native D-Pad reader |
| DLNA discovery/control and remote server sessions | DLNA missing; remote sessions partial | Scope-safe native reuse, actual remote commands |
| Plugin synchronization | Native stored/effective types are not wired | End-to-end protocol parity, local caps never exported |
| Settings, parental/PIN, screensaver, ratings, navigation and home customization | Native settings minimal, current values shown | Port reachable configuration and authorization |
| Admin suite | Routes present, AFTT entry/gates being verified | Do not silently discard active functionality |
| Google Cast, PiP, hero/preview/blur/focus expansion | Explicitly disabled on AFTT | Keep disabled, no added GMS or expensive effects |

## External gates

- Replacing the published main history needs an explicit owner decision.
- A release needs the production signing key, the pinned signing certificate
  (`FIREFIN_CERTIFICATE_SHA256`) and the protected `firefin-release` environment
  with required reviewers; there is no debug-key release. These exist and
  `0.2.3-firefin` and `0.2.4-firefin` were published with them. Every future
  release needs a new version, a lightweight tag `firefin-v<version>` on a
  `main` commit and a successful `android.yml` run for that SHA.
- API22 stock emulator and actual AFTT are separate evidence. Hardware release
  approval remains separate from x86 emulator runs.
- Test account credentials/tokens and raw server content are private; never copy
  them into sources, CI or public documentation. Login success alone is not a
  complete playback test.

## Version notes

### 0.2.5-firefin

Fixes in the native app, each with a regression test:

- Favorite and watched marks are cleared with `DELETE` on the item resource
  instead of `POST …/Delete` (`JellyfinClientTest`, API22 instrumentation).
- Sign-in failures are classified by exception type; a rejected password no
  longer appears as an expired session (`LoginErrorsTest`).
- Jellyfin REST, media and snapshot clients add ISRG Root X1 to the platform
  store; hostname and unknown-certificate checks still fail closed
  (`TransportRegressionTest`).
- Media reads fail after 30 s without data; a slow but continuous body completes
  (`TransportRegressionTest`).
- Natural end, fatal error and teardown finish a playback session once; a retry
  starts a new one (`PlaybackSessionTrackerTest`).
- Container children page past 60 entries with one page in flight and retry
  after a failed page (`PageCursorTest`, API22 instrumentation).

### Fixes after `56134a362`

- The Media3 stream uses the same immutable credential snapshot as the session
  reporter (`ServerTransport.snapshot().mediaHttp`), so an account switch during
  playback cannot mix identities. Regression test: a media request keeps the old
  origin and token after the main transport switches.
- The timeline contract lives in `PlaybackTimeline` (unit-tested): HLS and
  direct play let Media3 apply the resume position with offset 0; a restarted
  non-HLS transcode uses the server `StartTimeTicks` with Media3 starting at 0
  and the offset tracked exactly once for reporting and seeks.
- Artwork input is bounded end to end: a 4 MiB cap on network and disk bytes,
  per-image call/read timeouts and a startup disk-cache trim.
- Test and lint report uploads are fail-closed (`if-no-files-found: error`).
- Stale evidence claims in README/BUILDING and this ledger were refreshed.
- The media-snapshot regression test enqueues its MockWebServer response, and
  the reporter contract tests drain recorded requests only after the terminal
  state, so they do not depend on scheduler timing.

### Snapshot `c9da933aa2a331c760325b97e777b51be7d4980f` (2026-10-02)

- `:app:testDebugUnitTest`: 60 tests, 0 failures; Seerr contracts include
  Moonbase path/auth, status handling, permissions, 201/200/202/409/5xx
  outcomes, image URL restrictions, source selection and preference limits.
- `:app:lintDebug` and `:app:lintRelease`: 0 errors. `assembleDebug` and
  `assembleRelease` pass.
- APK verifier accepts debug ABIs `armeabi-v7a`/`x86` and release ABI
  `armeabi-v7a` only; release is unsigned by design.
- API-22 x86 evidence: authorized test login, Moonfin-style Home/library/detail
  flows, Seerr Moonbase 2.3.1.104 detection and password login,
  trending/search/poster TLS, TV season picker and request confirmation,
  480p/1 Mbit playback, resume at roughly 7:50, D-pad controller, audio track
  dialog, terminal session cleanup. The release-profile 720p H.264 High stream
  exceeded the AOSP x86 software decoder; Debug uses a baseline test profile for
  emulator verification. This is not AFTT hardware evidence.
- Native presentation captures are checked into `docs/firefin-home-current.png`,
  `docs/firefin-seerr-current.png`, `docs/screenshots-southpark-search.png`,
  `docs/southpark-season-two.png` and `docs/southpark-episode.png`; they contain
  no credentials or tokens and show the German translation.

### Historical notes

- An earlier strict-pipeline run passed on a historical snapshot (29 tests, lint
  0 errors / 58 warnings, R8 release APK); it preceded `56134a362`/`cacd4efc9`.
- All REST traffic goes through `ServerTransport.http`; Media3 and the image
  loader use `ServerTransport.mediaHttp` via `JellyfinClient.okHttp`. That media
  client split landed later than the commit message of `8d53ab583` suggests.
- The first API22 emulator check was an install/launch smoke only (package ID,
  `LoginActivity`, no `FATAL`); later records are listed above.

Deliberately reduced scope: no Live TV/DVR, offline downloads, music/book/photo
readers, DLNA, admin, plugin sync, full modern Moonfin theme customization,
QuickConnect code-polling, or Seerr people/4K/admin workflows. These are not
quietly represented as complete, and the legacy Flutter source remains.

Open external prerequisites: AFTT/ARMv7 hardware and CI for each new SHA.
