# Design

## Context

See `proposal.md` — Why for the defect and its evidence, `specs/map-modes/spec.md` for the behaviour
contract.

State that shapes the approach:

- `refreshBrowseReCenter(state)` (`MapCanvasViewModelFollow.kt:572-616`) is a derivation over one UI-state
  snapshot plus the private arm field `browseReCenterBeyondSinceMs` (`MapCanvasViewModel.kt:490`). Its only
  caller is `combine(uiState, recenterDwellTicks)` (`MapCanvasViewModelFollow.kt:906-909`) — the design of
  `fix-browse-recenter-dwell` D1: "one collector re-derives it …, so every pan, zoom and position update is
  covered without hooking each mutation site".
- The same function reads `screenWidth`, `screenHeight` and `projectionDpi` off the ViewModel
  (`:578-582`). `projectionDpi` is a per-device constant (`MapCanvasViewModel.kt:342`); the two metrics are
  not.
- `setScreenSize` (`MapCanvasViewModelCamera.kt:43-58`) is the only writer of those metrics. It already
  publishes state *indirectly* through `publishResolvedAnchor()`, but that copy happens only when the
  resolved anchor fraction moves — for the default CENTER anchor and no measured overlay inset the
  fraction is `0.5/0.5` at every size (`VehicleAnchor.kt:128,153`), so a size change alone publishes
  nothing.
- `browseCenterOffsetPx` returns `NaN` while either metric is unusable (`MapCanvasViewModel.kt:641`), and
  `refreshBrowseReCenter` discards the arm on NaN (`MapCanvasViewModelFollow.kt:584-588`): a NaN
  measurement leaves no trace, so nothing can complete later.
- Test constraints: the rule's suite (`MapCanvasViewModelBrowseReCenterTest`) drives the injected
  `timeSource` and pumps the scheduler from a real dispatcher, never a wall clock
  (`guidelines/Build.md`, section 6e, spec `unit-test-suite-runtime`).

## Goals / Non-Goals

**Goals:**

- A change that moves only the canvas metrics re-derives the offset: the derivation's inputs and its
  trigger are the same snapshot.
- No new collector, thread, loop or log line; no behaviour change outside BROWSE with a usable fix.

**Non-Goals:**

- Not moving the other `screenWidth`/`screenHeight` readers (route fit, POI/favourite framing, the follow
  target) into the state — they are command-style helpers, not derivations over a state snapshot.
- Not changing the thresholds, the dwell, the hysteresis, the mode model or the car variant.
- Not touching the renderer's own size fields.

## Decisions

### D1 — Publish the canvas metrics into `MapCanvasUiState`, and read them from there

`MapCanvasUiState` gains `canvasWidthPx` / `canvasHeightPx`; `setScreenSize` copies them into the state
when they change; `refreshBrowseReCenter` reads `state.canvasWidthPx` / `state.canvasHeightPx`. The
derivation now runs off the same snapshot it is triggered by, which is the rule every other input of it
already follows (the viewport, the position, the fix tier).

Alternatives rejected:

- **Call `refreshBrowseReCenter(_uiState.value)` from `setScreenSize`.** This is a second trigger beside
  the collector — the per-mutation-site hook `fix-browse-recenter-dwell` D1 removed, re-introduced for one
  site. It leaves the two halves (what the rule reads, what triggers it) disagreeing for the next input
  someone adds, which is the defect being fixed.
- **Have `setScreenSize` bump an unrelated state field (a counter) to force a re-derivation.** It emits
  the right event by accident: the snapshot would still not carry the metrics, and the next reader of the
  derivation cannot tell what it actually depends on.
- **Read `screenWidth`/`screenHeight` through a state-backed accessor.** Same emission problem — the
  accessor changes nothing about *when* the derivation runs.

### D2 — The emission is the existing `_uiState` copy, and only on a real change

`setScreenSize` is called from `Modifier.onSizeChanged`, i.e. only on a real size change, and the copy is
guarded by an equality check so a repeated report of the same size writes nothing. No debounce is added:
the value is what the rule reads, so it cannot flicker.

### D3 — One case, added to the rule's suite

`MapCanvasViewModelBrowseReCenterTest#aCanvasSizeChangeAloneReDerivesTheOffset` reproduces the first
layout from the unmeasured side: canvas 0×0, an off-centre viewport and a fix already in state (the offset
is NaN, no arm), then only `setScreenSize(SCREEN_W, SCREEN_H)`. It asserts the arm starts and, past the
dwell, that the button appears. On the base the arm assertion fails within its bounded pump — the size
change emits nothing — so the case is red for the mechanism and not for the dwell, which its sibling
cases already cover.

## Blast radius

- `MapCanvasUiState` — two fields with defaults; every existing `copy()` keeps 0 until the screen reports a
  size, so the state a test constructs by hand is unchanged in meaning.
- `setScreenSize` — one guarded copy added; the renderer push and the first-render trigger are untouched.
- `refreshBrowseReCenter` — two reads change source; its decision table is unchanged.
- `guidelines/UI.md`, section 7a, and the `map-modes` delta.

## Rollback

Revert the four edits: the derivation reads the fields again (the behaviour of the base), the two state
fields and their publication go away, and the added case is dropped with the delta. Nothing persisted,
migrated or versioned depends on them.

## Verification

`./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.MapCanvasViewModelBrowseReCenterTest"`
for the rule, the same task for the five other suites that name the rule, then the project's gate once.
