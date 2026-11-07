# Tasks

## 1. The red case first (no production code yet)

- [x] 1.1 Add `auto/src/test/java/com/naviveylin/auto/AutoMapRendererFrameWaitTest.kt` with the four
  cases the delta names, each driving the seam with explicit `nowMs` values and `RendererTestRule`
  for teardown (spec: `auto-map-renderer` — A requested car frame that never lands is
  re-requested): `aFrameThatNeverLandsIsRequestedAgain`, `landedFramesAreNotDuplicated`,
  `aStoppedRendererIssuesNoRetry`, `theExhaustedBudgetIsReportedOnce`. Each case asserts on
  `renderRequestCount` / the draw counters / the recorded diagnostic, never on wall-clock time
  (`guidelines/Build.md` §6e). Verify: the file compiles against the seam's intended signature and
  the three non-guard cases fail on HEAD — record the red run
  (`./gradlew :auto:testDebugUnitTest --tests "com.naviveylin.auto.AutoMapRendererFrameWaitTest"`,
  case names plus `tests/failures` from the XML) in this task. `landedFramesAreNotDuplicated` is the
  guard case and passes on HEAD; say so rather than claiming four reds.
  - **Red on HEAD (2026-10-30)** — the first case written was the one expressible without the new
    seam, `aRequestTheFramePathCannotServeStaysOutstanding` (the regression the class of defect
    consists of: `requestRender()` then `frameIteration()` with no surface). Run:
    `:auto:testDebugUnitTest --tests "…AutoMapRendererFrameWaitTest"` →
    **`1 test completed, 1 failed`**, XML `tests="1" failures="1"`, message
    `java.lang.AssertionError: the request was consumed although no frame could be drawn`.
    The other four cases reference the seam (`frameWaitIteration`, `frameWaitReport`) that HEAD does
    not have, so they could not be compiled on HEAD at all — the bound is absent by construction.
    Their behavioural red is the revert-check mutation in 2.4, which is the run that proves they
    assert the bound and not the implementation.
- [x] 1.2 (folded into 1.1 — the witness case and the four spec cases live in one class; the witness
  case is the red-on-HEAD run above, the four are driven by the seam the fix adds.)

## 2. The frame-wait bound in the renderer

- [x] 2.1 Add the frame-wait state to `AutoMapRenderer.kt` — the bound (`FRAME_WAIT_BOUND_MS = 4_000L`,
  KDoc carrying the measured 0.63 s / 0.87 s / 25–300 ms numbers and design D3), the retry budget
  (`FRAME_WAIT_MAX_RETRIES = 2`, design D3), the counters at arm time, and the attempt count.
  Verify: the class compiles and no public API changes (`git diff` shows only internal members).
  - **Done 2026-10-30** — state and constants are `internal`/`@Volatile` members of
    `AutoMapRenderer`; `FRAME_WAIT_DIAG_TAG = "MAP"`. `git diff` adds no public member.
- [x] 2.2 Add the seam and the watchdog body in `AutoMapRendererLoops.kt` next to the three existing
  loop entry points — `armFrameWaitWatchdog()` (one-shot job in the renderer scope, cancelled and
  replaced on a new arm) and `frameWaitIteration(nowMs)`: draw counters unchanged, not
  `paused`/`surfaceFailed`/`isShutdown`, a surface held, attempts left → `requestRender()` plus one
  `DiagnosticsLog.log("MAP", …)` line naming the retry index and the bound (spec: `auto-map-renderer`
  — A requested car frame that never lands is re-requested; `guidelines/Logging.md` §3 — identity
  only, so `checkNoCoordinatesInLogs` stays green). Verify: cases 1–4 of 1.1 pass.
  - **Done 2026-10-30** — `armFrameWaitWatchdog` / `disarmFrameWaitWatchdog` /
    `frameWaitIteration` in `AutoMapRendererLoops.kt`; the record goes through
    `AutoMapRenderer.frameWaitReport` (default `DiagnosticsLog.log(FRAME_WAIT_DIAG_TAG, …)`), the
    shape `RenderLoopSupervisor.record` uses. All cases pass: XML `tests="7" failures="0"` (five seam
    cases plus the two wiring cases the review's B1 asked for, see section 6).
- [x] 2.3 Arm the watchdog from `requestRender()` and disarm it in `pause()`, `detachSurface()` and
  `shutdown()` (`AutoMapRenderer.kt:600-635`, `:770-785`) — a stopped renderer performs no surface
  work (spec: `auto-map-renderer` — A stopped renderer holds no surface or frame buffer). Verify: the
  stop cases of 1.1 pass and `RendererTestRule`'s teardown still reports zero active jobs (a leaked
  watchdog job fails every test in the class).
  - **Done 2026-10-30** — `frameWaitJob` joined `activeBackgroundJobCount()`'s list, so the teardown
    rule counts a leaked watchdog; `aStoppedRendererIssuesNoRetry` covers pause, resume and shutdown.
