# Proposal — fix-a-canvas-size-change-re-derives-the-browse-offset

## Why

**Root cause.** The BROWSE re-center derivation has one input that is not in the UI state it is triggered
by: `refreshBrowseReCenter` reads the canvas metrics off the ViewModel (`screenWidth`, `screenHeight` —
`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelFollow.kt:578-582`), while its only trigger is
`combine(uiState, recenterDwellTicks)` (`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelFollow.kt:906-909`).
The one writer of those fields, `MapCanvasViewModel.setScreenSize`
(`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelCamera.kt:43-58`), publishes no UI state of its
own: it assigns the two fields, calls `publishResolvedAnchor()` — which emits only when the *anchor
fraction* changes, and the default CENTER anchor resolves to `0.5/0.5` at every canvas size
(`core/src/main/java/com/naviveylin/core/VehicleAnchor.kt:128`, `resolveAnchorAxis` returns the fraction
unchanged when no overlay inset is measured, `:153`) — and pushes the size into the renderer. So a change
that moves only the canvas metrics emits nothing, the derivation does not run, and an offset that was
unmeasurable stays unmeasurable (NaN, `MapCanvasViewModel.kt:641`). The dwell never arms, so the clock path
that completes a pending dwell (`MapCanvasViewModelFollow.kt:825-831`) cannot fire either: nothing but a
later fix, pan or zoom can reveal the button.

The reachable path is the first layout. `initMap` restores the persisted viewport and publishes it while
the canvas is still unmeasured; a fix that is already in state publishes the position. Both emissions
measure the offset as NaN. The `onSizeChanged` that follows
(`app/src/main/java/com/naviveylin/ui/map/MapCanvasMapBand.kt:133-136`) is then the only event, and it
emits nothing — so at a standstill (where the location provider stops reporting) BROWSE keeps the map
off the vehicle's centre with no re-center button, the symptom `fix-browse-recenter-dwell` fixed through
the other route. The item recorded this as out of scope of that change
(`openspec/changes/archive/2026-10-11-fix-browse-recenter-dwell/design.md:39`).

**The red case on the base (`f026bc61`), 2026-10-27T17:16Z** (JUnit XML in `evidence/`):

| case | XML | what it reads |
|---|---|---|
| `MapCanvasViewModelBrowseReCenterTest#aCanvasSizeChangeAloneReDerivesTheOffset` | `tests=24 failures=1` | `a canvas size change alone must re-derive the offset and arm the dwell` — the size change emits nothing, so the dwell is never armed |

surface: 1 case added, 0 rewritten, 2 main files and 1 guideline section realigned — `grep -rl -e
browseReCenterVisible -e browseCenterOffsetPx -e RECENTER_SHOW_OFFSET_PX
app/src/test/java/com/naviveylin/ui/map/*.kt` (6 files carry the rule; only the re-center suite gains a
case, the other five assert behaviour this change does not move).

**Owning requirement.** `map-modes` — "Browse re-center".

**Owning guideline.** `guidelines/UI.md`, the section "Phone map re-center button (per-mode)" (7a).

## What changes

1. **`MapCanvasUiState` carries the canvas metrics.** Two fields, `canvasWidthPx` / `canvasHeightPx`,
   default 0 — the same way the viewport is in the state rather than beside it.
2. **`setScreenSize` publishes them.** The one writer of the metrics writes the state it is an input of;
   no other behaviour of that function changes (the renderer push and the first-render trigger stay).
3. **`refreshBrowseReCenter` reads them from the state it is handed**, so its inputs and its trigger are
   the same snapshot. A canvas-size change is now a state change and re-derives the offset.
4. **The spec delta** adds the sentence and the scenario to `map-modes` — "Browse re-center";
   `guidelines/UI.md`, section 7a, states that the canvas metrics are observed state.

## Impact

- One more emission per real canvas-size change (first layout, rotation, unfold, inset-driven resize) —
  the same order as the viewport writes the rule already reacts to.
- The other readers of `screenWidth`/`screenHeight` (route fit, POI/favourite framing, the follow target)
  keep reading the fields; they are not derivations over the state snapshot and are out of scope here.
- No change to the thresholds, the dwell, the hysteresis, the modes, the car variant or any log line.

## Verification

The rule's suite turned green on the fix (24 cases, no failure — `evidence/gate-recenter-rule.xml`), the five
other suites that name the rule stayed green (`evidence/focused.log`), and the project's gate ran once on the
archived change: every one of its 23 suites passed (`evidence/gate.log`, `evidence/gate-tallies/`).
