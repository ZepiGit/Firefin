# Firefin migration: acceptance ledger

## Current objective and authority

Continue the existing repository as Firefin, with a native Kotlin/Views client for
AFTT / LY73PR, Fire OS 5.2.8.0, Android API 22, ARMv7 and 1 GiB RAM. Keep minSdk 21.
Application ID, namespace and base package are `zepigit.firefin.app`.
The migration ledger records implementation decisions, verification evidence and
remaining external prerequisites.

**Status: incomplete, not release-ready.** The previous completion labels were
not acceptance evidence. This ledger supersedes the inaccurate earlier claims
of clean lint, all review findings fixed, protected signing, bounded disk decode,
and completed API-22 validation. No final hardware or release approval
is claimed. No Flutter cleanup is permitted while active features lack parity.

## Current verification snapshot

Native identity: `zepigit.firefin.app`, `0.2.0-firefin`, versionCode `3001000`
(`version.properties`; above the legacy `3000028`), minSdk 21, targetSdk 34.

Evidence basis: current candidate changes are uncommitted on branch
`feature/moonfin-ui-seerr`; the base commit is `df208d2d7` and the candidate
remains pending final review. Local gate evidence is
56 JVM tests, a passing API22 instrumentation login-focus test, strict lint
with 0 errors, Debug/Release builds, API22 emulator screens and real Moonbase
test-server checks. The Seerr check used password
login; QuickConnect/Jellyfin-SSO code flows are not implemented or advertised.
No CI run is claimed for this uncommitted candidate.

| Evidence | Status |
|---|---|
| Clean local build: 56 unit tests, strict debug/release lint 0 errors, R8 release APK (unsigned) | Verified locally on current candidate |
| API 22 stock x86 emulator: login, Moonfin-like Home/Library/Detail/Search/Settings, Seerr discovery/search/detail/season confirmation, TMDB artwork, 480p playback, resume, track dialog and cleanup | Verified manually; x86 codec evidence is not AFTT hardware evidence |
| CI (`android.yml`) on current candidate | Not run: candidate remains uncommitted |
| Physical AFTT (ARMv7) run | **Absent** |
| Signed release (protected `release.yml`, production key, pinned fingerprint) | **Absent**; workflow never run |
| API 22 playback/D-Pad smoke; media redirect behavior on device (JVM test present); feature parity outside the implemented slim Seerr/Moonbase subset (Live TV, downloads, music, books, DLNA, admin, plugin sync, Seerr people/4K) | **Open** |

The old "18/21 tests" and "5m25s" CI figures describe earlier trees and are not
current evidence.

## Verified starting points and history

- Original main: `9f46e1e34469a0f5ac2da438bc3649fba08ea9c1`.
- Reconstructed history: `3af0e6b728ba336c30eb6c0dbd901b8c7b508682`.
  History reconstruction was independently checked for tree identity, ancestry and authors.
- Original upstream base: `2563c9d8ef1dc191b1e1fbf8d5027c88a27b45a1`;
  306 original upstream objects, one import delta, 20 legacy and eight original
  PR #1 commits. The squash commit is not applied twice. The separate books
  branch is preserved, not silently merged or discarded.
- Import root/delta tree: `79eb22b2fb506ccd67cf34c8d89cb0234199097f`.
  Final old-main/reconstructed tree: `94a6903d88e2e4089da8f6ecf60a67e6f9f6ac09`.
- Delta commit: `ca70c744f3e0d3d0a15b2501a73f35402d504638`.
  Legacy replay committer-date leakage was corrected and independently rechecked.
  Replayed SHAs and committer metadata are reconstructed, not original signatures.
- Audit baseline: `e32fcfbf17e526bee0ecf49d6d23ca4061e24582` (historical).
  Repaired-history CI evidence through `cacd4efc9` is historical; current
  candidate evidence is the uncommitted snapshot recorded above. Current CI is
  intentionally open until the candidate is committed and pushed.
- External verified audit bundle: `../firefin-audit/e32fcfbf17e5/start.bundle`,
  SHA256 `0f49c5ca64ef87b7a9dc02118ce9e6d99631fa59575f08611c894c06040a3469`.
  Read-only git-archive snapshots for reviewers live alongside it, outside Git.