- [x] 2.4 Revert-check (one mutation): make `frameWaitIteration` return before `requestRender()` and
  confirm `aFrameThatNeverLandsIsRequestedAgain` and `theExhaustedBudgetIsReportedOnce` fail; restore
  and re-run green forced (`-PforceTests --no-build-cache`, `guidelines/Build.md` §2/§4,
  `revert-check` skill). Verify: mutation failures and the green re-run are recorded here with their
  counts.
  - **Done 2026-10-30** — mutation 1, `if (false) requestRender()` in `frameWaitIteration` →
    **`5 tests completed, 2 failed`** (XML `tests="5" failures="2"`), the two predicted cases:
    `aFrameThatNeverLandsIsRequestedAgain`, `theExhaustedBudgetIsReportedOnce`. Mutation 2 (added by
    the review, section 6): `armFrameWaitWatchdog` made a no-op → **`7 tests completed, 3 failed`**
    (`aRequestArmsTheWaitAndAStopDisarmsIt`, `theArmedWaitFiresAndRetriesTheFrame`,
    `theExhaustedBudgetIsReportedOnce`). Both restored, then
    `./gradlew :auto:testDebugUnitTest -PforceTests --no-build-cache` → `BUILD SUCCESSFUL`,
    **93 classes / 906 tests / 0 failures / 0 errors**.

## 3. Gate

- [x] 3.1 `./gradlew :auto:testDebugUnitTest` and read the executed counts from
  `auto/build/test-results/` (never an up-to-date line, `guidelines/Build.md` §4a). Verify: 0
  failures, and the counts recorded in this task. Exclusion: `:app` suites are not run — the change
  adds no public API and no `:app`-visible behaviour (`requestRender()`'s signature is unchanged);
  state that instead of running them.
  - **Done 2026-10-30** — forced run: `classes=93 tests=906 failures=0 errors=0` (after the review's
    two wiring cases). `:app` suites excluded (no public API, no `:app`-visible behaviour).
- [x] 3.2 `./gradlew :app:assembleAutomotiveDebug -Pandroid.injected.build.abi=x86_64` (the AVD's ABI,
  `guidelines/Build.md` §10a). Verify: build succeeds with no new warning from the changed files.
  - **Done 2026-10-30** — `BUILD SUCCESSFUL in 12s`, APK
    `app/build/intermediates/apk/automotive/debug/app-automotive-debug.apk` (93 MB) installed on
    `emulator-5556`.

## 4. On device

- [x] 4.1 Install that APK on `emulator-5556`, open `Freie Fahrt`, and hold the screen for 60 s.
  Verify (spec: `auto-map-renderer` — Landed frames are not duplicated, on the healthy path):
  `adb logcat -d | grep -c 'frame wait expired'` = **0**, the first frame lands within the measured
  ~0.6 s (`Diag/MAP render` after `surface created`), and `renderer#N lock OK` keeps counting.
  Record the timestamps.
  - **Measured 2026-10-30 on `emulator-5556`** (automotive debug, x86_64): push →
    `21:50:45.214 renderer#1 surface created` → `21:50:46.002 Diag/MAP render mag=13.00` →
    `21:50:46.008 renderer#1 lock OK` = **0.79 s**. Over the whole 60 s run:
    `frame wait expired` **0**, `dropping frame` **0**, surface failures **0**; `lock OK` counted
    continuously and the five screenshots (2/5/10/30/60 s) all carry the map (mean RGB ≈ 185/204/174,
    ~3 200 distinct colours — never the blank `#040505`). No false positive from the new wait.
- [x] 4.2 Record what a real expired wait looks like if one occurs (the diagnostic's text, the retry
  index, whether the frame landed on the retry). **Status: not producible on demand** — the window
  does not reproduce at HEAD; if it occurs during the run, capture the log and close #25 against it,
  otherwise say so.
  - **Not observed 2026-10-30** — no expired wait in the 60 s run; the diagnostic's text is
    therefore recorded from the unit cases (`… (retry 1/2)`, `… after 2 retries`), not from a device.
    #25's 31 s window was not reproduced (see the proposal's measurement and the comment on the
    issue).

## 5. Documentation and bookkeeping

