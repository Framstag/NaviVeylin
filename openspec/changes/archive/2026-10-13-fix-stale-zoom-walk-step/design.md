# Design — fix-stale-zoom-walk-step

## Context

Facts the decision depends on. `proposal.md` — Why carries the measured evidence.

- `core/src/main/java/com/naviveylin/core/ZoomWalk.kt` is a plain JVM object with two fields,
  `target` (the magnification the walk heads for, NaN when none is pending) and `step` (the
  magnification the render pipeline and the display are on). It is instantiated once, by the phone map
  (`MapCanvasViewModel.zoomWalk`); `AutoMapRenderer` carries its own equivalent fields
  (`zoomWalkTarget`, `advanceZoomWalk`) and does not use this class.
- The render magnification is read from `renderMag` at four sites in `MapCanvasViewModel`: `:1648`
  and `:1652` (follow render target and `prepareViewport`), `:1756` (auto-zoom commit), and `:4677`
  (`renderMap`), the single funnel every render request goes through.
- The display-side mirror already falls back: `advanceZoomWalk` publishes `zoomWalkStepMag = NaN` as
  soon as the walk is inactive (`MapCanvasViewModel.kt:4539-4541`), and `zoomAnimationTargetMag`
  derives the eased scale from it. Only the render path keeps using the walk's step.
- Writers of `viewport.magnification`: `commitMagnification(target, walk)` (`:4498`) is called from
  the auto-zoom commit (`:1728`) and from the zoom input path (`:4462`). The route overview fit
  (`:4338`), the navigation preset on follow engage (`:3546`) and the mode presets write the
  viewport's magnification directly.
- `ZoomWalk` has three terminal paths that leave `step` set: `request` landing directly because no
  frame is on screen (`:100-104`), `request` landing inside the window (`:105-108`), and
  `onFrameLanded` landing on the target (`:126-135`). Only `cancel()` clears `step`.

## Goals / Non-Goals

**Goals**

- The walk's pin on the render magnification lives exactly as long as the walk does.
- The fix has one home. The walk's own behaviour — window-sized steps, retracking, landing exactly on
  the target, the persisted target, the coverage guarantee — is unchanged.
- The invariant is host-testable, and the user-visible symptom has a device measurement that settles
  it.

**Non-Goals**

- Routing the fit and the presets through `commitMagnification` so their commits are *walked* rather
  than landing in one frame. That is a second finding (the transition, not the pin).
- Changing `AutoMapRenderer`, the animation durations, the overrun factor, the log lines, or any
  persisted value.

## Decisions

### D1 — The reader falls back: `renderMag` honours its step only while the walk is pending

| Alternative | Why it is not the fix |
|---|---|
| **A (chosen)** `ZoomWalk.renderMag` returns the fallback whenever no walk is pending | The reader is the one place that answers "what magnification should this render use?". The display path already answers the same question that way (`zoomWalkStepMag` → NaN), so this restores one answer instead of two |
| B cancel the walk at the fit and at the preset paths | The measurement eliminates it as *the* fix: the pinned frames (`18:24:11.568`, `:12.069`) were rendered while no walk was pending — 15 s of `renderMap` calls with no `zoomWalk step=` advance — and the pinned value survived a later commit. Cancelling at one writer moves the hole to the next writer; the set of writers is open (the fit, the follow engage, the presets, the persisted-viewport restore today) |
| C clear `step` at each terminal path of the walk (landing inside the window, landing on the target, request with no frame on screen) | Same invariant, three edit sites that must keep agreeing, and a fourth path added later would reopen the defect — the shape this project's `Design.md` §12 warns about. Rejected on edit-site count and on the risk of a future terminal path, not on behaviour: its *effect* is the same |

