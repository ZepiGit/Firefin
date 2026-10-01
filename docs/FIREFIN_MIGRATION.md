# Firefin Migration

Central migration log for turning `ZepiGit/Moonfin-FireTV32` into **Firefin**, an
independent native Kotlin Jellyfin client for Fire TV Stick 2nd Gen / Basic Edition
(AFTT / LY73PR, Fire OS 5.2.8.0, Android 5.1 / API 22, ARMv7, 1 GB RAM).

Product identity: app name `Firefin`, applicationId / namespace `zepigit.firefin.app`,
minSdk 21 (runtime verification on API 22). Moonfin remains credited as origin
(LICENSE, THIRD_PARTY_NOTICES, history). Status legend: ✅ done · 🟡 in progress ·
⬜ open · ⛔ blocked.

## Phase 0 — Inventory and backup ✅

- Start state: main `9f46e1e34469a0f5ac2da438bc3649fba08ea9c1` (= squash of PR #1,
  app 1.1.0-firetv32-r21, versionCode 3000028). No additional commits on origin/main.
- Backup: `firefin-backup/firefin-pre-repair-9f46e1e.bundle` (verified complete
  history), archive refs `refs/archive/firefin-pre-repair/{main,books-native-20260928}`,
  remote branch `archive/pre-repair-main-9f46e1e`.
- Divergent contributor branch `books-native-20260928` (4 commits on 900af3e,
  MichelFlix Books custom-app line) is preserved untouched; not part of Firefin scope.
- CI failure evidence: run `36852773811` = `failure` after 0 s, job list empty
  (`total_count: 0`) → workflow-validation failure. Root defect in old
  `build.yml` line 117: `if: ${{ secrets.APPLE_ID != '' }}` — the `secrets`
  context is not allowed in step `if` conditions. Additional confirmed defects:
  Flutter 3.41.2 vs documented 3.32.8; artifact glob `Moonfin_Android_v*.*`
  vs actual `Moonfin_FireTV32_Unofficial_…apk`; stale `-r2` suffix; tag filter
  `[0-9]+…` vs actual `v…` tags; release job waiting on all desktop platforms;
  release signing threw at Gradle configure time without secrets; `lintVital*`
  disabled.
- Feature matrix: ~90 routes inventoried (Explore sweep). Active on Fire TV:
  leanback UI + D-Pad, Home (resume/next-up/latest), libraries/genres/letters,
  search, item details, video/audio playback, Live TV incl. EPG, Seerr, downloads,
  music, books/comics (PDF/EPUB/CBZ), DLNA casting (native Kotlin), remote session
  control, Quick Connect (both directions), Emby support, admin suite, plugin sync.
  Disabled on Fire TV: media bar/hero carousel, card focus expansion, episode
  previews, live blur, backdrop slideshow, mpv.conf editor, Google Cast, PiP,
  HEVC/VP9/AV1 direct play (H.264 only, stereo downmix). Transcode ceiling
  1280×720 / 4 000 000 bit/s incl. audio. Image classes: poster 320 w,
  landscape 640 w, backdrop 960 w; decode buckets [160,240,320,480,640,960];
  image cache 32 MiB / 40 entries; image host concurrency 2.
- Legacy TLS bypass (`legacy_firetv_certificate_support.dart`:
  `badCertificateCallback => true`) must NOT be ported.

## Phase 1 — Git history repair ✅ (independent review pending)

- Upstream `Moonfin-Client/Moonfin-Core` fetched; base commit
  `2563c9d8ef1dc191b1e1fbf8d5027c88a27b45a1` ("Updated readme and app to 1.1.0")
  has genuine ancestry and was preserved SHA-identical.
- Reconstruction (worktree `firefin-repair`, branch `repair-main`, pushed as
  `repair/history-main`):
  - Delta commit `ca70c744f3e0d3d0a15b2501a73f35402d504638` on top of upstream
    base, tree = `fbd4a3c^{tree}` (`94a6903d88e2e4089da8f6ecf60a67e6f9f6ac09`),
    original author/committer metadata preserved
    ("Moonfin FireTV32 Baseline <moonfin-firetv32@localhost>",
    2026-07-29T13:22:55+02:00 / 13:24:12+02:00).
  - 20 legacy commits replayed (`repair-legacy`), then the 8 original PR #1
    commits (from tag `v1.1.0-firetv32-r21`) replayed on top. The squash commit
    `9f46e1e` was deliberately **not** applied.
