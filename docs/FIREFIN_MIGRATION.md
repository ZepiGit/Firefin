# Firefin migration: acceptance ledger

## Current objective and authority

Continue the existing repository as Firefin, with a native Kotlin/Views client for
AFTT / LY73PR, Fire OS 5.2.8.0, Android API 22, ARMv7 and 1 GiB RAM. Keep minSdk 21.
Application ID, namespace and base package are `zepigit.firefin.app`.
The current executing developer is GPT-6-astra (user instruction); configured
Firefin subagents research and independently review, never implement production
code. Maximum three supporting agents and one heavy build/emulator per host.

**Status: incomplete, not release-ready.** The previous completion labels were
not acceptance evidence. This ledger supersedes the inaccurate earlier claims
of clean lint, all review findings fixed, protected signing, bounded disk decode,
and completed API-22 validation. No final migration, hardware or release approval
is claimed. No Flutter cleanup is permitted while active features lack parity.

## Current verification snapshot

Native identity: `zepigit.firefin.app`, `0.2.0-firefin`, versionCode `3001000`
(`version.properties`; above the legacy `3000028`), minSdk 21, targetSdk 34.

Evidence basis: branch `firefin/dev`, commit `cacd4efc90f2c1a5043fe43503b005fe2e35036e`
and later follow-up commits (see commit history). Native Android CI run
`36926364003` = success on `cacd4efc9`; the legacy validation workflow is
path-filtered and runs only when legacy sources change. Later commits (incl. the native fixes in `16b6911e5`) are NOT covered by
run `36926364003`; their CI results are in the GitHub Actions run history for each SHA.

| Evidence | Status |
|---|---|
| Clean local build: 40 unit tests, strict lint 0 errors / 58 warnings, R8 release APK (unsigned) | Verified locally on `firefin/dev` HEAD |
| API 22 stock x86 emulator install/launch smoke: package ID `zepigit.firefin.app`, `LoginActivity` shown, no `FATAL` | Verified manually; login, browsing and playback not exercised |
| CI (`android.yml`) on `cacd4efc9` | Success (run `36926364003`); later commits rerun CI on push |
| Physical AFTT (ARMv7) run | **Absent** |
| Signed release (protected `release.yml`, production key, pinned fingerprint) | **Absent**; workflow never run |
| API 22 playback/D-Pad smoke; media redirect behavior on device (JVM test present); feature parity (Quick Connect, all library types, Live TV, Seerr, downloads, music, books, DLNA, admin, plugin sync) | **Open** |

The old "18/21 tests" and "5m25s" CI figures describe earlier trees and are not
current evidence.

## Verified starting points and history

- Original main: `9f46e1e34469a0f5ac2da438bc3649fba08ea9c1`.
- Reconstructed history: `3af0e6b728ba336c30eb6c0dbd901b8c7b508682`.
  FF-P1-01 and FF-P1-02 independently reviewed tree identity, ancestry and authors.
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
  Current evidence basis: `firefin/dev` HEAD — `cacd4efc90f2c1a5043fe43503b005fe2e35036e`
  and later follow-up commits; Native Android CI run `36926364003` on
  `cacd4efc9` = success. Legacy validation workflow runs on demand only
  (path-filtered).
- External verified audit bundle: `../firefin-audit/e32fcfbf17e5/start.bundle`,
  SHA256 `0f49c5ca64ef87b7a9dc02118ce9e6d99631fa59575f08611c894c06040a3469`.
  Read-only git-archive snapshots for reviewers live alongside it, outside Git.
- Published main is still the original lineage. Replacement requires separate
  explicit approval, fresh remote verification and an exact `--force-with-lease`.
  Existing tags/releases must not move. No approval is inferred from `/goal`.
- Repository has been renamed in place to `ZepiGit/Firefin`; origin uses the new
  URL. The repository-ID assertion still needs current API evidence in the final audit.

## Prompt-to-artifact acceptance checklist

A file or green job is evidence of existence, not of correct behavior. OPEN means
unproven or incomplete; HISTORICAL means evidence applies only to its named SHA.