- Published main is still the original lineage. Replacement requires separate
  explicit approval, fresh remote verification and an exact `--force-with-lease`.
  Existing tags/releases must not move. No approval is inferred from `/goal`.
- Repository has been renamed in place to `ZepiGit/Firefin`; origin uses the new
  URL. The repository-ID assertion still needs current API evidence in the final audit.

## Acceptance checklist

A file or green job is evidence of existence, not of correct behavior. OPEN means
unproven or incomplete; HISTORICAL means evidence applies only to its named SHA.

| Requirement / gate | Artifact or required evidence | Status |
|---|---|---|
| Read full implementation order, repo rules, current main/branches/PR/comments | Original order, repo status; refresh PR/comment metadata before publication | Partial |
| Gate 0: backup, actual features, CI logs | Verified external bundle and reachability matrix | Partial |
| Gate 1: original upstream, import delta, per-commit attribution and final tree identity | repair-main 3af0e6b; historical review record | Historical pass |
| Complete old-to-new mapping including eight PR commits | Regenerate mapping in this document against final frozen refs | OPEN |
| Gate 2: syntax accepted, real legacy jobs and build status | Runs 36852773811 (0 jobs), 36870222276 (legacy success); independent audit | Historical only |
| Gate 3: rename same repo, app identity, icons/banner, credits, install instructions | GitHub repository ID; Gradle/manifest/APK; LICENSE/notices/docs | Partial |
| Gate 4: pinned dependencies, locked/verified resolution, stock API22 fixture playback | Root :app; dependency graph, Lint, instrumentation on API22 | Partial: versions pinned, strict lint, API22 x86 install/launch smoke; no lock/verification metadata, no instrumented or fixture playback |
| Gate 5A: URL/subpath/HTTPS, password login/logout/session and account isolation | data, auth screens, MockWebServer + UI tests | Password login, origin/subpath and account-safe snapshots verified; no QuickConnect code-polling or server switching claimed |
| Gate 5B: selected Home/library/series-detail/search/favorite/sort/page flows, focus/scroll | ui/ + actual feature matrix and API22 evidence | Partial: selected flows verified; music/live-TV/admin/full modern parity omitted by design |
| Gate 5C: source/track negotiation, HLS auth, resume/seek, reports and cleanup | playback/ + delayed-request/media fixtures and API22 smoke | Partial: HLS/transcode/resume/seek/audio dialog/terminal cleanup verified; next-episode prompts omitted |
| Gate 5D: deterministic player controls, remote/media keys, focus, audio focus and lifecycle | Instrumented D-Pad and suspend/resume tests | OPEN |
| Gate 5E: all other actually active AFTT features | Native path and tests for each matrix row | OPEN |
| Gate 6: 1080p/4 Mbit incl audio, lower limits, 320/640/960, bounded caches/decodes/queues | Serialized profile/URL tests; image cold/warm cache stress | 56 JVM/profile tests cover the 1080p/4M ceiling and 480p/1M setting; API22/AFTT negotiation and account-keyed image stress remain open |
| Gate 6: stored/effective preferences and end-to-end plugin sync | Preferences and protocol fixture tests; no unused-only type proof | OPEN |
| Gate 6: trust chain/hostname, API22 CA policy, redirect/token isolation | Transport TLS and redirect negative tests | Partial: default trust (no bypass in code), REST redirect and header-only token tests; no TLS negative tests, media-client redirect tests exist; device-side redirect behavior remains open, no API22 CA test |
| Gate 7: actionlint, wrapper/dependency verification, tests, strict debug/release lint, both builds | android.yml with mandatory reports/APKs | Local current-candidate run: 56 tests, 0 lint errors, both APKs and adjusted verifier pass; CI and dependency-verification metadata remain open |
| Gate 7: separate protected signing, required secrets, stable cert, tag/full SHA, v1, checksums/source | release.yml, verifier, protected environment and actual run/release | OPEN: workflow implemented, never run; secrets, pinned fingerprint and environment reviewers unconfirmed |
| Gate 8: native-only cleanup after parity; updated public English docs/templates | README, BUILDING, COMPATIBILITY, CHANGELOG, SECURITY, notices/templates | Partial: the seven English docs updated for the current tree; issue/PR templates, `FIRETV32-README.md`, `RELEASE_NOTES_*` and the German UI strings are untouched |
| Final SHA: repeat tests/reviews/CI/APK after cleanup; no stale-run substitution | Exact frozen candidate snapshot, local reports and CI run references | OPEN until the candidate is committed |
| Existing authorized server and hardware only; no new server/emulator platform | Fixtures/MockWebServer + stock SDK images; test account kept private | Required |
| Independent verification scopes and exact candidate snapshots | Local reports and documented verification scopes | Partial |
| Independent final architecture/security/UI/core/build reviews; developer fixes and rechecks | Documented findings and recheck outcomes | OPEN |
| Public maintainer reply only after proven implementation | Existing thread reply, not yet posted | OPEN |