- Verification: `git diff --exit-code 9f46e1e repair-main` → empty;
  tree IDs identical (`94a6903d…`); upstream is ancestor; `git fsck --full` clean;
  335 commits; authors preserved (Axl Nunez, RadicalMuffinMan, Michel Tie,
  Moonfin FireTV32 Baseline). Full old→new SHA mapping:
  `firefin-backup/sha-mapping.txt` (only "no match" is the intentionally dropped
  squash commit).
- Reviewer note: rebased own commits keep authorship but legitimately get new
  SHAs/committers; the delta commit preserves both original author and committer
  identity of the import snapshot.
- **Open:** independent `firefin-review` (Opus) review FF-P1-01. The published
  main history is replaced only after explicit user approval, using
  `--force-with-lease` bound to the verified old main SHA.

## Phase 2 — Legacy CI repair ✅ (CI run pending)

- `build.yml` replaced with Android/Fire-TV-only baseline: pinned Flutter 3.32.8,
  `flutter test` step, conditional release signing (job-level env mirrors secret
  presence, step `if: env.HAS_RELEASE_SIGNING == 'true'` — valid context usage),
  artifact `Moonfin_FireTV32_Unofficial_<version>-build<nnnn>.apk`, release job
  only for Android, tag filter `v*`.
- `android/app/build.gradle.kts`: signing env names `FIREFIN_*`; missing release
  keys no longer throw at configure time (fallback to debug signing so PR/test
  builds run; real releases require the production key).
- `lintVital` disablement kept only as documented legacy baseline behavior; the
  native pipeline must not copy it.

## Phase 3 — Firefin identity ✅

- GitHub repository renamed `Moonfin-FireTV32` → **`ZepiGit/Firefin`** (same repo,
  redirects active, remotes updated). Description set to a migration-phase wording.
- Legacy app (until removal in Phase 8): label `Firefin`, namespace +
  applicationId `zepigit.firefin.app`, Kotlin bridge packages moved to
  `zepigit/firefin/app`, Flutter channels `zepigit.firefin.app/…`,
  DeviceProfile name `Firefin for Fire TV (32-bit)`.
- Documented exceptions: internal Dart package name `moonfin` and its derived
  auth-header appName stay until the Flutter stack is deleted (avoiding mass
  import churn on code slated for removal); protocol key `MoonfinLegacyFireTv`
  stays (server-plugin protocol identifier); `storage_path_service` directory
  `Moonfin/` stays (user-data migration concern).
- Install note (native app): new applicationId = different Android app, fresh
  login required, old app can stay installed; no token/setting migration.

## Phase 4 — Native API 22 base ✅ (local build + tests green)

Toolchain (pinned): JDK 17, Gradle 8.10.2 (wrapper committed), AGP 8.7.3,
Kotlin 2.0.21, compileSdk 35, targetSdk 34, minSdk 21, Media3 1.8.1 (last line
with minSdkVersion 21 per `constants.gradle`; 1.9.0 needs API 23), OkHttp 4.12.0,
org.json (platform, plus `org.json:json` for JVM tests). No Compose, no
`tools:overrideLibrary`, no NewApi suppressions.

