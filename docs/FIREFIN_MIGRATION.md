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

## Phase 4 — Native API 22 base 🟡

Toolchain: JDK 17, Gradle 8.10.2, AGP 8.7.3, Kotlin 2.0.21, compileSdk 35,
targetSdk 34, minSdk 21, Media3 1.8.1 (last line with minSdkVersion 21; 1.9.0
needs API 23), OkHttp 4.12.0, org.json (platform). Dependency matrix with
API-22 evidence: see `docs/DEPENDENCY_MATRIX.md`.

## Phases 5–8

See the phase checklist at the top; updated as slices land.
