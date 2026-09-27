# Design: Bounded, anchored zoom animation on the phone

## Context

See `proposal.md` - Why for the reported defect, the code evidence and the three
differences. This document decides *how* the phone side changes.

Current state that shapes the approach:

```
DISPLAY (Compose, MapCanvasScreen.kt)
  drawFrontFrame: scale(zoomScale, zoomScale, pivot = zoomAnchor)        (:2109)
  target scale = 2^(viewport.magnification - renderViewport.mag)         (:315)
  auto-zoom commits: pivot = canvas center, AUTO_ZOOM_ANIMATION_MS = 650 (:326-340, :2067)
  render-land handoff + CROSSFADE_MS = 150                               (:409-434, :2050)
  frontMag = uiState.renderViewport.mag, published by the ViewModel's
  frameFlow collector                                                   (MapCanvasViewModel.kt:1884-1907)

RENDER REQUEST (MapRenderer.kt)
  renderMap() -> requestRender() -> submitDebounced()                    (MapCanvasViewModel.kt:3540)
  debounce loop: pan/rotate/zoom timeouts, zoomDebounceMs = 200          (:47, :489-519)
  a zoom change is never blit-served (isZoom -> full render)             (:450, :467)
  overrun canvas factor DEFAULT_CANVAS_OVERRUN = 1.2                        (:953)
  -> the frame in hand can serve log2(1.2) = 0.263 levels of scale

AUTO-ZOOM COMMIT (MapCanvasViewModel.kt)
  first commit jumps to the target, later ones converge <= 0.5/update    (:1258-1287, :1274)
  committed mag is the PERSISTED viewport (spec smooth-zoom -
  Viewport records final magnification)

CAR REFERENCE (auto/AutoMapRenderer.kt)
  advanceZoomWalk: one step per LANDED render, <= ZOOM_BLIT_LIMIT = 0.25 (:869-897, :1991)
  displayedScale() clamps to the window, snaps outside it                (:1081-1086)
  pivot = resolvedFollowAnchor()                                         (:1367-1377)
  walk loop: ZOOM_WALK_FRAME_MS = 20                                     (:2003)

ANCHOR (core/VehicleAnchor.kt)
  VehicleAnchorPosition grid 0.1..0.9, DEFAULT = CENTER
  uiState.resolvedAnchor (fx, fy) resolved against the phone's overlay insets
```

## Goals / Non-Goals

**Goals**

- A displayed phone frame never shows a magnification farther than the frame in hand
  can serve, including the auto-zoom entry - the same bound the car holds.
- The vehicle marker keeps the same screen pixel for every frame of a zoom animation
  whenever a follow mode is active, on every anchor preset.
- The displayed animation never exposes uncovered surface area.
- Keep the Compose display-layer scale as the sub-step ease (it is cheap and already
  verified); do not move the phone onto the car's renderer structure.

**Non-Goals**

- No Android Auto behavior change; the car stays the reference.
- No change to the speed-to-magnification table, the 0.5/update convergence, the
  epsilon deadband or the manual-zoom suspension rules.
- No new settings, no zoom-control redesign, no persistence format change.
- Not replacing the phone's Compose display scale with car-style render-only stepping:
  the phone's sub-level ease is what makes small commits read as continuous.

## Decisions

### D1 - Walk the magnification in bounded steps, keep the Compose ease per step (chosen)

The committed magnification advances one step per **landed** render, each step within the
window the frame in hand can serve, and the Compose display scale eases toward the step
that is pending. The last step lands exactly on the requested magnification.

- **Alt A (chosen): walked steps + per-step Compose ease.** Every rendered frame is at a
  magnification within the window of what is displayed, so no frame ever shows
  wrong-magnification geometry; the ease between steps keeps motion continuous. Mirrors the
  car's `advanceZoomWalk` semantics while keeping the phone's cheap display-side smoothness.
- **Alt B: clamp the display scale only (keep one render).** One render, coverage safe,
  resampling bounded to ~1.19x - but a large change stays "short capped ramp, then a jump
  to the correct geometry", i.e. the reported symptom is reduced, not removed. Kept as the
  documented fallback if the walked render cost proves unacceptable on device.