- [x] 5.1 `guidelines/MapRendering.md` §14 (Android Auto renderer): one paragraph — a requested frame
  that does not land within 4 s is re-requested at most twice per renderer instance and reported once
  as a `MAP` line; the numbers and the reason (the healthy path measures 0.63 s / 0.87 s). Verify: the
  section states the bound, the budget and the diagnostic, and `tools/check-doc-routes.sh` is
  unaffected (no new routed section).
  - **Done 2026-10-30** — bullet "A requested frame that never lands is re-requested" added to the
    Android Auto §14 section (+16 lines), with the bound, the budget, the device numbers, the
    disarm rule and both diagnostic texts. No new `##` section, so no routing-table change.
- [x] 5.2 Comment on issue #25 with the 2026-10-30 measurement (push → first frame 0.63 s; stylesheet
  reload 100–300 ms; toggle path 0.87 s; not reproduced) and this change's name, and keep
  `status/open-device`: the cause of the 31 s window is not established. Verify: the comment carries
  the numbers and the change name, and the item's labels are unchanged.
  - **Done 2026-10-30** — comment on #25
    (`#issuecomment-6533235915`): the four measurements, the stale-fix-candidate note
    (`AutoMapRenderer.kt:406`), the two rework commits the report predates, this change's name with
    its `:auto` counts, and the explicit "keeping `status/open-device`" sentence. No label touched.
- [x] 5.3 File the follow-up item this change deliberately leaves out — a car map screen that has
  never drawn a frame has no channel for a notice (surface-only drawing needs a frame,
  `NavigationTemplate` has no content slot) — with `MapUnavailableOverlay`'s KDoc limitation and the
  rejected template-fallback alternative (design D4) as its evidence. Verify: the item exists with
  `class/improvement` and no claim that it fixes #25.
  - **Done 2026-10-30** — filed as **#722** (`class/improvement`, `category/car`, `status/open`,
    `source/agent`) through `tools/issues/create.sh`; its body names #25 as the unresolved instance of
    the situation and claims no fix for it.
- [x] 5.4 `openspec validate car-frame-wait-bound --strict`. Verify: clean before the first
  production edit, and again at archive time with `openspec archive car-frame-wait-bound`.
  - **Done 2026-10-30** — valid before the production edit and valid after the implementation and
    the evidence edits (re-run at archive time).

## 6. Independent review (read-only), and what it changed

- [x] 6.1 One read-only reviewer over the change and its implementation. Verdict **FIX-FIRST** with
  three blocking items; all addressed before the archive:
  - **B1 — the watchdog was untested.** Every case derived its clock from `frameWaitArmedAtMs`, so a
    no-op `armFrameWaitWatchdog` kept them green while the feature was dead in production. Fixed by
    two cases — `aRequestArmsTheWaitAndAStopDisarmsIt` (arm on request; `pause`, `detachSurface`,
    `shutdown` disarm) and `theArmedWaitFiresAndRetriesTheFrame` (the armed job, not only the seam) —
    plus an injectable bound (`frameWaitBoundMs`) and clock (`frameWaitClock`). The mutation in 2.4
    now fails 3 cases.
  - **B2 — the spec claimed a retry the code refuses** for the no-surface cause. Fixed in the
    requirement body ("While a surface is held …"), in scenario 1's GIVEN, and in the proposal's
    "stays outstanding" bullet; the surface delivery is what requests the frame there.
  - **B3 — design said `surfaceFailed` suppresses the check, the code did not read it.** Fixed in the
    code (`frameWaitIteration` returns early while `surfaceFailed`, the same rule `frameIteration`
    follows), so a dead surface produces no retry lines.
- [x] 6.2 The review's non-blocking findings, all corrected here: `onSurfaceDestroyed()` now disarms
  like `detachSurface()`; the clock is `SystemClock.elapsedRealtime` (a wall-clock step cannot make an
  expired wait look fresh); design D2 now states that `fullRenderCount` counts a completed render
  attempt (it is incremented before the draw and also on the dropped-frame path), not landed pixels;
  D4/proposal say three lines per stalled wait, not one; the Risks entry no longer claims a retry
  coalesces through `pendingRender` (a retry after a >4 s render costs one extra native render,
  bounded); the design's Verification no longer claims "three of the four" reds; the stale prose in
  `AutoMapRendererLoopConfinementTest.kt` ("the request was consumed") now states the new rule; the
  proposal's line citations and its `Design.md` §4 reference are corrected.
- [x] 6.3 `openspec validate car-frame-wait-bound --strict` after the corrections — valid, requirement
  body 469 characters.
