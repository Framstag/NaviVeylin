# Tasks

## 1. The red case (spec: `smooth-follow` — The follow blit offset is computed by one seam that owns the anchor; issue #73)

- [x] 1.1 Add `core/src/test/java/com/naviveylin/core/OverrunWindowHomeTest.kt`: the prediction class declares
  no shift helper (`shiftPx`), no old helper name and no `DisplayOffset`, and
  `core/src/main/java/com/naviveylin/core/OverrunWindow.kt` declares both — one case per half, each with a
  positive control that the scan read the file it claims to read. Verify: on the base tree the class reports
  `tests="2" failures="2"` — `evidence/red-on-head.xml`, run 2026-10-29T16:53:50Z with only this file added and
  the two production sources at `87cc95de`.

## 2. The neutral home (spec: `canvas-overrun` — Overrun window shift for pan)

- [x] 2.1 Add `core/src/main/java/com/naviveylin/core/OverrunWindow.kt`: `object OverrunWindow` carrying the
  moved `data class DisplayOffset` and `fun shiftPx(...)`, body verbatim from
  `FollowPrediction.displayOffsetPx` (rotated geo delta, anchor offset at `(fx − 0.5)·canvas`, clamp into
  `((bitmap − canvas)/2).coerceAtLeast(0.0)`), KDoc naming the three callers and the two specs. Verify:
  task 2.3's cases pass and task 1.1's second case turns green.
- [x] 2.2 Delete `DisplayOffset` and `displayOffsetPx` from
  `core/src/main/java/com/naviveylin/core/FollowPrediction.kt`; the class keeps the prediction only. Verify:
  the module compiles with no unused-import warning (`cos`/`sin` remain used by the speed model).
- [x] 2.3 Move the ten geometry cases out of `core/src/test/java/com/naviveylin/core/FollowPredictionTest.kt`
  into `core/src/test/java/com/naviveylin/core/OverrunWindowTest.kt`, assertions unchanged, calling
  `OverrunWindow.shiftPx`; `FollowPredictionTest` keeps the prediction cases, including the two the shipped
  spec names (`staleBaseDoesNotDeclareStop`, `holds position when the fix has not moved`). Verify:
  `./gradlew :core:testDebugUnitTest --tests "com.naviveylin.core.OverrunWindowTest" --tests
  "com.naviveylin.core.FollowPredictionTest"` green.

## 3. The phone call sites (spec: `smooth-follow` — The follow blit offset is computed by one seam that owns the anchor; spec: `canvas-overrun` — Overrun window shift for pan)

- [x] 3.1 `app/src/main/java/com/naviveylin/ui/map/FollowBlitRules.kt` and
  `app/src/main/java/com/naviveylin/ui/map/PanWindowRules.kt`: return type `OverrunWindow.DisplayOffset` and
  `OverrunWindow.shiftPx(...)`; the seam still reads `MapCanvasUiState.resolvedAnchor` itself and the pan rule
  still passes the default center anchor. Verify: tasks 3.4 and 5.1.
- [x] 3.2 `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` (`panWindowOffset`'s return type) and
  `app/src/main/java/com/naviveylin/ui/map/MapCanvasMapBand.kt` (the parameter type the band takes).
- [x] 3.3 `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt`: `overrunWindowCovers` calls
  `OverrunWindow.shiftPx` — same inflated canvas (`+2·BLIT_COVER_SLACK_PX`), same default anchor, so the
  coverage decision and the applied clamp still share one computation. Verify: the `canvas-overrun` pan
  cases in task 3.4.
- [x] 3.4 Update the four phone test classes that drive the helper: `FollowAnchorFramingTest.kt` (the
  framing contract and the previous-orientation clamped case), `MapPanDisplayWindowTest.kt` (the pan window
  contract: finger travel, rotated projection, saturation at the margin),
  `MapCanvasViewModelVehicleAnchorTest.kt` (the resolved-vs-raw anchor geometry),
  `FollowBlitCallSiteTest.kt` (its inline-call needle becomes `OverrunWindow.shiftPx(`, so the screen that
  computes the offset itself is still refused). Verify:
  `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.FollowAnchorFramingTest" --tests
  "com.naviveylin.ui.map.MapPanDisplayWindowTest" --tests "com.naviveylin.ui.map.FollowBlitCallSiteTest"
  --tests "com.naviveylin.ui.map.MapCanvasViewModelVehicleAnchorTest"` green.

## 4. The car call sites (spec: `auto-smooth-follow` — Single resolved anchor in the AA follow blit)

- [x] 4.1 `auto/src/main/java/com/naviveylin/auto/AutoMapRendererMotion.kt` (the extrapolation-loop blit) and
  `AutoMapRendererRender.kt` (the `renderFrame` blit and the marker projection) call
  `OverrunWindow.shiftPx`; both keep passing the resolved fraction on the follow path and `0.5/0.5` on the
  browse path. `AutoMapRendererFollow.kt` takes the result type in `followDiagnosticLine`'s signature.
- [x] 4.2 Update `AutoMapRendererBlitTest.kt` (resolved-vs-raw anchor, browse center contract) and
  `AutoMapRendererFollowAnchorTest.kt` (the locale-independent diagnostic line, which constructs a
  `DisplayOffset`). Verify: `./gradlew :auto:testDebugUnitTest --tests
  "com.naviveylin.auto.AutoMapRendererBlitTest" --tests
  "com.naviveylin.auto.AutoMapRendererFollowAnchorTest"` green.

## 5. Docs, spec deltas and the whole-suite gate

- [x] 5.1 Repoint the nine `FollowPrediction.displayOffsetPx` mentions in `guidelines/MapRendering.md` (§3
  follow mode, §5 overrun window, §14 Android Auto renderer) to `OverrunWindow.shiftPx`; the two spec deltas
  (`smooth-follow`, `auto-smooth-follow`) name the new home in the one scenario/bullet that named the helper.
  Verify: `openspec validate fix-overrun-window-shift-home --strict` and
  `tools/check-doc-routes.sh` pass (`evidence/checks.txt`, `evidence/shell-gate.log`);
  `git grep -n "displayOffsetPx" -- '*.kt'` finds no live mention, and the two `*.md` mentions that remain are
  the shipped requirement texts this change's deltas replace at archive (archived changes and their evidence
  files keep their historical text).
- [x] 5.2 The whole-suite gate: `./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache` (both `:app`
  flavors, `:auto`, `:core`, `:osmscout-client-java`), plus the shell checks `tools/loop/gate.sh --base
  87cc95de` selects for the touched paths. Verify: BUILD SUCCESSFUL, `FAILED` count 0, and the executed-task
  list read rather than assumed (`evidence/gate.log`).
- [x] 5.3 Revert-check for the one invariant this change introduces beyond the move (the guard): mutate
  `FollowPrediction` by re-adding a `fun shiftPx(` declaration and confirm
  `OverrunWindowHomeTest.the prediction class carries no overrun window shift` fails; restore and re-run the
  case forced. Verify: `evidence/revert-check-mutated.xml` and `evidence/revert-check-restored.xml`.