## Actual feature matrix

The baseline inventory found the following routes and candidate flows. A route
alone is not proof of Fire-TV reachability. The feature matrix records gates and entry points. Until then no potentially active area is removed as "out of scope".

| Flow | Legacy status / native status | Acceptance gap |
|---|---|---|
| Server list, discovery, saved users, password login, Quick Connect, Emby Connect | Legacy present; native password login only | Switching, restore, invalid tokens, Quick Connect |
| Home resume/next-up/latest/libraries/favorites | Native partial: continue watching, next up, latest for at most four movies/tvshows/mixed libraries; no favorites row; empty state added in working tree | All libraries and types, retry, refresh without lost focus |
| Movies, shows, seasons/episodes, folders, box sets, genres/letters/suggestions | Native partial recursive grid/details | Hierarchy, filters, stable sort and navigation |
| Search, favorites, watched-state | Native partial | Mutating endpoints, cancellation, pagination and status refresh |
| Video direct play/remux/transcode, versions/tracks/chapters/quality/speed/aspect | Native partial, reports not yet correct | HLS/source/timeline/track contracts, teardown |
| Next-up/still-watching prompts, local/remote trailers | Legacy present, native missing | Native flow + fixtures |
| Music albums/artists/songs/genres/playlists, queue, lyrics, background audio | Legacy present, native missing | Native browsing/audio and lifecycle |
| Live TV channels/EPG/player/schedule/DVR/series timers | Legacy present, native missing | Native complete flows and cleanup |
| Seerr discovery/search/media/status/season selection/request dialog | Native slim Moonbase flow present; people/admin/4K omitted deliberately | Live discovery/search/detail/season confirmation verified; submission code is covered by mocks, live server submission is not claimed; QuickConnect/Jellyfin-SSO code flows are not implemented |
| Downloads/offline/storage management | Legacy present, native missing | Quota/cancellation/offline navigation/resume |
| Books/comics PDF/EPUB/CBZ/CBR/7z, photos/slideshows | Legacy present, native missing | Format support/security, native D-Pad reader |
| DLNA discovery/control and remote server sessions | DLNA missing; remote sessions partial | Scope-safe native reuse, actual remote commands |
| Plugin synchronization | Native stored/effective types are not wired | End-to-end protocol parity, local caps never exported |
| Settings, parental/PIN, screensaver, ratings, navigation and home customization | Native settings minimal | Port reachable configuration and authorization |
| Admin suite | Routes present, AFTT entry/gates being verified | Do not silently discard active functionality |
| Google Cast, PiP, hero/preview/blur/focus expansion | Explicitly disabled on AFTT | Keep disabled, no added GMS or expensive effects |

## Historical verification notes

- Strict pipeline and fail-closed verifier passed an equivalent local run on the
  historical working tree (29 tests, lint 0 errors / 58 warnings, R8 release APK). CI for the current candidate remains open until the changes are committed and
  the workflow runs on that exact SHA. (Historical: this state preceded `56134a362`/`cacd4efc9`; the current
  evidence basis is in the header snapshot.)
- All REST traffic goes through `ServerTransport.http`; Media3 and the image
  loader use `ServerTransport.mediaHttp` via `JellyfinClient.okHttp`. That media
  client split landed later. The commit message of `8d53ab583` mentions it, but
  that commit does not contain the change.
- API22 stock x86 emulator: debug APK installed and launched (package ID,
  `LoginActivity`, no `FATAL`). Login, browsing and playback on the emulator have
  not been exercised, and no instrumented tests exist.