| Requirement / gate | Artifact or required evidence | Status |
|---|---|---|
| Read full implementation order, repo rules, current main/branches/PR/comments | Original order, repo status; refresh PR/comment metadata before publication | Partial |
| Gate 0: backup, actual features, CI logs | Verified external bundle; reachability matrix below; FF-AUDIT-BUILD/UI | Partial |
| Gate 1: original upstream, import delta, per-commit attribution and final tree identity | repair-main 3af0e6b; FF-P1-01/02 | Historical pass |
| Complete old-to-new mapping including eight PR commits | Regenerate mapping in this document against final frozen refs | OPEN |
| Gate 2: syntax accepted, real legacy jobs and build status | Runs 36852773811 (0 jobs), 36870222276 (legacy success); independent audit | Historical only |
| Gate 3: rename same repo, app identity, icons/banner, credits, install instructions | GitHub repository ID; Gradle/manifest/APK; LICENSE/notices/docs | Partial |
| Gate 4: pinned dependencies, locked/verified resolution, stock API22 fixture playback | Root :app; dependency graph, Lint, instrumentation on API22 | Partial: versions pinned, strict lint, API22 x86 install/launch smoke; no lock/verification metadata, no instrumented or fixture playback |
| Gate 5A: URL/subpath/HTTPS/explicit HTTP, login/logout/session, Quick Connect and switching | data, auth screens, MockWebServer + UI tests | Partial: URL/subpath/HTTPS-default and password login unit-tested; no cleartext consent, no Quick Connect, no switching |
| Gate 5B: all home/libraries/detail/search/favorite/filter/sort/page flows, focus/scroll | ui/ + actual feature matrix and API22 tests | OPEN |
| Gate 5C: source/track negotiation, HLS auth, resume/seek, reports, next episode and cleanup | playback/ + delayed-request/media fixtures and device smoke | OPEN |
| Gate 5D: deterministic player controls, remote/media keys, focus, audio focus and lifecycle | Instrumented D-Pad and suspend/resume tests | OPEN |
| Gate 5E: all other actually active AFTT features | Native path and tests for each matrix row | OPEN |
| Gate 6: 720p/4 Mbit incl audio, lower limits, 320/640/960, bounded caches/decodes/queues | Serialized profile/URL tests; image cold/warm cache stress | OPEN |
| Gate 6: stored/effective preferences and end-to-end plugin sync | Preferences and protocol fixture tests; no unused-only type proof | OPEN |
| Gate 6: trust chain/hostname, API22 CA policy, redirect/token isolation | Transport TLS and redirect negative tests | Partial: default trust (no bypass in code), REST redirect and header-only token tests; no TLS negative tests, no media-client redirect test, no API22 CA test |
| Gate 7: actionlint, wrapper/dependency verification, tests, strict debug/release lint, both builds | android.yml with mandatory reports/APKs | Partial: workflow implemented, equivalent local run passes; CI not run on the current tree; no dependency verification |
| Gate 7: separate protected signing, required secrets, stable cert, tag/full SHA, v1, checksums/source | release.yml, verifier, protected environment and actual run/release | OPEN: workflow implemented, never run; secrets, pinned fingerprint and environment reviewers unconfirmed |
| Gate 8: native-only cleanup after parity; updated public English docs/templates | README, BUILDING, COMPATIBILITY, CHANGELOG, SECURITY, notices/templates | Partial: the seven English docs updated for the current tree; issue/PR templates, `FIRETV32-README.md`, `RELEASE_NOTES_*` and the German UI strings are untouched |
| Final SHA: repeat tests/reviews/CI/APK after cleanup; no stale-run substitution | Exact commit/tag/run/artifact evidence | OPEN |
| Existing authorized server and hardware only; no new server/emulator platform | Fixtures/MockWebServer + stock SDK images; test account kept private | Required |
| All five supporting roles, correct routing/effort/tools/context, isolated exact SHA reviews | Preflight table and review ledger below | Partial |
| Independent final architecture/security/UI/core/build reviews; developer fixes and rechecks | Review ledger with exact scopes/SHAs and outputs | OPEN |
| Public maintainer reply only after proven implementation | Existing thread reply, not yet posted | OPEN |