Evidence (local, API of the build host ≠ API 22 runtime):
- `./gradlew :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL,
  18/18 unit tests green.
- `./gradlew :app:lintDebug` → BUILD SUCCESSFUL, no NewApi errors.
- `aapt2 dump badging app-debug.apk`: package `zepigit.firefin.app`, versionCode 1,
  versionName `0.1.0-firefin`, minSdk 21, targetSdk 34, label `Firefin`,
  launchable + leanback-launchable `zepigit.firefin.app.ui.HomeActivity`,
  banner `res/drawable/tv_banner.png`.
- Fixed along the way: `sdk.dir` property escaping, OkHttp 4 `MediaType.parse`
  deprecation (error level), `Build.MODEL` null-safety, JVM-test `org.json`.

## Phase 5 — Native flows 🟡 (core slice implemented)

Implemented as real vertical slices (package `zepigit.firefin.app`):
- Data: `JellyfinClient` (OkHttp; auth header incl. Token, AuthenticateByName,
  Views, Resume/NextUp/Latest, Items browse with paging/sort, item/children,
  PlaybackInfo with DeviceProfile, session reporting start/progress/stop,
  remote sessions + commands, favorite/played toggles), `SessionStore`
  (SharedPreferences, device id persisted; no passwords stored).
- Util: `Ticks` (ms↔ticks), `Urls` (server normalization incl. subpath,
  image/direct-stream URLs, root-relative transcoding join — regression-tested).
- Images: `ArtworkPolicy` (320/640/960 classes, 480 poster height cap,
  decode buckets + clamp), `ImageLoader` (LruCache ≤32 MiB / heap/8, disk cache
  ≤64 MiB trimmed, max 2 concurrent fetches, single-axis cache keys).
- Playback: `DeviceProfile` (H.264 direct play, stereo AAC, TS/HLS transcoding,
  1280×720 + 4 000 000 bit/s ceilings), `PlaybackEngine` (Media3 ExoPlayer,
  audio focus via setAudioAttributes, becoming-noisy handling).
- UI: HomeActivity (resume/next-up/latest rows, toolbar, scroll restore),
  LoginActivity, LibraryActivity (grid, paging, sort cycle), DetailActivity
  (backdrop 960, play/resume, favorite/watched, episodes), PlayerActivity
  (SurfaceView via PlayerView, deterministic D-Pad: center play/pause,
  left/right seek 10 s, menu controller; progress every 10 s, Stopped on exit),
  SearchActivity (debounce + stale-request cancellation), SettingsActivity,
  RemoteActivity (session control Play/Pause/Stop).
- Tests: UrlsTest, TicksTest, DeviceProfileTest (ceilings + buckets),
  JellyfinClientTest (MockWebServer: login contract, PlaybackInfo body carries
  profile ceilings, transcoding URL join).

**Not yet ported (honest status — NOT feature-complete):** Live TV incl. EPG,
Seerr, downloads/offline, music browsing/audio player, books/comics reader,
DLNA casting, Quick Connect, photo viewer, trailers, next-up/still-watching
prompts, admin suite, plugin sync, subtitle/audio track selection dialogs,
parental controls/PIN, screensaver, home-section customization. These remain
legacy-only until ported; the migration must not be advertised as feature-equal
until they land.

## Phase 6 — Performance & security rules 🟡

Ported: image classes/caches/concurrency (incl. poster 480 height cap,
backdrop fallback, shared OkHttp client, retry-on-failed-rebind), no
blur/preview effects (native UI has none), stable ids actually enabled,
sort-race guard, stored-vs-effective preference split with tests
(`EffectiveDevicePreferences` — device limits exist only in the effective
type and are excluded from serialization). The legacy global TLS bypass
(`badCertificateCallback => true`) is **not** ported: the native stack uses
default certificate and hostname verification; TLS failures fail visibly.
A user-configurable trust-store path for self-hosted servers is still open.

## Review cycle FF-P5-UI-01 (firefin-ui, SHA eb315af55) → fixed in dfae8305f

11 findings applied (F1–F11), highlights: **F1 BLOCKER** — media cards were
not D-Pad focusable on API 22 (clickable ⇒ focusable only from API 26; fixed
with explicit focusable/clickable), **F2** — PlayerView consumed keys while
the controller was hidden (fixed: deterministic dispatchKeyEvent routing,
MENU toggles, UP/DOWN open audio/subtitle track dialogs), **F3** — logout
left stale Home on the back stack (fixed: CLEAR_TASK login start), plus
stable ids, per-row poster/landscape artwork classes, loading/empty/error
states, off-thread stop reporting, generation-guarded sorting, remote
deviceId self-filter and PlayPause command. Runtime-only checks (real AFTT)
remain open. Re-check of F1/F2/F3 suggested after fixes.

## Review cycle FF-P5-CORE-01 (firefin-core) → partially fixed, remainder tracked

Findings F1–F10 against SHAs 284e7202f/eb315af55 (+ re-check of dfae8305f):

**Fixed in the follow-up commit:**
- F1 (P1): `CodecProfiles.Type` used invalid enum values `VideoCodec`/
  `VideoAudioCodec` → corrected to Jellyfin's `Video`/`VideoAudio`; test now
  asserts the valid enum set (the old test pinned the wrong value).
- F4 (P1): BACK-UP was consumed when the controller was hidden → key routing
  now consumes only keys it actually handles; BACK always reaches the
  framework.
- F3 (P1, partial): `runBlocking` removed, activity scopes cancelled, stop
  reports sent off-thread; progress reports after a terminal stop are dropped
  (SessionReporter guard). Ordered per-generation event serialization with
  cancellable HTTP remains open (P2-grade follow-up).
- F2 (P1, contract): progressive TS transcodes are unseekable → implemented
  the explicit restart contract: seek rebuilds the transcode URL with
  `StartTimeTicks` and rebases the offset (`Urls.withStartTimeTicks`),
  progress reports `offset + player position` (single application of resume).

**Open (documented, not fixed yet):** F5 (report on player-state listeners,
buffering vs. pause, MediaSourceId/PlayMethod reporting), F6 (disk-cache
decode bypasses sampling limits), F7 (uniform HTTP error handling incl. 401
surfacing), F8 (media source selection by Support* flags), F9 (transport/
redirect token policy incl. cross-origin 307 on login; OkHttp strips
Authorization cross-origin, query tokens are not), F10 (playback request
lifecycle bound to activity), plus HLS negotiation decision (current profile
negotiates progressive TS with an explicit restart contract instead).
These are tracked for the next slices before a release candidate.

## Native CI (Phase 7) ✅ first green run

`.github/workflows/android.yml` — PR/push/dispatch + `v*` tag release path:
`testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease` (R8,
debug-signed fallback without secrets). First full run on `firefin/dev`
(36869237388): **success in 5m25s** incl. release/R8. Fixed on the way:
`gradlew` exec bit. Release publication still requires FIREFIN_* signing
secrets (open, user-provided). Legacy `build.yml` continues as baseline.

## Subagent routing preflight (§6.1)

Session after restart, roles from `.zcode/agents/` registered globally in
`~/.zcode/agents/`:

| Role | Model (as configured) | Probe | Status |
|---|---|---|---|
| firefin-review | claude-opus-5-5, medium | FF-P1-01/02 real reviews | CONFIGURED_AND_PROBED |
| firefin-research | gemini-3.8-flash, high | file read probe | CONFIGURED_AND_PROBED |
| firefin-ui | claude-sonnet-5-5, high | ArtworkPolicy parity check (found: poster height, clamp) | CONFIGURED_AND_PROBED |
| firefin-core | gpt-6.1-sol, high (272000 worker ctx per config) | DeviceProfile/URL review (found: subpath join bug, fixed) | CONFIGURED_AND_PROBED |
| firefin-build-qa | gpt-6-luna, max | gradle/YAML read probe | CONFIGURED_AND_PROBED |

Provider-internal model/effort attestation is not exposed by the harness;
internal model identity is therefore unattested (documented limitation, no
self-declaration accepted). Max 3 concurrent subagents respected.

## Phase 1 correction log (post-review)

- FF-P1-01 REVIEW PASS; F1 (LOW, leaked GIT_COMMITTER_* onto legacy replay)
  fixed by redoing the rebase with `--committer-date-is-author-date`; FF-P1-02
  re-check: REVIEW PASS. Corrected tree IDs: `fbd4a3c^{tree}` =
  `79eb22b2fb506ccd67cf34c8d89cb0234199097f` (delta commit target), final main
  tree = `94a6903d88e2e4089da8f6ecf60a67e6f9f6ac09` (both preserved).
- Branch layout: development on `firefin/dev` in the main worktree;
  `repair-main`/`repair-legacy` are frozen references of the reconstruction
  (pushed as `origin/repair/history-main`). `origin/main` is still the old
  squash lineage; replacement requires explicit user approval.

## Phase 2 — Legacy CI status

- Workflow accepted; jobs now run (1m27s failure vs 0 s validation failure
  before). First real failure: `widget_test` pumping the whole app
  (StateError from platform-channel services) — pre-existing, now exposed and
  fixed (kAppName = 'Firefin', test pins identity instead of booting DI).
- `repair/history-main` CI failures are expected: that branch preserves the
  historical tree including the old broken workflow.

## Phases 7–8

Open: native CI workflow (Phase 7), Flutter legacy removal + docs (Phase 8),
published-main history replacement (needs explicit user approval), AFTT
hardware tests (device ADB access not yet available), release signing keys
(not available; CI release path will require FIREFIN_* secrets).