## External gates (not waived)

- No explicit approval to replace the published main history has been given.
- Production signing key, pinned signing certificate (`FIREFIN_CERTIFICATE_SHA256`)
  and the protected `firefin-release` environment with required reviewers must
  exist before a release can succeed; no debug-key release. The release workflow
  needs a lightweight tag `firefin-v0.2.0-firefin` on a reviewed `main` commit
  with a successful `android.yml` run for that SHA.
- API22 stock emulator and actual AFTT are separate evidence. The emulator
  install/launch smoke exists; the AFTT run does not. Hardware release approval
  remains separate from x86 smoke.
- Test account credentials/tokens and raw server content are private; never copy
  them into sources, CI, internal notes or public documentation. Login success alone is
  not a complete playback test.


## Final verification cycle

Findings against 56134a362, all fixed on the follow-up commit:

- F1 (High): the Media3 stream now uses the SAME immutable credential snapshot
  as the session reporter (`ServerTransport.snapshot().mediaHttp`), so an
  account switch mid-playback can no longer mix identities. Regression test:
  media request keeps old origin/token after the main transport switches.
- F2 (High): single timeline contract extracted into `PlaybackTimeline`
  (unit-tested): HLS/direct = Media3 applies resume locally, offset 0;
  non-HLS transcode restart = server StartTimeTicks with Media3 start 0 and
  offset tracked exactly once for reporting and seeks.
- F3 (Medium): artwork input is now bounded end to end — 12 MiB cap on network
  and disk bytes, per-image call/read timeouts, startup disk-cache trim.
- F5 (Low/Medium): test/lint report uploads are fail-closed
  (`if-no-files-found: error`).
- F4 (Medium): stale evidence claims in README/BUILDING/ledger refreshed
  (39 tests, current SHA basis, resolved observations marked historical).
- Test-infra defect found and fixed during verification: the media-snapshot
  regression test had no enqueued MockWebServer response and blocked the whole
  suite; the reporter contract tests were rewritten to drain recorded requests
  only after the terminal state (no scheduler timing dependence).

Historical open-items note: the current candidate now has API-22 x86
playback/D-Pad evidence recorded below. Still open and explicitly not claimed:
real AFTT hardware evidence, production signing, CI on the final commit, and
full legacy-feature parity (Live TV, downloads, music, books, DLNA, Quick
Connect code polling, admin, plugin sync, Seerr people/4K).


## Current native candidate evidence (uncommitted; 2026-10-02)

Current local evidence:

- `:app:testDebugUnitTest`: 56 tests, 0 failures; Seerr contracts include
  Moonbase path/auth, status handling, permissions, 201/200/202/409/5xx
  outcomes, image URL restrictions, source selection and preference limits.
- `:app:lintDebug` and `:app:lintRelease`: 0 errors; warnings remain visible in
  the generated reports. `assembleDebug` and `assembleRelease` pass.
- APK verifier accepts debug ABIs `armeabi-v7a`/`x86` and release ABI
  `armeabi-v7a` only; release is unsigned by design.
- API-22 x86 evidence: authorized test login, Moonfin-style Home/library/detail
  flows, Seerr Moonbase 2.3.1.104 detection and password login,
  trending/search/poster TLS, TV season picker and request confirmation,
  480p/1 Mbit playback, resume at roughly 7:50, D-pad controller, audio track
  dialog, terminal session cleanup. The historical release-profile 720p H.264
  High stream exceeded the AOSP x86 software decoder; Debug uses a baseline test
  profile for emulator verification. This is not AFTT hardware evidence.
- Native screenshots are checked into `docs/screenshots-home.png`,
  `screenshots-detail.png`, `screenshots-player.png`, and
  `screenshots-seerr.png`; they contain no credentials or tokens.

Deliberately reduced scope: no Live TV/DVR, offline downloads, music/book/photo
readers, DLNA, admin, plugin sync, full modern Moonfin theme customization,
QuickConnect code-polling, or Seerr people/4K/admin workflows. These are not
quietly represented as complete, and the legacy Flutter source remains.

External blockers: AFTT/ARMv7 hardware, production keystore/protected release
workflow, and CI on the final committed candidate.