## Actual feature matrix

The baseline inventory found the following routes and candidate flows. A route
alone is not proof of Fire-TV reachability. FF-AUDIT-UI-01 is verifying gates and
entry points. Until then no potentially active area is removed as "out of scope".

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
| Seerr discovery/search/media/people/request/status/config | Legacy present, native missing | Server-configured optional integration |
| Downloads/offline/storage management | Legacy present, native missing | Quota/cancellation/offline navigation/resume |
| Books/comics PDF/EPUB/CBZ/CBR/7z, photos/slideshows | Legacy present, native missing | Format support/security, native D-Pad reader |
| DLNA discovery/control and remote server sessions | DLNA missing; remote sessions partial | Scope-safe native reuse, actual remote commands |
| Plugin synchronization | Native stored/effective types are not wired | End-to-end protocol parity, local caps never exported |
| Settings, parental/PIN, screensaver, ratings, navigation and home customization | Native settings minimal | Port reachable configuration and authorization |
| Admin suite | Routes present, AFTT entry/gates being verified | Do not silently discard active functionality |
| Google Cast, PiP, hero/preview/blur/focus expansion | Explicitly disabled on AFTT | Keep disabled, no added GMS or expensive effects |

## Independent review and remediation ledger

| Review | Scope / SHA | Result and current interpretation |
|---|---|---|
| FF-P1-01/02 | History at 226ff14 then corrected 3af0e6b | PASS, valid for history only |
| FF-P5-UI-01 | app at eb315af55 | F1–F12; prior claim "all fixed" withdrawn; recheck required |
| FF-P5-CORE-01 | app at eb315af55 and dfae8305 | F1–F10; unsafe TS offset and report races remain at e32fcfbf |
| FF-AUDIT-BUILD-01 | isolated e32fcfbf snapshot | Findings below; no final result recorded here; recheck on the final SHA |
| FF-AUDIT-UI-01 | isolated e32fcfbf snapshot | Reachability/parity findings feed the feature matrix; no final result recorded here |
| FF-AUDIT-RESEARCH-01 | isolated e32fcfbf + installed SDK/official sources | API22 fixture prerequisites and timeline contract; no final result recorded here |
| FF-DOCS-SONNET-01 | working tree on top of 8d53ab583 | Documentation accuracy review; observations at the end of this section |

Defects found at e32fcfbf and their state in the current tree (none is accepted
until rechecked on the final committed SHA):

| Defect at e32fcfbf | Current state |
|---|---|
| `lint.abortOnError=false` hid Media3 opt-in errors | `abortOnError = true`; local strict lint 0 errors / 58 warnings; not yet in CI |
| CI uploaded `app-release.apk` but unsigned Gradle emits `app-release-unsigned.apk`; uploads not fail-closed | CI uploads `app-release-unsigned.apk` with `if-no-files-found: error`; verifier fails closed |
| No native signing wiring; legacy tag workflow could publish debug-signed artifacts | `-Pfirefin.productionSigning=true` plus `FIREFIN_*` inputs; protected manual `release.yml`; legacy workflow is validation-only. Release path never run |
| `local.properties` repeatedly reintroduced | Root file untracked and `/local.properties` ignored; CI rejects tracked copies |
| TS resume added an offset without a matching server offset | HLS transcode profile; start position applied once via the Media3 start position. Non-HLS TS streams still use the offset/restart path. Not verified on device |
| Terminal session set grew for the whole process; queued reports not serialized | One ordered `SessionReporter` per playback (Playing, Progress, Stopped) with its own scope, cancelled with the player; no stress or delayed-request test |
| Image disk hits decoded originals without bounded sampling | Disk and network paths now decode with bucket sampling (RGB_565), 2 network fetch permits, 32 MiB memory and 64 MiB disk caps. Disk hits still read the whole file without a permit; no cold/warm stress evidence |
| URI prefix matching, cleartext consent, HLS origin authentication | Origin and path-segment binding in `ServerTransport`/`Urls`; header-only auth; same-origin media redirects. Cleartext consent still open; media-client redirect path untested |
| Stored/effective types are not proof of sync isolation | Unchanged: types and tests only; plugin sync not implemented |

