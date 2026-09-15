## Context

See proposal.md — Why. Current state that shapes the approach:

- `SpeedZoomTable.compute()` already returns fractional targets (14.5 @ 75 km/h) and is shared by phone (`MapCanvasViewModel`) and AA (`AutoZoomController`). Both throw the fraction away at the commit gate (`roundToInt()`), then gate commits on integer-era machinery: 3 identical-sample stability, 2.5 s cooldown, 1.0-level hysteresis.
- Fractional magnification is proven end-to-end: pinch commits continuous values on the phone; AA's `AutoMapRenderer` renders `magnification = 2^viewportZoomFraction` and pinch `zoomStep` already returns `(fraction, int)` pairs — the fraction is the actual render scale.
- Phone display smoothness exists only for discrete input: `MapCanvasScreen.animateDiscreteZoom*` drives a 250 ms `ZoomAnimation` (ease-out cubic, retrack-capable) that scales the front buffer until the native render at the target lands. Auto-zoom commits never enter this path — they write `viewport.magnification` and re-render, so the display snaps on render completion.
- AA has no display-animation need: each commit renders a fresh frame at the fractional magnification and the follow-extrapolation loop (`auto-smooth-follow`) already eases the display between fixes.

## Goals / Non-Goals

**Goals:**
- One shared fractional-convergence primitive used by both phone and AA controllers.
- Slower zoom transitions: ≤ 0.5 magnification level per position update (vs 1.0 in the current spec), no integer rounding.
- Phone auto-zoom commits visibly eased via the existing front-buffer animation at a slower duration (~500 ms).
- Preserve manual-zoom suspension + speed-band re-engage, turn/curve/post-turn floors, initial-zoom-at-15 semantics.

**Non-Goals:**
- No changes to the speed table itself or its interpolation (spec auto-speed-zoom SPEED_ZOOM_TABLE unchanged).
- No display animation on AA beyond what exists (fresh fractional frames + follow ease are sufficient).
- No native/stylesheet/persistence changes.

## Decisions

### D1: Shared rate-limited convergence helper (core)

Add a pure function to the speed-zoom domain in `core` (same file family as `SpeedZoomTable`), e.g. `SpeedZoomTable.stepToward(current: Double, target: Double, maxStep: Double = 0.5): Double`:

```
delta = target − current
|delta| < 0.05            → return current (epsilon no-op, no commit/render)
step   = delta × 0.3     (proportional: strength depends on the gap "is → target")
step   = clamp(step, −0.5, +0.5)
step   = floor(step, ±0.05)   (sub-epsilon gain steps floored so convergence
                               lands inside the deadband in bounded updates)
return current + step
```

Rationale: phone VM and `AutoZoomController` currently duplicate constants and semantics; both switch to the same stepping math, single test surface. The proportional gain (instead of a fixed max step per update) makes the zoom move fast when far from the target and gentle when near — speed-noise target jitter (e.g. around a table breakpoint) is then damped to sub-threshold motion instead of being chased at full step size back and forth, which rendered as zoom "pumping". Alternative (keep per-module copies) rejected — the drift that produced integer rounding on both sides is exactly what a shared primitive prevents.

**First-commit exception:** the first auto-zoom computation after navigation/free-drive start or drive-preset reset commits directly to the target (no stepping), preserving the "Speed unknown → jumps directly to the target" scenario (auto-speed-zoom) while the initial magnification stays 15.0. Implemented as: when the controller has no previous committed target, return the target unchanged.

### D2: Phone controller rewrite (`MapCanvasViewModel`)

Replace the integer commit block (lines ~944-970) with:

- `target = maxOf(speedTarget, turnFloor, curveFloor, postTurnFloor)` (unchanged, now committed fractionally).
- `newMag = if (no committed target yet) target else stepToward(newMag, target)`.
- Commit `newMag` directly (no `roundToInt`, no `targetInt != newMag` guard, no `zoomTargetStableSamples`, no `ZOOM_COOLDOWN_MS`/`ZOOM_COMMIT_SAMPLES`/`ZOOM_HYSTERESIS_MAG`). Epsilon no-op returns without render (the existing `viewportMoved` check already skips the render when mag is unchanged).
- Keep: `autoZoomSuspended` + `lastSpeedBandIndex` re-engage logic, `currentTargetMag`, filtered speed, per-fix cadence.

