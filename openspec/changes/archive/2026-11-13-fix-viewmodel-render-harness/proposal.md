# Proposal

## Why

No unit test of `MapCanvasViewModel` had ever obtained a frame from the render pipeline. Every case that
needed a landed frame injected one through the `@VisibleForTesting` hook
`MapCanvasViewModel.publishRenderedFrameForTest` (`MapCanvasViewModelCamera.kt:392`), which sets
`renderViewport` and calls `advanceZoomWalk` directly — it never enters `applyRenderedFrame`, never sees a
`MapRenderer.FrameState`, and never exercises the bitmap or the frame's own viewport.

The reason is structural: the renderer exists only inside `initMap` (`MapCanvasViewModelMapInit.kt:241`) and
`renderMap` refuses to queue while the surface size is zero (`MapCanvasViewModelCamera.kt:296`; the renderer
logs `enqueueRenderJob skipped: screen 0x0`). The walk and the route-fit classes call neither `initMap` nor
`setScreenSize` (`:43`), so their ViewModel has no renderer and no size: nothing can land.

A defect in the wiring between the renderer and the UI state is therefore invisible to the suite.

## What Changes

- Add a test-side harness that drives the **real** path: `initMap` → `setScreenSize` → the injected
  `rendererDispatcher` runs the queued job → `MapRenderer.frameFlow` → `applyRenderedFrame`. The fake client
  already returns pixels (`FakeOSMScoutClient.render`, `:258`; `createTestPixels`), so no native render is
  involved.
- State the requirement in `map-render`: a ViewModel frame reaches the UI state through the production
  collector, and a render test that needs a landed frame uses that path rather than a test-only setter.
- Retire `publishRenderedFrameForTest` once the cases that call it (the walk cases and the route fit case)
  drive the harness instead, so the shortcut cannot be reintroduced.
- Record the harness recipe in `guidelines/BuildTests.md` beside the JNI-stub rules, so the next render test
  finds it instead of inventing a fourth ad-hoc script.

Not BREAKING: production code gains no behaviour change. `setScreenSize` and the frame collector already
exist and are untouched; the only production edit is the removal of a `@VisibleForTesting` function, whose
callers are all in `app/src/test`. Rollback: restore the function and revert the cases.

**Scope: `:app` unit tests only.** No Android Auto surface, no JNI, no CMake, no resources.

## Capabilities

### New Capabilities

None. What is missing is a requirement on the pipeline that already exists, not a new pipeline.

### Modified Capabilities

- `map-render`: adds the requirement that a frame reaches `MapCanvasUiState` through the production
  collector, and that a test needing a landed frame drives that path. The rendering behaviour itself is
  unchanged — the requirement is about the seam the pipeline is observed through.

## Impact

Code:

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModelCamera.kt` — remove
  `publishRenderedFrameForTest` (`:392-403`).
- `app/src/test/java/com/naviveylin/ui/map/` — a new case class (`MapCanvasViewModelFrameHarnessTest`)
  driving the real path, and the existing `MapCanvasViewModelZoomWalkTest` and
  `MapCanvasViewModelRouteFitTest` cases moved onto it.
- No production component, DI binding, manifest entry, resource or Gradle change.

Guidelines:

- `guidelines/BuildTests.md` §6 — one rule stating the harness path (read with §6a for the Robolectric
  sandbox and §6h for the JNI stub).

surface: 1 requirement added with 2 scenarios, 2 Kotlin test classes touched, 1 guideline rule added —
count re-taken with `bash tools/declared-cases.sh fix-viewmodel-render-harness`.

Reference: the walk cases already assert the window and the landing exactly; this change moves them onto the
collector so those assertions read a frame the pipeline produced.