- **Alt C: fix the pivot only.** Removes the vehicle-slides-off-its-slot defect on
  non-center presets but leaves the content defect untouched; does not satisfy the
  bounded-frame requirement. Rejected as the change's approach, adopted as a required part
  (D6).
- **Alt D: move the walk into `MapRenderer` (car structure).** Rejected: the phone's
  display state lives in Compose, so the renderer would have to publish a *display* target
  it does not own, and its debounce/coalescing pipeline would have to grow a second
  pacing path. The car needed the renderer because that renderer owns both the display and
  the loop; the phone does not.

### D2 - The walk arithmetic is a pure `:core` class; the pacing lives in `MapCanvasViewModel`

`core/src/main/java/com/naviveylin/core/ZoomWalk.kt` (new) holds the step arithmetic as a
pure, host-JVM-testable class next to `ZoomAnimation.kt`:

```
request(target, frontMag)      // no frame yet (frontMag <= 0) -> lands directly (D10)
                               // else: step = clamp(target, frontMag +/- WINDOW)
onFrameLanded(frameMag)        // returns the next step mag, or null when nothing to do
  if pendingTarget is NaN              -> null
  if |frameMag - step| > SETTLE        -> null      (previous step has not landed)
  if |pendingTarget - frameMag| <= WINDOW -> pendingTarget   (landing step)
  else step = frameMag + sign * WINDOW
  pendingTarget = NaN when step == pendingTarget    (walk finished)
cancel()                       // manual zoom / new non-walk commit (D8)
```

The pacing is wired in `MapCanvasViewModel`: the frame collector that already publishes
`renderViewport` (`:1884-1907`) advances the walk on each landed frame and publishes the
current step as the display target (D5).

- **Alt A (chosen): arithmetic in `:core`, pacing in the ViewModel.** The step rules are
  testable on the JVM in milliseconds, and the ViewModel already owns the committed
  magnification, the persisted viewport and the frame collector. Smallest new surface.
- **Alt B: everything in the ViewModel.** Fewer files, but the step rules then only exist
  behind a Robolectric + fake-client harness, which is exactly the layer where the
  bounded-frame rule is hardest to observe.
- **Alt C: everything in the Compose screen.** Rejected: render pacing and persistence
  decisions would move into the UI layer, and the walk could not be unit-tested without a
  composition.

### D3 - Walk steps request an immediate render, bypassing the pan/zoom debounce

A walk step's render is already paced by *landing*, so the 200 ms zoom debounce
(`MapRenderer.kt:47`) adds pure latency: a 4-level entry is ~16 steps, i.e. ~3.2 s of
debounce alone. `MapRenderer` gains an explicit entry point for a walk step that enqueues
the render immediately (the existing debounce path and its coalescing stay untouched for
pan, rotate, gesture and follow-commit renders).

- **Alt A (chosen): immediate enqueue for walk steps.** Mirrors the car, where
  `commitZoom` calls `requestRender()` directly.
- **Alt B: reuse `submitDebounced`.** No new API, but +200 ms per step and the coalescing
  window could swallow a step whose next request arrives inside it.
- **Alt C: shorten `zoomDebounceMs`.** Rejected: it is tuned for gesture/auto-zoom commit
  coalescing; changing it would alter unrelated render churn.

### D4 - While a walk is pending, the walk owns the magnification

Follow commits during a pending walk carry center and angle only and render at the current
step. The newest auto-zoom request replaces the walk's target (monotonic per leg, never
passing the requested value, ending exactly on the last requested one - the car's rule).
The persisted viewport keeps the **final target** throughout, so `smooth-zoom`'s
"Viewport records final magnification" requirement is unchanged.

- **Alt A (chosen): the walk owns the mag.** Otherwise a fix arriving mid-walk would
  commit the target and land the whole difference in one frame - the defect this change
  removes, only intermittently.
- **Alt B: let fixes re-commit the target and let the walk re-derive.** Rejected: the same
  one-frame landing, plus the car already records that re-committing the value read back
  must not cancel a pending walk (`AutoMapRenderer.kt:513-516`).