Rationale: stepping IS stability (constant target → epsilon no-op) and IS hysteresis (≤0.5 level change per update), so the three integer-era knobs collapse into `maxStep` + `epsilon`. Alternative — keep samples/cooldown with floats — rejected: exact-equality stability never fires on a continuously interpolated target.

### D3: Phone display animation (B-plane)

- `ZoomAnimation.start`/`retrack` gain a `durationMs` parameter (default `DEFAULT_DURATION_MS`, keeping existing tests/behavior); one shared instance still serves both classes of zoom.
- `MapCanvasUiState` gains a monotonic `autoZoomCommitTick: Int`, incremented exactly when the auto-zoom block commits a magnification (not by pinch/buttons — discrete paths keep calling `animateDiscreteZoom*` themselves).
- `MapCanvasScreen` observes the tick in a `LaunchedEffect` and calls `animateDiscreteZoomToCenter(durationMs = 500)` — center anchor is correct because follow mode keeps the vehicle centered. Retrack covers consecutive commits (accelerating/decelerating).

Rationale: explicit signal, no gesture-source ambiguity, display state stays in the screen layer as today (VM never touches the animation). Alternative — detect commits in the frame loop from mag-vs-frontMag diffs — rejected: indistinguishable from gesture commits.

Risk (accepted): commit → render request happens synchronously in the VM on the GPS fix; the animation starts at next composition — a one-frame window where a fast render could land before the animation starts. Handoff logic already shows the rendered frame immediately in that case (no snap, just no easing on that particular commit). Consecutive commits retrack, so the next animation starts from the true displayed scale.

### D4: AA controller + screens

- `AutoZoomController.onSpeed` returns `Double?` (fractional target, stepped per D1, first-commit exception, suspension/band logic unchanged; `commitSamples`/`cooldownMs`/`hysteresisMag` constructor params removed).
- `FreeDrivingScreen`/`NavigationScreen`: `newZoom` becomes `Double?`; commit via
  `mapRenderer.setViewport(lat, lon, newZoom.roundToInt(), angle, newZoom)` —
  integer slot keeps the viewport model, fifth arg carries the fractional render magnification (identical shape to pinch `zoomStep`'s `(fraction, int)`).
- `autoZoomTarget` and `shouldCommitViewport` signatures follow (gate on null).

Rationale: minimal surface change; the renderer already renders `2^fraction`, so the fix is routing the controller's fraction into the existing 5th parameter instead of `Int.toDouble()`. Alternative — render at integer and scale the surface — rejected: doubles the zoom pipeline and reintroduces jumps.

### D5: Tunables

- `MAX_ZOOM_STEP_PER_UPDATE = 0.5` levels (spec changed: 1.0 → 0.5, "slower").
- `ZOOM_CONVERGENCE_GAIN = 0.3` — step = gain × remaining gap (distance-
  proportional; anti-pump).
- `ZOOM_EPSILON = 0.05` levels (plus the 0.05 step floor for convergence).
- `AUTO_ZOOM_ANIMATION_MS = 650` (discrete stays 250; 500 felt slightly too
  fast and contributed to the pump look, so the auto-zoom easing is a little
  slower).
- Initial magnification 15.0 unchanged.

## Risks / Trade-offs

- **Render churn while accelerating** → fractional commits land up to ~1 Hz (vs every 2.5 s today). Mitigations: `GPS_FOLLOW_RENDER_INTERVAL_MS` coalescing; native tile data cache (512 tiles) makes fractional re-renders incremental; the display animation masks intermediate frames.
- **Continuous fractional renders** (e.g. 14.5 levels) have no per-level label/LOD boundaries — stylesheet switches (building labels at ≥16) apply to the *target*, but intermediate frames show mid-LOD content. Mitigated by the display animation bridge + stepped convergence spending little time at intermediate fractions. Native correctness already handled: pinch renders fractional today.
- **`first-commit jumps straight to target`** after drive restart could still surprise after a resume at high speed — accepted, matches the existing spec scenario explicitly.
- **Per-animation duration parameter broadens `ZoomAnimation` API** → existing tests use constructor duration; `start`/`retrack` default keeps them green; new tests cover the auto-zoom duration.
- **AA integer model drift**: viewport `zoom` (int) and render fraction can disagree briefly (int rounds to nearest). Same as pinch today; fractional render is authoritative for display.
