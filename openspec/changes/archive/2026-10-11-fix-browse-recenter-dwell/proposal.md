# Proposal

## Why

In phone BROWSE mode the re-center button does not appear when the app starts with a GPS fix on a
viewport that is not centered on the vehicle, and it also fails to appear after a quick flick pan: the
appear edge is armed but never completes. Root cause: the dwell that damps the appear edge is evaluated
**only when the UI state emits**, and completing it requires a *later* emission that may never arrive.

- `MapCanvasViewModel.refreshBrowseReCenter` (`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt:3612`)
  records `browseReCenterBeyondSinceMs` when the offset first exceeds `RECENTER_SHOW_OFFSET_PX`
  (`:3634-3638`) and flips `browseReCenterVisible` only on a later pass when
  `now - since >= RECENTER_DWELL_MS` (`:4673`).
- Its single caller is one collector over the UI state (`:1334-1336`). Nothing schedules that later pass.
- Every remaining emitter is user- or fix-driven and goes quiet exactly in the failing case: the
  location provider is silent at standstill (`LocationService.MIN_DISTANCE_M = 5.0f`, `shouldEmit`,
  `LocationService.kt:840-857`), the fix-quality pipeline publishes only on a tier change
  (`distinctUntilChanged` + `debounce(2000)`, `MapCanvasViewModel.kt:1259-1281`), and the stale-speed
  ticker does not run outside follow mode (`:1318-1327`). On a cold start the arming emission is the
  quality change itself — the last event before silence.

The defect contradicts spec `map-modes` — **Browse re-center**: "Off-center viewport at start shows the
button … without any user interaction", and the requirement's "SHALL be re-evaluated on every position
fix and every viewport change" with a damped appear edge. The single resulting fix: complete the dwell
on the clock, not on the next unrelated state emission. The existing `Dispatchers.Default` real-clock
ticker (already used for fix quality, `:1243-1251`) drives it through the injected `timeSource`, so no
wall-clock wait enters a test.

Evidence that HEAD cannot satisfy the scenario:

- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelBrowseReCenterTest.kt:150` —
  `awaitDwellAndRefresh` advances the injected clock **and delivers a second fix**; its 10 call sites,
  including the case named for this symptom (`:200`), only pass because the harness supplies the missing
  emission. The test encodes the defect as expected behavior.
- Repro on device: phone, persisted viewport away from the current position, stationary, no touch →
  no button; `adb logcat -s NaviVeylin` shows one `browse re-center off=…px vis=false` and no `vis=true`.
- Red case to write first (`fix-loop` gate, `AGENTS.md` "Agent iteration loop"): one fix on an
  off-center BROWSE viewport, quality settled, then the dwell elapsed with **no further fix and no
  viewport write** → assert visible. Fails on HEAD, must pass after the fix.

## What Changes

- Complete the BROWSE re-center dwell on time: while an appear dwell is armed and the button is not yet
  visible, the existing real-clock tick emits a re-evaluation once the injected clock has passed
  `RECENTER_DWELL_MS`; the derivation then reveals the button with no further fix, pan or zoom.
- Add a test hook pair mirroring `fixQualityTickMs` / `fixQualityTickCount`
  (`MapCanvasViewModel.kt:440`, `:1140`): an injectable tick period and an observable tick count for the
  dwell path, so the new case is deterministic without a wall-clock wait.
- Keep the arm on a `@Volatile` field so the ticker thread never reads `_uiState` (the
  "fix-quality re-evaluation stays off the main thread" rule, `MapCanvasViewModel.kt:1230-1242`,
  `TODO.md §121`).
- Existing behavior is unchanged: the 24 px appear / 12 px hide hysteresis, the 1 s dwell length, the
  BROWSE-only scope, the anchor-preset exclusion in `recenterInBrowse`, and the
  `gpsFixQuality != NONE` gate all stay as they are.
- Additive, not breaking. No API, persistence, schema or manifest change. Rollback: revert the
  ticker duty and the two hook properties; the pre-change (defective) behavior returns.
- Scope: **phone BROWSE only.** The car variant's re-center is an imperative renderer action
  (`auto/src/main/java/com/naviveylin/auto/RendererGate.kt:364`) and is untouched; FREE_DRIVE and
  NAVIGATION visibility keep the `driveSuspended` rule.
- Out of scope, recorded separately: `setScreenSize` (`MapCanvasViewModel.kt:2397`) changes the canvas
  metrics without a UI-state emission, so a late canvas size alone cannot re-derive the offset.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `map-modes`: the **Browse re-center** requirement's damped appear edge gains the liveness rule — a
  dwell that has elapsed MUST reveal the button without a further position fix, pan or zoom, and the
  re-center derivation MUST be re-evaluated when the dwell expires, not only on a state change. The
  existing scenarios (off-center start, vehicle movement, map movement, rotation, hysteresis, noise
  band, no fix, press behavior, anchor exclusion) keep their meaning and gain a case for the
  no-further-event reveal and for the arm being cleared.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — the dwell arm/reveal path, the
  tick loop, two internal test hooks.
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelBrowseReCenterTest.kt` — the new red case
  plus the boundary, no-tick-when-centered, disarm, mode round-trip and quality-gate cases; the
  harness stops implying that an extra fix is required to reveal the button.
- No production call-site change in `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt`: the
  three overlay gates (`:1826-1837`, `:1970-1981`, `:2159-2168`) and
  `MapCanvasViewModel.shouldShowReCenterButton` (`:4609`) already read the derived flag.
- No `:auto`, no `:core`, no `:osmscout-client-java` or native/JNI change; no new dependency.
- Guidelines affected: `guidelines/UI.md` (phone map controls — the `map-recenter-button` /
  `map-modes` control description), `guidelines/MapRendering.md` (follow mode / GPS section owning the
  offset rule), `guidelines/Build.md` §6 (test constraints: teardown of an endless tick loop, awaiting
  state instead of a deadline) and §2 (the iteration protocol).
- Verification: unit suite (`MapCanvasViewModelBrowseReCenterTest`, `MapCanvasViewModelFixQualityTest`
  for the shared quality gate), then `revert-check` (mutate the tick condition to `false` — the new
  case must go red, restore, forced green), then a phone `device-check` / `pixel-check` run with the
  `RECENTER` diagnostics line as evidence.