### D5 - The display animation target becomes explicit state

`MapCanvasUiState` gains the magnification the display animation eases toward
(`zoomAnimationTargetMag`); it equals `viewport.magnification` when no walk is pending and
the walk's current step otherwise. The screen's animation start/retrack and its render-land
handoff compare against this value instead of `viewport.magnification`
(`MapCanvasScreen.kt:315, :336-340, :409-434`).

- **Alt A (chosen): explicit state field.** The persisted target and the display target are
  genuinely different values during a walk; separating them keeps the persistence rule and
  the animation rule from fighting.
- **Alt B: overload `viewport.magnification` with the step.** Rejected - it would persist
  intermediate magnifications, violating the spec.

### D6 - The pivot is the resolved follow anchor while a follow mode is active

The animation anchor pixel becomes `(resolvedAnchor.fx * canvasWidth, resolvedAnchor.fy *
canvasHeight)` when follow is active, and keeps the current rule (screen center for
buttons/keyboard/auto-zoom, cursor for the wheel) otherwise. The marker compensation
(`LocationMarkerOverlay.kt:67,132`) already uses the same anchor, so keeping the two in
sync is what makes the vehicle pixel stay fixed. `guidelines/UI.md:36`'s parity table and
`guidelines/MapRendering.md` sections 11-13 are updated to state this.

- **Alt A (chosen): follow anchor pixel.** Matches the car, and it is the point the follow
  frame is rendered around (`anchorCenter`), so content under the marker cannot shift.
- **Alt B: keep the center pivot and instead offset the display transform.** Rejected: the
  offset would have to reproduce `(1 - s) * (anchor - center)` exactly, i.e. compute the
  same pivot with extra steps.

### D7 - The window is 0.25 magnification levels

`ZOOM_BLIT_LIMIT = 0.25` in `:core`, derived exactly as on the car: the 1.2 overrun canvas
(`MapRenderer.kt:953`) covers `log2(1.2) = 0.263` levels, minus margin. Sharing the
derivation (not the constant by file) keeps phone and car on the same bound.

- **Alt A (chosen): 0.25.** Same bound as the car, same derivation, verified margin.
- **Alt B: use the full 0.263.** Rejected: no margin; the car kept 0.25 for that reason.
- **Alt C: raise the overrun factor to allow larger steps.** Rejected as the default for the
  car's reason (`AutoMapRenderer.kt` D3): the overrun size is paid by every render of the
  session, not just during a transition. Held as the data-driven fallback if the walked
  entry is too slow - a larger window means fewer steps for the same change.

### D8 - A manual zoom cancels the pending auto-zoom target and walks its own change (refined during implementation)

Pinch, zoom buttons, keyboard shortcuts and the scroll wheel cancel a pending walk and then apply
their own commit through the same bounded path, from the frame in hand. A programmatic camera fit
(the POI fit) passes `walk = false` and lands directly.

- **Alt A (chosen): the manual commit is itself bounded.** The spec's rule is unconditional ("the
  displayed frame SHALL NOT show a magnification farther than the window the frame in hand can
  serve") and the coverage requirement applies to manual zoom-out too - a one-level manual zoom-out
  would otherwise scale the 1.2x frame to 0.5 and expose 40% of the surface. Walking costs little:
  the phone's tile path serves fractional steps from the per-level tile cache, so a one-level
  change renders natively only where it crosses `floor(mag)`.
- **Alt B (the car's shape): the manual commit lands directly** (the car passes `walkZoom = false`
  on `setViewport`). Rejected for the phone: the car's gesture and button paths already render at
  the committed magnification, so no wrong-magnification frame is displayed there - the phone's
  Compose display scale is what needs the bound, and it runs for manual input too.
- **Alt C: one render plus a clamped display scale for manual input.** Rejected as the mechanism -
  it stays "short capped ramp, then a jump to the correct geometry", i.e. the reported symptom. The
  clamp survives as the guard (D9).
- A camera fit lands directly because it never runs the display zoom animation (its viewport change
  is applied by a plain render), so no unbounded frame is shown.

