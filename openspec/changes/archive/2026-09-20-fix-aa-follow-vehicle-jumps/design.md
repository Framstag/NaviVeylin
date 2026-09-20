# Design — Fix AA Follow-Mode Vehicle Jumps

## Context

See `proposal.md` — Why. The AA follow pipeline (`AutoMapRenderer` extrapolation loop + shared `FollowPrediction`) is the pre-fix phone pipeline: the display eases toward the extrapolated prediction with no forward-only rule (fix-arrival backward slide), the gate-off branch zeroes the displayed position (`displayLat = NaN`), and `setGpsMarker` re-anchors the viewport on every stationary fix. Spec delta (this change): `auto-smooth-follow` — Correction easing becomes forward-only (hold when the target lies behind); Extrapolation loop gating freezes the displayed position + marker at stops.

Current state (unchanged by this design): overrun buffer (1.2x), sub-region blit, resolved-anchor blit offsets (single resolved anchor, from `fix-aa-follow-blit-anchor-mismatch` — already in the tree at L714-726/L783-796), `reengageFollow`/`reCenter` viewport commits, `anchorCenterFor`/`clampAnchorOutOfPane` anchor resolution, the screens' fix feed (`NavigationScreen`/`FreeDrivingScreen` → raw GPS + derived speed/bearing via `fixDerivation`). `FollowDisplayState` (core, 11 tests) implements the phone's forward-only + teleport semantics and is currently marked phone-only.

## Goals / Non-Goals

