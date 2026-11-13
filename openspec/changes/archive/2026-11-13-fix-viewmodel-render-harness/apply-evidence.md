# Apply evidence — fix-viewmodel-render-harness

Machine: this working tree (`main` at `dcc0ef2a`), JDK per `tools/loop/loop.conf`, Robolectric default sandbox.
Every command below was run in this tree; the numbers are the runs' own output.

## 1.1 — the diagnosis, measured before the case asserted a frame

Before the change no ViewModel case had a frame reach `MapCanvasUiState` through the collector. That is a count,
not an impression:

| what a case does at HEAD | who |
|---|---|
| asserts a frame from a `MapRenderer` it holds itself, no ViewModel and no collector | `MapRenderer*Test`, `TileCacheRenderTest`, `RenderModeSwitchTest` |
| calls `initMap`/`setScreenSize` but asserts renderer state or a native side effect, not a published frame | `MapCanvasViewModelDatabaseOpenTest`, `DiagnosticsCoordinatePrivacyTest`, `MapCanvasViewModelCarSessionTest` (which injects a real renderer with `setMapRendererForTest` and reads `renderer.frameFlow.value` directly) |
| needs a landed frame and injects it through a test-only setter | `git grep -c publishRenderedFrameForTest HEAD` → `MapCanvasViewModelRouteFitTest.kt:2`, `MapCanvasViewModelZoomWalkTest.kt:9` |

The walk class called neither `initMap` nor `setScreenSize`; the route-fit class called `setScreenSize` but not
`initMap`. So the renderer, when it existed at all, had no viewport request behind the class's own state.

The harness was built empty first (renderer, size report, scheduler, collector — no assertion beyond "the
renderer exists") and driven twice:

| drive | native renders | frames delivered |
|---|---|---|
| `initMap` + `setScreenSize(120, 200)` | 1 (`renderCount`) | 1, 144×240 bitmap (= `DEFAULT_CANVAS_OVERRUN` 1.2 × the surface) |
| `initMap` only, size never reported | 0 | 0 |

So the empty harness is not a missing stub and not a JNI refusal: the renderer exists and its queue runs, and
the frame is stopped by the size gate — `MapRenderer.enqueueRenderJob` returns while `screenWidth`/`screenHeight`
are zero (`enqueueRenderJob skipped: screen 0x0`, `MapRenderer.kt:686-688`), which is exactly why
`renderMap` also returns early on a zero-sized surface (`MapCanvasViewModelCamera.kt:296`, `:315`). The second
row is the finding's own condition and it is now a case, `a render without a reported size is skipped`.

## 2.1 — the case, and its red proof

`MapCanvasViewModelFrameHarnessTest#aFrameLandsThroughTheFrameCollector` (green). Mutating the drive to omit the
size report — the only edit, the case body otherwise unchanged — reddens it:

```
java.lang.AssertionError: exactly one frame for one size-triggered render expected:<1> but was:<0>
```

Restored: green.

## 3.1 / 3.2 / 3.3 — the moved cases

| class | cases | result |
|---|---|---|
| `MapCanvasViewModelFrameHarnessTest` | 3 | green (0.914 s) |
| `MapCanvasViewModelZoomWalkTest` | 7 | green (0.325 s) |
| `MapCanvasViewModelRouteFitTest` | 21 | green (2.532 s) |

`bash tools/declared-cases.sh fix-viewmodel-render-harness` selects those three (484 test classes in the module;
selected 3, measured class time 3.8 s). Every existing assertion of both moved classes is kept — only the frame
source moved, from the injected state to the collector that consumes `MapRenderer.frameFlow`.

One scheduling fact the move measured and the guideline now carries: `advanceUntilIdle()` alone leaves a
debounced render unsubmitted. With the state already at the requested magnification the first attempt reported
`renders=0` and the frame still at the previous 5.0; `scheduler.advanceTimeBy(1_000)` before the idle submits it
(`zoomDebounceMs` 200 ms). The walked steps themselves take the immediate path and need no window.

## 4.1 — the hook is gone

`grep -rn publishRenderedFrameForTest app/` → no match. Both case classes green, before and after.

## 5.1 — the recipe

`guidelines/BuildTests.md` §6i "A frame from the render pipeline" (2 458 bytes), listed in §6's index and in
`AGENTS.md`'s two §6 routing rows. `bash tools/check-doc-routes.sh` → all 340 section references resolve, all 7
skill references resolve.

## 6.1 — the suite

`./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.*"` → 908 cases, no failure.

The change's own gate, `bash tools/loop/gate.sh --base HEAD` (both `:app` flavors, forced, no build cache, plus
every shell check the touched paths select):

```
LOOP-GATE: tests=242 failures=0 errors=0 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 486s, …)
```