### D9 - Coverage holds by construction, with a display-side clamp as a guard

Every step is within the window, so a zoom-out can never scale the frame below the area it
covers. The display scale is additionally coerced to `+/-WINDOW` (parity with the car's
`displayedScale()`), so a future bug in the step arithmetic degrades to a bounded scale
instead of an uncovered strip.

### D10 - Cold start lands directly

No front buffer (`renderViewport == null` / `frontMag <= 0`) means there is nothing to
transition from: the requested magnification lands in one render, which is the spec's
"no frame yet" case and the car's `overrunMag <= 0` branch.

### Threading / Lifecycle

- **No new component, no new dispatcher.** The walk's arithmetic (`ZoomWalk`) is
  dispatcher-free and synchronous. The pacing runs inside the ViewModel's existing
  `viewModelScope.launch { renderer.frameFlow.collect { ... } }` (`:1884-1907`), i.e. on the
  main dispatcher, where the uiState writes already happen - one state emission per landed
  frame, no allocation in the hot path (the step is a `Double` and a small immutable
  holder).
- The screen stays a pure consumer: it reads `zoomAnimationTargetMag` and
  `resolvedAnchor` in its existing `withFrameNanos` loop (`MapCanvasScreen.kt:361-434`) and
  never requests renders itself.
- The render request from a walk step goes through `MapRenderer`'s existing
  `enqueueRenderJob` path on the renderer's own scope, exactly as today's renders do; no
  lock, lock scope or buffer-lifecycle rule changes (`guidelines/MapRendering.md` section 2
  stays intact).
- Lifecycle: the walk lives in ViewModel state, so a config change (activity recreation)
  re-reads the current step and resumes; a pause stops new renders as today. Nothing native
  and nothing surface-bound is touched, so no car-surface rule applies.

## Risks / Trade-offs

- **Render burst during a walked entry** (up to ~16 renders, ~25-300 ms each) -> bounded by
  the window; measured on device (task 6.x); fallbacks are a larger window (D7 Alt C) or the
  clamp variant (D1 Alt B).
- **Display lag:** the display eases toward a step that the front buffer already holds, and
  a step whose ease is still running is retracked by the next landed step. Mitigation: the
  retracking rule is already specified; per-step deltas are <= 0.25 levels, so the worst
  visual is a bounded, sub-level easing - never wrong-magnification geometry.
- **Follow commits during a walk** could land the target if D4 were not enforced ->
  mitigation: D4 plus a unit test that pumps a fix mid-walk and asserts no step larger than
  the window is rendered.
- **A walk that never lands** (render failure, surface loss, no frames) would leave the
  display short of the target -> mitigation: the walk is `frameFlow`-driven and simply waits
  (last-good display preserved), and the persisted viewport already holds the target, so a
  later render (fix, gesture, restart) reconciles it. No timeout that commits the target
  behind the frame.
- **Two pacing paths (follow commits and walk steps)** could interleave renders at
  different magnifications -> mitigation: both render at the walk's current step, and the
  front-buffer mag is the single source the walk compares against.
- **Battery/CPU on the phone** during long entries -> the cost is paid only while a
  magnification change larger than the window is in flight, and never while the vehicle
  stands still (the walk advances per landed frame, not per tick).

## Migration Plan

- **Rollout:** plain app update; no persistence, settings or schema change. The previous
  viewport magnification keeps its value and is the walk's first target, so an upgrade
  cannot produce an unexpected zoom after the update.
- **Rollback:** revert the phone-side Kotlin edits (`MapCanvasScreen.kt`,
  `MapCanvasViewModel.kt`, `MapRenderer.kt`, `LocationMarkerOverlay.kt` coverage only) and
  delete `core/.../ZoomWalk.kt`; the display falls back to the single-scaled-bitmap
  animation. The spec delta reverts with the change; nothing on disk depends on it.

## Open Questions

None that change the specs, the approach or the task breakdown. The two proposal questions
are resolved here as D1 (walked variant chosen, clamp variant documented as the fallback)
and D5 (delta-dependent duration accepted: a walked transition lasts as long as its renders
take, which is the car's behavior too).
