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
- Development baseline for this audit: `e32fcfbf17e526bee0ecf49d6d23ca4061e24582`.
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
| Gate 4: pinned dependencies, locked/verified resolution, stock API22 fixture playback | Root :app; dependency graph, Lint, instrumentation on API22 | OPEN |
| Gate 5A: URL/subpath/HTTPS/explicit HTTP, login/logout/session, Quick Connect and switching | data, auth screens, MockWebServer + UI tests | OPEN |
| Gate 5B: all home/libraries/detail/search/favorite/filter/sort/page flows, focus/scroll | ui/ + actual feature matrix and API22 tests | OPEN |
| Gate 5C: source/track negotiation, HLS auth, resume/seek, reports, next episode and cleanup | playback/ + delayed-request/media fixtures and device smoke | OPEN |
| Gate 5D: deterministic player controls, remote/media keys, focus, audio focus and lifecycle | Instrumented D-Pad and suspend/resume tests | OPEN |
| Gate 5E: all other actually active AFTT features | Native path and tests for each matrix row | OPEN |
| Gate 6: 720p/4 Mbit incl audio, lower limits, 320/640/960, bounded caches/decodes/queues | Serialized profile/URL tests; image cold/warm cache stress | OPEN |
| Gate 6: stored/effective preferences and end-to-end plugin sync | Preferences and protocol fixture tests; no unused-only type proof | OPEN |
| Gate 6: trust chain/hostname, API22 CA policy, redirect/token isolation | Transport TLS and redirect negative tests | OPEN |
| Gate 7: actionlint, wrapper/dependency verification, tests, strict debug/release lint, both builds | android.yml with mandatory reports/APKs | OPEN |
| Gate 7: separate protected signing, required secrets, stable cert, tag/full SHA, v1, checksums/source | release.yml, verifier, protected environment and actual run/release | OPEN |
| Gate 8: native-only cleanup after parity; updated public English docs/templates | README, BUILDING, COMPATIBILITY, CHANGELOG, SECURITY, notices/templates | OPEN |
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
| Home resume/next-up/latest/libraries/favorites | Native partial, formerly limited to four views | All libraries, empty/error/retry, refresh without lost focus |
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
| FF-AUDIT-BUILD-01 | isolated e32fcfbf snapshot | Running: tests/lint/APK/CI evidence audit |
| FF-AUDIT-UI-01 | isolated e32fcfbf snapshot | Running: reachability/parity and claimed fixes |
| FF-AUDIT-RESEARCH-01 | isolated e32fcfbf + installed SDK/official sources | Running: API22 fixture prerequisites and timeline contract |

Confirmed current audit defects at e32fcfbf:
- `lint.abortOnError=false` allowed at least nine Media3 opt-in errors to appear
  green. No claim of clean lint or API22 runtime may derive from that exit code.
- CI uploaded `app-release.apk`, while unsigned Gradle emits
  `app-release-unsigned.apk`; upload missing-file behavior was not fail-closed.
- Native signing settings were not wired at all. Legacy tag workflow could
  publish debug-signed artifacts. Both release paths need fail-closed replacement.
- `local.properties` was repeatedly reintroduced because the ignore pattern
  covered only android/local.properties. Root file is being untracked and ignored.
- TS resume added an offset without requesting the matching server offset,
  so the previously claimed "exactly once" timeline was not true.
- Terminal session set does not serialize already queued/in-flight reports and
  grows for the whole process. Cleanup and reporting need a per-playback actor.
- Image disk hits decode originals without bounded sampling/concurrency.
- URI prefix matching, explicit cleartext consent and HLS origin authentication
  are not yet secured; network errors and mutation statuses are inconsistent.
- Stored/effective types and their tests are not proof of actual sync isolation.

Current corrective work (not yet accepted): strict Lint with explicit Media3
unstable-API opt-ins; version.properties; unsigned PR release checks; required
production signing flag; exact APK checker; separate manual main-only protected
release; legacy validation-only pipeline without publishing.

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

## Audit work in progress

- Strict pipeline and fail-closed verifier are being validated locally on the revised working tree; no green result is claimed until the exact SHA is committed and CI is rerun.
- `ServerTransport` and URL hardening are implemented in the working tree but not yet integrated through every existing JellyfinClient/image/player call site; this remains an open integration gate.
- API22 stock packages are available but not installed; no emulator smoke result exists.

## External gates (not waived)

- No explicit approval to replace the published main history has been given.
- Production signing key, pinned signing certificate and protected release
  environment must be available before a release can succeed; no debug-key release.
- API22 stock emulator and actual AFTT are separate evidence. Neither was proven
  in the preceding work. Hardware release approval remains separate from x86 smoke.
- Test account credentials/tokens and raw server content are private; never copy
  them into sources, CI, prompts or public documentation. Login success alone is
  not a complete playback test.
