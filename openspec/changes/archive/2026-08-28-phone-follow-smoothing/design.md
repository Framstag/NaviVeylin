## Context

See `proposal.md` — Why. Current state that shapes the approach:

- `LocationService` emits `GpsFix` at ~1 Hz (`UPDATE_INTERVAL_MS=1000`, `FASTEST_INTERVAL_MS=500`, `MIN_DISTANCE_M=5`). `GpsFix` carries `lat/lon/accuracy/speedKmH/smoothedBearing/markerBearing/time`.
- `MapRenderer` already has: overrun buffer (1.2x), `trySubRegionBlit` (fix-driven blit + emit), debounce, double buffering, tile cache. `emitFrame(viewport, marker, crop)` already supports cropped vs full emission.
- `MapCanvasViewModel` follow mode: EMA-smoothed center → `prepareViewport`/`renderMap`; marker updated directly via `updateMarkerState` (live fix) and snapshotted into frames.
- `MapCanvasScreen` draws the emitted bitmap scaled to fill the canvas, plus the marker overlay (Compose Canvas). It already applies live gesture transforms to the bitmap.
- The gap: between fixes (up to 1 s) nothing moves — the map freezes, then the fix-driven blit snaps it.

## Goals / Non-Goals

**Goals:**
- Smooth follow-mode scrolling between 1 Hz fixes (extrapolation + correction easing).
- Zero per-frame bitmap allocation (GC pressure at 60 fps is unacceptable).
- Keep the existing fix-driven blit/render pipeline untouched (tested behavior).
- Marker glides with the map at the predicted position.

**Non-Goals:**
- No sensor fusion (accelerometer/gyro) — linear extrapolation from GPS speed + heading is sufficient for a 1 s window.
- No changes to the navigation engine input path (real fixes only, unchanged).
- No changes to non-follow interaction (pan/zoom gestures keep current behavior).
- No faster GPS requests — the chip is 1 Hz; extrapolation is the fix.

## Decisions

### D1: Display clock lives in the Compose screen, not the renderer

