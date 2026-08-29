## Context

See `proposal.md` — Why. Current state that shapes the approach:

- `AutoMapRenderer` renders to a raw `Surface` via `lockCanvas`/`unlockCanvasAndPost`, serialized across renderers by a shared `surfaceLock`. No overrun buffer, no blit — every viewport change is a full native `renderToBitmap` at surface size (100-500 ms), debounced at 100 ms.
- Follow mode: `setGpsMarker` sets `viewport = fix` and calls `requestRender()`. GPS fixes arrive at ~1 Hz from the same `LocationService` (via `AutoLocationProvider`).
- The renderer has `pause()`/`resume()`, `surfaceFailed` handling, and a `renderSignal`-driven loop on `Dispatchers.Default`. The GPS marker is a Kotlin overlay drawn in `drawToSurface`.
- The phone change `phone-follow-smoothing` creates a shared `FollowPrediction` helper in `core` — this change reuses it.

## Goals / Non-Goals

**Goals:**
- Smooth follow-mode scrolling on the car display between 1 Hz fixes (extrapolation + correction easing).
- Cut full native renders: viewport changes within the overrun region are served by blit, not re-render.
- Respect the surface lifecycle, shared `surfaceLock`, and head-unit battery/thermal constraints.
- Marker glides with the map at the predicted position.

**Non-Goals:**
- No sensor fusion — linear extrapolation from GPS speed + heading (same as phone).
- No changes to the navigation engine input path (real fixes only).
- No changes to non-follow interaction (pan/zoom gestures keep current behavior).
- No phone renderer changes — phone handled by `phone-follow-smoothing`.

## Decisions

### D1: Overrun buffer at 1.2x surface size

Render to an offscreen bitmap at ~1.2x the surface size; draw the visible region to the surface. Margin ≈ 0.1 × surface each side, covering ~8-15 s of prediction at nav zoom.

- **Alternative A — render at surface size, blit within it**: rejected — no margin, any shift reveals edges.
- **Alternative B — 1.2x overrun (chosen)**: matches the phone renderer's `canvasOverrun = 1.2`; one constant to reason about.
- **Alternative C — 1.5x overrun**: more margin but 2.25x render pixels per full render; rejected — renders are already the expensive path.

### D2: Sub-region blit draws the shifted overrun buffer directly to the surface

On a viewport change within the overrun region, `lockCanvas` → `drawBitmap(overrun, dx, dy)` → `unlockCanvasAndPost`. No intermediate bitmap, no allocation. When the shift exceeds the margin, fall back to a full render at the new center.

- **Alternative A — blit to an intermediate bitmap, then to surface**: rejected — pointless copy; the surface canvas can draw the overrun buffer directly.
- **Alternative B — direct draw (chosen)**: one draw call per frame; the exposed strip never reaches the surface because the margin covers the shift.

### D3: Extrapolation loop as a coroutine loop on the renderer scope, ~30 fps

A `while` loop with `delay(16-33 ms)` on the renderer's existing `Dispatchers.Default` scope, gated on `resumed && followMode && moving && surface valid`. Each tick: compute predicted position via `FollowPrediction`, blit the overrun buffer by the delta, draw the marker at the predicted position.

- **Alternative A — Choreographer via main Handler**: precise vsync but the renderer is Default-dispatched; adds main-thread plumbing and cross-thread state. Rejected.
- **Alternative B — coroutine loop (chosen)**: matches the existing `renderSignal` pattern; 30 fps is smooth enough for a car display and cheaper on battery than 60 fps.
- **Alternative C — drive from the car host's `Screen.invalidate` cycle**: the host does not drive custom Surface rendering; rejected.

### D4: Correction easing (same as phone)