Corrective work in the tree: strict lint with explicit Media3 unstable-API
opt-ins; `version.properties`; unsigned release checks in CI; required
production signing flag; exact APK verifier; separate manual main-only protected
release; legacy validation-only pipeline without publishing; REST/media client
split and per-playback reporter (landed by `56134a362`; historical note).

Observations from the documentation review (code not changed by that task; each
needs a developer decision and a recheck):

- `JellyfinClient.VERSION` is `0.1.0`. It is sent in the `Authorization`
  header and shown in Settings > About, while the APK is `0.2.0-firefin`.
- `ServerTransport.mediaHttp` is built from the same mutable `OkHttpClient.Builder`
  after the REST interceptor was added to it, so it appears to run the REST
  interceptor too. That would force `Accept: application/json` on media and
  image requests and make the `*/*` branch unreachable. Needs a test.
- The image loader uses the media client (no call/read timeout, 10 s connect
  only). A stalled artwork body can hold one of the two fetch permits
  indefinitely.
- The `mediaHttp` redirect loop and header policy have no unit test; the HLS
  header-auth test exercises the REST client (`transport.http`).
- `HomeActivity` calls `loadHome()` in `onCreate` and again in `onResume`, so
  the first start issues two concurrent loads; `homeLoaded` is written but never
  read.
- The player does not side-load external subtitle files although the device
  profile declares external SRT/VTT.
- `Urls.stripCredentials` is unused by production code.
- The login screen hint suggests an `http://` address; there is no cleartext
  consent. Platform defaults block cleartext on API 23+ for this target SDK.

## Agent routing evidence and limitations

Existing Agent tool successfully launched build-qa, ui and research for this
new audit, using the existing named profiles and read-only snapshot prompts.
The installed loader maps `thoughtLevel` to `modelSelection.options.reasoningLevel`.
No provider-internal weight/effort attestation is available.

| Role | Profile model / effort observed | Current evidence |
|---|---|---|
| firefin-build-qa | openai/gpt-6-luna / max | Probe + independent baseline build requested |
| firefin-ui | anthropic/claude-sonnet-5-5 / high | Probe + static parity review requested |
| firefin-research | new-provider/gemini-3.8-flash / high | Probe + official source/SDK checks requested |
| firefin-review | anthropic/claude-opus-5-5; project medium, user high | Conflicting scopes; effective setting must be resolved before claiming medium |
| firefin-core | openai/gpt-6.1-sol / high | 272000 appears in description only; worker-specific context not proven |

The installed profile parser has no context-window field; inventing one in
Markdown would not configure it. No global 272000 context override, new bridge,
substitute model or silent effort fallback is authorized. Pending configuration
requirements remain visible rather than being counted as successful probes.

## Historical audit notes (superseded)

- Strict pipeline and fail-closed verifier passed an equivalent local run on the
  working tree (29 tests, lint 0 errors / 58 warnings, R8 release APK). No green
  CI result is claimed until the fixes are committed and `android.yml` runs on
  that exact SHA. (Historical: this state preceded `56134a362`/`cacd4efc9`; the current
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
  them into sources, CI, prompts or public documentation. Login success alone is
  not a complete playback test.


## Final overall review cycle FF-FINAL-OVERALL-01 (firefin-core, gpt-6.1-sol high) → fixed

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

Open items (unchanged, explicitly not claimed): API 22 playback/D-Pad smoke on
the stock emulator, real AFTT hardware evidence, production signing run, and
full legacy-feature parity (Live TV, Seerr, downloads, music, books, DLNA,
Quick Connect, admin, plugin sync).