Goals:
- Monotonic forward display under AA: no backward correction slide at fix arrival.
- Frozen stop: displayed position + marker hold at the stop (no NaN reset, no per-jitter re-anchor, no raw-fix fallback); seamless resume.
- Smaller freeze-then-advance step at the overrun margin (parity with the phone's 200 ms render throttle).

Non-Goals:
- Changing anchor presets/resolution (in-flight `fix-aa-vehicle-anchor-not-applied`, `anchor-per-surface-visible-area`).
- Changing `FollowPrediction` itself (shared core; AA passes raw fixes — single position source; the hold rule lives at the call site).
- Changing the screens' fix feed, the navigation session, or the renderer's native render/blit internals (only inputs to them change).
- No new capabilities: reuses `FollowDisplayState` and the renderer's existing volatile display fields.

## Decisions

### D1 — Adopt `FollowDisplayState` in the AA extrapolation loop (forward-only)

**Decision**: `AutoMapRenderer` owns a `FollowDisplayState`; `extrapolationTick` replaces the inline ease (`display += (predicted − display)·α`) with `state.advance(predicted.first, predicted.second, dtSec, headingDeg)`, where `headingDeg` = the current fix's effective bearing (`gpsMarkerBearing`; NaN → rule skipped — no extrapolation runs without a heading anyway, matching the phone). After each advance, copy `state.lat/lon` into the existing `@Volatile displayLat/displayLon` fields — they remain the single source for the marker (`markerPosition`) and the margin render target (`anchorCenterFor`). The teleport snap (>50 m) replaces the old hard-jump behavior on GPS teleports/reroutes for free.

- **Alternatives**
  - A: Inline the forward-component check in the loop (~10 lines, no second position source). Rejected: duplicates the tested hold/resume/teleport semantics; `FollowDisplayState` already exists in core with 11 passing tests — the phone and AA then share one tested monotonic rule.
  - B: Clamp the ease target to `max(fix, predicted)` — rejected (phone D2-A/B history): leaves a permanent forward lead with no convergence, and no monotonic guarantee on curves.

**State-sync across the freeze** (the second position source concern): the state object and the volatile fields can diverge while the gate is closed (the loop does not tick). On the first tick after a gate-off period, re-sync: if the volatile display is non-NaN and differs from the state's position, `state.reset()` first so the next `advance` seeds from the held position — the display continues exactly from the frozen position (spec: "resume continues from the frozen position"). `clearOverrunBuffer` (surface change) resets the state alongside the volatile fields.

**Threading**: `advance` runs only on the extrapolation loop's scope (Dispatchers.Default); the volatile fields remain the cross-thread channel (marker draw thread, `setGpsMarker` seed writes, tests). No shared mutable state between threads beyond the existing volatiles.

### D2 — Frozen gate, seed-once at stop (remove the every-fix re-anchor)

**Decision**:
1. Gate-off branch (`startExtrapolationLoop`): remove `displayLat = NaN; displayLon = NaN`; the fields (and the marker riding them) simply hold. Extract the branch into an internal `onGateInactive()` so it is testable without the async loop.
2. `setGpsMarker` stationary branch: replace the every-fix `display = fix; viewport = anchorCenterFor(fix); requestRender()` with a **seed-once latch**: re-seed (display = fix, viewport = anchor center, one render) only on the first stationary fix after a moving episode (or before any display exists); thereafter jitter fixes update nothing (spec: "no re-frame on stationary GPS-jitter fixes"). The latch resets when the vehicle crosses the movement threshold again (a new stop episode can seed again).
3. `markerPosition()` keeps its existing fallback (raw fix when no display exists) — with the freeze it is hit only before the first display position.

- **Alternatives**
  - A: Keep the NaN reset + raw-fix fallback (status quo) — rejected: this is the stop/resume snap and a violation of the base spec's "displayed viewport SHALL remain at the last position".
  - B: Freeze but never seed (marker parks at the eased lead position ≤ v·τ ahead of the true stop) — considered; rejected in favor of phone parity: one small re-seed at the stop transition gives GPS-truth parking, and the latch guarantees it happens once, not per jitter fix.
  - C: Hide the marker on gate-off — rejected: the vehicle position is the core navigation cue on AA.

Trade-off acknowledged: the seed is a one-time ≤ v·τ backward re-frame at the stop transition (≈ 3-4 m at 50 km/h) — the same bounded lead the phone accepts; after it, the map and marker are fully static through the stop.

### D3 — Margin render-request throttle parity (500 → 200 ms)

**Decision**: `RENDER_REQUEST_INTERVAL_MS` 500 → 200 ms, matching the phone's `GPS_FOLLOW_RENDER_INTERVAL_MS`. The margin full render is still initiated per overrun-margin hit (drift beyond ±0.1·canvas — anchor-independent, verified from the `displayOffsetPx` formula), but the request fires sooner after the clamp, so the freeze-then-advance step shrinks from `v × (500 + 100 + native latency)` to `v × (200 + 100 + latency)` ms. Requests coalesce (`renderSignal` + `pendingRender` + 100 ms debounce), so no extra native renders are queued — the throttle only delays.

- **Alternatives**: A — leave 500 ms (smaller risk of request storms on slow head units). B — chosen: phone parity; the clamp event already caps the render rate, and a fast vehicle hits the margin repeatedly regardless — a shorter delay closes the gap, it does not add renders.

## Risks / Trade-offs

- [Dual position source (state vs volatile fields) drifts across the freeze] → On-gate-reopen re-sync (`reset` → next `advance` seeds from the held position); `clearOverrunBuffer` resets both; unit test pins the resume-from-frozen path.
- [Seed-once latch misses on screen switches (free-driving ↔ navigation, new renderer instance)] → Each renderer owns its latch; a new surface (`clearOverrunBuffer`) also resets it; worst case one extra seed render after a screen switch — harmless.
- [AA regression through shared `FollowDisplayState`] → `FollowPrediction` untouched; no signature change to `update()`; run `:auto:testDebugUnitTest` + `:core:testDebugUnitTest` (state class unchanged — its tests stay green).
- [Throttle drop queues a render every 200 ms while clamped on a slow head unit] → `requestRender` coalesces; the render loop's 100 ms debounce + `pendingRender` dedupe collapse bursts; verify by logcat (`clamped` frequency) on device.
- [Interaction with `fix-aa-follow-blit-anchor-mismatch` (8/9, same function)] → Blit call-site lines must stay untouched; this change edits the ease expression + gate branch around them. Coordinate task ordering: their change's remaining task is verification-only; no rebase needed if this lands next.

## Migration Plan

1. `AutoMapRenderer.kt` only (+ test file) — additive: D1 (state + re-sync) → D2 (gate-off extract + seed latch) → D3 (constant) → comment update in `FollowDisplayState.kt` (phone-only → phone + AA).
2. No data/schema/settings changes; `FollowPrediction` and the screens untouched.
3. Rollback: revert the `AutoMapRenderer.kt` edits (behavior returns to the current jumpy path); spec-visible behavior rolls back with it.
4. Verification: `:auto:testDebugUnitTest` (+ new hold/freeze/seed/resume tests), `:core:testDebugUnitTest` (unchanged), `:app:assembleMobileDebug` + `:app:assembleAutomotiveDebug -Pandroid.injected.build.abi=arm64-v8a`, on-device AA GPX replay with the existing `follow` logcat diagnostics (`fix/pred/disp/off/clamped`, L731-744): no per-fix backward `disp` delta, `clamped` stays rare, no snap at stop/resume.

## Open Questions

None — the remaining tuning (exact stop-seed threshold, device feel) is verified on device and does not change the specs or the approach.
