# Design

## Context

The render pipeline's two halves are already separable, and the seam a test needs is in place; nothing here
invents an abstraction.

| what a harness needs | where it exists today |
|---|---|
| a renderer bound to a scope a test controls | `initMap` builds `MapRenderer` on `CoroutineScope(SupervisorJob() + rendererDispatcher)` (`MapCanvasViewModelMapInit.kt:239-241`), and `rendererDispatcher` is an `internal var` a test assigns (`MapCanvasViewModel.kt:175`) |
| a surface size that lets a job through | `setScreenSize(width, height)` (`MapCanvasViewModelCamera.kt:43`) sets the renderer's size and triggers the first render when the size was zero (`:57-65`) |
| pixels without JNI | `FakeOSMScoutClient.render` returns `createTestPixels(width, height)` (`FakeOSMScoutClient.kt:258-275`) |
| the production consumer | `renderer.frameFlow.collect { applyRenderedFrame(it) }` (`MapCanvasViewModelMapInit.kt:271`) |

What is missing is only the drive: the tests construct the ViewModel, never call `initMap`, and inject a
magnification through `publishRenderedFrameForTest` (`MapCanvasViewModelCamera.kt:392`). The gate that stops a
job without that drive is the zero-size check — `renderMap` returns early while `screenWidth <= 0`
(`MapCanvasViewModelCamera.kt:296`, `:315`) and `enqueueRenderJob` logs `enqueueRenderJob skipped: screen 0x0`
(`MapRenderer.kt:686-688`).

## Decisions

### D1 — The harness drives `initMap`, not a second entry point (chosen)

The harness calls the same three production functions the screen calls, in the same order: `initMap`,
`setScreenSize`, then the zoom/route/follow entry under test, advancing `rendererDispatcher` between steps.
The frame arrives through `applyRenderedFrame`, which is the whole point of the requirement: a case that
asserts on `uiState.renderViewport` then reads what the pipeline published, bitmap and viewport together
(`MapCanvasViewModelCamera.kt:233-245`).

- **Alt A: keep `publishRenderedFrameForTest` and add a second harness hook beside it.** Rejected: the
  shortcut is the defect — a hook that publishes the viewport without the frame is exactly the wiring the
  cases cannot see.
- **Alt B: an instrumented `androidTest` harness on a device.** Rejected for the walk: the AVD costs a
  round-trip per case and the pipeline's own arithmetic is device-independent; the device keeps its role for
  the arrival and the visual claims (`MapFollow.md` §165-189).

### D2 — The walk cases keep their assertions; only the frame source moves (chosen)

`MapCanvasViewModelZoomWalkTest.walkToCompletion` (`:98-122`) already asserts the window bound and the exact
landing on every step. Replacing `publishRenderedFrameForTest(step)` with "advance the dispatcher and read
the landed frame" preserves every assertion and adds the ones the shortcut could not make: the bitmap is the
one the request produced, the viewport rides with it, and the walk's step equals what the renderer actually
rendered.

### D3 — `publishRenderedFrameForTest` is removed, not deprecated (chosen)

A deprecated hook is the one a new case reaches for when the harness is inconvenient. The two call sites
(`MapCanvasViewModelZoomWalkTest`, `MapCanvasViewModelRouteFitTest:232`, `:236`) move; the function goes.

### D4 — The recipe is recorded beside the JNI-stub rules (chosen)

`guidelines/BuildTests.md` §6 already owns what a ViewModel test may and may not do (the stub, the sandbox,
the Compose constraints). One rule states the harness path — `initMap` → `setScreenSize` → dispatcher →
collector — so the next render test reads it there instead of writing a fourth private script.

### D5 — The diagnosis is a task, not an assumption (chosen)

`initMap` opening a map database under Robolectric has not been exercised by any case in the tree. Task 1
runs the harness empty — `initMap` and `setScreenSize` only — and records what the log says (`initMap:
viewport …`, `MapRenderer created`, `render complete`, or the failure line). If `initMap` refuses before the
renderer exists, the harness states the refusal in the case and the requirement's second scenario names the
cause; the change is not worked around with a synthetic renderer.

## Open decisions

none