The predicate: the walk pins while `active` (`target` not NaN) and its step is known; otherwise the
caller's fallback — the magnification the viewport records — is used. A walk that is still pending
keeps pinning its step, which is what the coverage guarantee of `smooth-zoom` ("Zoom animation never
exposes uncovered map area") needs.

Rationale for the chosen shape: it is one expression in the file that owns the pin, it needs no
enumeration of the callers, and it makes the render path agree with the display path that already
behaves this way. It also keeps `step`'s meaning intact for the reader that still needs it
(`onFrameLanded`'s "has the previous step landed?" check).

### D2 — The rule is stated where the walk is described, not only in the spec

`guidelines/MapRendering.md` §11 (the walk bullet, lines 476-491) says the committed magnification
advances one step per landed render and that the last step lands exactly on the recorded target; it
never says when the pin ends. The delta adds that sentence: the pin lives exactly as long as the walk,
so a camera fit, a preset or any other later commit is rendered at the committed value. The spec delta
in this change (`smooth-zoom` — *The render pin lives only as long as the walk*) is the contract; the
guideline is where an implementer reads it next to the pipeline it constrains.

**No `Logging.md` change:** the existing `MapCanvasVM: renderMap mag=… (viewport …)` line already
exposes the disagreement between the rendered and the recorded magnification, which is how the defect
was measured; a second line would duplicate it.

### D3 — Two test cases, at the two levels the defect spans

- `core/src/test/java/com/naviveylin/core/ZoomWalkTest.kt` — the invariant, pure JVM: a walk that has
  landed at magnification A, then `renderMag(B)` must return B; and the guard that a *pending* walk
  still pins its step. These are the cases the two new spec scenarios name.
- `app/src/test/java/com/naviveylin/ui/map/` — the pipeline: a completed walk followed by a route-fit
  commit must render at the fit's magnification. The neighbours are `MapRendererBlitTest` and
  `MapCanvasViewModelFollowModeTest`; the renderer is injectable (`setMapRendererForTest`) and the
  fake client records the render calls, so the assertion reads the magnification the renderer was
  asked for rather than pixels.

### D4 — Verification: host cases now, the same device flow as the evidence afterwards

**Host (no device):** the two `:core` cases and the one `:app` case above, run through the module
filters (`--tests com.naviveylin.core.ZoomWalkTest`, `--tests com.naviveylin.ui.map.*`).

**Device (the measurement that settles the symptom):** repeat the captured flow on the phone —
plan a route, note the overview, press Start, stay parked — and read two numbers, both already
instrumented:

1. the log's pair `fitViewportToRoute mag=X` / `executeRender start mag=Y`: on HEAD `X = 10.0`,
   `Y = 17.756`; after the fix `Y` is the committed magnification;
2. route-paint pixels in the frame the phone displays, counted off the screenshot with the script in
   `.pi/logs/route-nav-start/count.py` (the detector is calibrated against a frame known to carry the
   route). On HEAD the parked post-Start frame counted **0**, one re-center render counted **47 854**.

`pixel-check`'s script is not applicable: it finds the analysed-segment highlight by its casing colour,
not the route. The device run must be made with the vehicle on the route (the same session reported
`Abseits der Straße`, i.e. an off-road fix), otherwise a clean frame is not attributable to the fix.

**Revert-check:** the new invariant ("a landed walk does not pin") is mutated once — restore
`if (step.isNaN()) fallback else step` — and the named `:core` case must fail; restore and re-run
green.

## Risks / Trade-offs

- **A commit made while a walk is pending still renders at the walk's step.** That is required by the
  coverage guarantee; the committing path is expected to re-request through the walk. Unchanged by
  this fix and covered by the pending-walk scenario.
- **The `:app` case may not be expressible with today's test hooks** (the fit is triggered by a route
  result and a canvas size). Mitigation: drive it through `setRoutePanelViewModel` with a fake result
  and the injected renderer, as `MapCanvasViewModelRouteVisibilityTest` already does; if the frame
  sequence cannot be produced deterministically, the case asserts the magnification the renderer was
  handed instead of a rendered bitmap, and the device run carries the end-to-end claim.
- **The fix hides nothing else:** a magnification that never becomes the viewport's value still renders
  at the walk's step while pending, and a walk that is cancelled still falls back — the cancelled-walk
  case (`ZoomWalkTest:152`) must stay green.
- **Follow-up left open:** a fit's commit still lands in one frame rather than being walked (unchanged
  today). Recorded in `proposal.md` — Not in scope; no regression from this change.

## Migration Plan

One commit: the `:core` predicate and its KDoc, the two `:core` cases and the `:app` case, the
`MapRendering.md` §11 sentence, the `smooth-zoom` delta. No persisted state, no schema, no JNI and no
build input is involved, so the rollback is a revert of that commit; the walk has no on-disk
representation and `persistMag` is untouched, so a reverted build reads today's stored viewports
unchanged.

## Open Questions

None that would change the specs, the approach or the tasks. The deferred question — whether the fit
and the preset commits should be walked like a zoom commit — is a transition question with its own
measurement (the eased duration for a fit is not obviously the 200-300 ms of discrete zoom input), and
it is recorded as a follow-up rather than guessed at here.