The extrapolation loop runs in `MapCanvasScreen` via `withFrameNanos` (Compose's frame clock), not in `MapRenderer`.

- **Alternative A — renderer-side Choreographer loop**: renderer blits to the predicted position and emits a new cropped bitmap each frame. Rejected: 60 screen-sized bitmap copies + allocations per second (GC churn); renderer runs on `Dispatchers.Default` with no Looper, so Choreographer would need main-thread plumbing.
- **Alternative B — screen-side offset (chosen)**: the screen draws the *same* emitted bitmap each frame with a computed pixel offset. Zero copies, zero allocations. The screen already owns the draw + marker overlay and has the live `GpsFix` via `uiState.gpsLocation`.
- **Alternative C — ViewModel coroutine loop with `delay(16)`**: no Choreographer, but timing jitter and a second clock source. Rejected — Compose already provides a frame clock.

### D2: Emit overrun-sized frames so the screen can offset within the margin

The screen's offset must not reveal bitmap edges. The renderer's overrun buffer (1.2x) provides the margin: emit the full overrun buffer (change `emitFrame` to `crop=false` in follow mode) instead of the extracted center region. The screen draws the overrun bitmap at NATURAL size (scale = 1) with the visible window shifted by the predicted delta — the overrun bitmap is already 1.2x the canvas, so natural-size draw + offset keeps the window inside the margin. (Correction during implementation: the original design said "scale to fill the canvas"; scaling the overrun bitmap down would shrink the margin and misalign the offset scale — natural-size draw is required.)

- **Alternative A — keep cropped emission, screen offsets**: rejected — a screen-sized bitmap has zero margin; any offset reveals a 1 px+ edge strip immediately.
- **Alternative B — overrun-sized emission (chosen)**: margin ≈ 0.1 × screen each side ≈ 108 px at 1080p, covering ~8-15 s of prediction at nav zoom. When the offset exceeds the margin, the screen requests a render at the predicted position (new overrun buffer).
- **Alternative C — renderer-side blit per frame**: rejected in D1 (allocation churn).

The existing fix-driven `trySubRegionBlit` stays as-is: on fix arrival it shifts the overrun buffer and emits a frame centered on the true fix; the screen's offset then eases from `(predicted − true)` to 0. Both paths (blit and full render) produce a frame whose viewport the screen offsets against — consistent regardless of how the frame was produced.

### D3: Linear extrapolation along smoothed bearing, clamped

`predicted = lastFix + speed × heading × (now − fixTime)`, using `smoothedBearing` (same signal the map rotation uses — less jitter than `markerBearing`). Clamp: if `now − fixTime > 1.5 s` (fix lost), stop extrapolating and hold the last predicted position. If speed or bearing is unavailable, hold position.

**Speed source (rev 2 — from the emulator GPX log analysis):** the receiver GPS speed is the PRIMARY estimate (`eff = gpsSpeed`), and the 70% cap applies ONLY while the GPS speed is decreasing (>5% per fix, `eff = min(gpsSpeed, 0.7 × smoothAvg)` where `smoothAvg` is an EMA of the position-diff average, τ=1 s). Rev 1 used `min(gpsSpeed, 0.7 × avgSpeed)` unconditionally; the emulator log showed the raw position-diff average is jitter-inflated (±2-5 m per fix) and swings 21-123 km/h while the receiver speed is a smooth 55-75 km/h, so the unconditional cap made the displayed speed oscillate 15-74 km/h ("stop and go") and left the display 10-20 m behind the fix (catch-up "overshoot" at stops). The rev 2 logic: steady state uses the full receiver speed (no deficit, no oscillation); deceleration caps the prediction so a lagging position-diff speed cannot run the map past a braking vehicle; the smoothed average removes the jitter inflation from the cap value. A GPX-track simulation (2 stops, 7 hard brakes) showed rev 2 cuts the worst backward jump 5.5→2.2 m, the past-stop drift 23→2.9 m, and the mean speed error 2.4→1.2 m/s vs rev 1.

**Stop handling (rev 2b — from the emulator log):** two mechanisms. (1) A zero GPS speed means stopped and wins over a stale average — `update()` keeps `NaN` as unknown speed (only negatives are coerced to 0), and `predictedPosition()` takes the capped min whenever the GPS speed is valid, including zero. (2) The fix-movement check: when the fix has not moved more than 5 m for 3 s the vehicle is considered stopped and the prediction holds — the emulator reports a non-zero speed (6.3 km/h) while stationary, so a zero GPS speed cannot be relied on. The 3 s base threshold must exceed the longest fix gap (the emulator sends fixes at irregular 1-2.4 s intervals): a 1 s threshold fired during normal driving, holding the prediction mid-gap and easing the display backward (a visible "overshoot" slide every gap). A fast path (GPS speed < 10 km/h AND no movement for 1 s) catches a real stop quickly so the prediction does not drift past it. The extrapolation window was raised 1.5 → 3.0 s for the same reason: a window shorter than the fix gap made the prediction hold mid-gap.

- **Alternative A — marker bearing (freshest)**: rejected — jitterier; the map scroll should match the rotation signal.
- **Alternative B — sensor fusion**: rejected (Non-Goals).
- **Alternative C — no clamp**: rejected — a lost fix would extrapolate indefinitely.
- **Alternative D — GPS Doppler speed only**: rejected after device testing — overshot on some receivers; the average-only variant overshot from jitter-inflated distances.
- **Alternative E — min(avg, gps) without cap**: rejected after device testing — still overshot when the GPS speed is biased high or lags (both estimates inflated); the 0.7 cap bounds the overshoot by construction.
- **Alternative F — conservative discount (0.9×)**: rejected — eliminated backward jumps at moderate jitter but still overshot during hard braking with a lagging GPS speed.

### D4: Exponential ease-out for correction smoothing

On fix arrival, the screen eases its display offset from `(predicted − true)` to 0 with `offset *= exp(−dt/τ)`, τ = 500 ms. No overshoot, one scalar of state.

**Tau (rev 2):** 300 ms. Rev 1 raised τ to 500 ms because the unconditional speed cap (D3 rev 1) made the displayed position lag the fix, so each fix arrival produced a forward nudge — the map speed oscillated ("stop and go"). With the rev 2 speed model the prediction speed equals the receiver speed in steady state, so the fix-arrival correction is small and a shorter τ keeps the display lag `v·τ` (and the stop catch-up) small — the user reported "more overshoot" at τ=500 ms, which the log analysis traced to the display lag/catch-up, not the nudge. The GPX-track simulation shows τ=300 ms balances the past-stop drift (2.9 m) against the display lag (4.3 m); τ=500 ms reduces the drift to 2.3 m but doubles the lag to 7.6 m.

- **Alternative A — linear ramp**: visible kink at the end; rejected.
- **Alternative B — critically damped spring**: smoother but needs velocity state; rejected as overkill for a ≤ 1 s correction.

### D5: Shared prediction/easing helper in `core`

New `FollowPrediction` class in `core/src/main/java/com/naviveylin/core/`: `update(fix)`, `predictedPosition(now)`, `easeOffset(...)`. Created in this change, reused by `auto-smooth-follow`.

- **Alternative A — duplicate in each renderer**: rejected — same math, one source of truth.
- **Alternative B — put it in `MapRenderer`**: rejected — AA needs it too; `core` is the shared module.

### D6: Loop gating

The `withFrameNanos` loop runs only when: follow mode active, valid fix present, speed > ~1 m/s. Otherwise the screen draws the last frame unshifted (current behavior). Non-follow gestures bypass the loop entirely.

## Risks / Trade-offs

- **Offset exceeds overrun margin** (long fix gap, high speed) → screen requests a render at the predicted position; until it completes, the offset is clamped to the margin (map holds at edge, no blank strip). Mitigation: clamp + render request.
- **Overrun-sized emission changes Compose draw cost** (1.44x pixels per frame) → measure; the bitmap is drawn once per frame with a transform, GPU-scaled. If costly, fall back to emitting cropped frames and let the renderer blit on a sub-second cadence (D1 alternative A).
- **Marker/frame desync** → the screen computes both the map offset and the marker position from the same predicted position each frame; they cannot diverge.
- **Interaction with live gesture transforms** → the follow offset composes with the existing gesture transform (offset applied first, then gesture). Verify during implementation.
- **Existing blit path regression** → the fix-driven `trySubRegionBlit` is untouched; only the emission crop flag changes in follow mode. Existing tests cover the blit.

## Migration Plan

1. Add `FollowPrediction` to `core` with unit tests.
2. Change follow-mode emission to overrun-sized frames in `MapRenderer` (crop flag).
3. Add the `withFrameNanos` offset + easing loop to `MapCanvasScreen`; marker draws at predicted position.
4. Verify: build, unit tests, manual drive test (smooth scroll, no edge reveal, marker on road).
5. Rollback: revert the crop-flag change and the screen loop — the fix-driven blit remains as the fallback behavior.

## Open Questions

- Does the overrun-sized emission measurably affect Compose draw performance on low-end devices? (Deferrable — measure during implementation; fallback documented in Risks.)
- AAOS head units may report GNSS at 5-10 Hz — irrelevant here (phone change), but informs the AA change's extrapolation window.