On fix arrival, ease the display offset from `(predicted − true)` to 0 with
`offset *= exp(−dt/τ)`, τ = 0.3 s — the phone's `FollowPrediction.easeAlpha`
default. (The original design text said τ ≈ 100 ms; the phone's tuned value is
0.3 s and the AA must match it — a shorter τ makes the display run further
ahead of the fix and the fix-arrival correction jerk back, which reads as
overshoot.) Reuses `FollowPrediction.easeAlpha` from `core`.

- **Alternative A — snap to fix**: rejected — visible jump, defeats the purpose.
- **Alternative B — linear ramp**: rejected (kink at end); same rationale as phone D4.

### D5: Shared `FollowPrediction` helper from `core`

Reuses the class created in `phone-follow-smoothing` (`core/src/main/java/com/naviveylin/core/FollowPrediction.kt`). The AA change depends on the phone change landing first (or the helper being extracted independently).

- **Alternative A — duplicate the math in `AutoMapRenderer`**: rejected — same math, one source of truth.
- **Alternative B — shared helper (chosen)**: one implementation, two consumers; unit-tested once.

### D6: Loop gating for battery/thermal

The loop runs only when: renderer resumed, follow mode active, valid fix, speed
> ~1.8 km/h (0.5 m/s — the phone's `fix.speedKmH > 1.8` check), surface valid,
not `surfaceFailed`. Each iteration checks these before touching the surface;
the existing `surfaceLock` serializes with other renderers. A stale fix does NOT
stop the loop: `FollowPrediction` holds the position beyond its extrapolation
window, so the display eases back to the last fix instead of freezing ahead of
it (the phone's per-frame loop behaves the same; freezing ahead is what reads
as a massive overshoot during GPS gaps). When the loop is gated off, the
display state is dropped so the marker falls back to the raw fix (phone's else
branch).

- **Alternative A — run whenever follow mode is active**: rejected — head-unit battery/thermal; a stationary car must not spin a 30 fps draw loop.
- **Alternative B — gated loop (chosen)**: stops drawing when stationary; the last frame stays on screen.

### D7: Marker drawn at predicted position

The GPS marker overlay (and destination marker) project the *predicted* position against the displayed viewport each frame, so the marker glides with the blitted map. The raw fix remains the render target.

- **Alternative A — marker at raw fix**: rejected — marker would lead/lag the blitted map content.
- **Alternative B — predicted marker (chosen)**: consistent with the phone change's marker behavior.

## Risks / Trade-offs

- **Surface contention** (two renderers, one surface) → existing `surfaceLock` serializes; the loop checks `surfaceFailed`/`isValid` each tick and stops on failure. Mitigation: reuse the existing failure-reporting path.
- **Battery/thermal on head units** → movement gating + 30 fps cap; if still hot, drop to 15 fps or blit only when the delta exceeds 0.5 px (measure on device).
- **AAOS host throttling of custom Surface draws** → unknown; if the host throttles `lockCanvas`, the loop naturally coalesces (blit is idempotent). Test on device.
- **Full render cost at 1.2x** → each full render is 1.44x pixels; renders become rare (overrun-driven), so net cost drops. Measure render frequency before/after.
- **Extrapolation drift on curves** → correction easing absorbs it; if drift is large, the offset exceeds the margin and triggers a full render (correct behavior).

## Migration Plan

1. Land `phone-follow-smoothing` first (creates `FollowPrediction` in `core`).
2. Add the overrun buffer + blit to `AutoMapRenderer` (render at 1.2x, draw visible region).
3. Add the gated extrapolation loop + easing; marker draws at predicted position.
4. Verify: build, unit tests (`AutoMapRendererTest`), on-device drive test (smooth scroll, no edge reveal, marker on road, no surface lock errors).
5. Rollback: revert the overrun/blit/loop changes — the renderer falls back to full-render-per-fix (current behavior).

## Open Questions

- Does the AAOS host throttle custom Surface draw rates? (Deferrable — test on device; the loop coalesces naturally if so.)
- Real head-unit GNSS rate (5-10 Hz?) — a shorter extrapolation window reduces drift risk but does not change the design.
