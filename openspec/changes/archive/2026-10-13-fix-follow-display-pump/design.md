# Design

## Context

See `proposal.md` — Why for the measured baseline and the root cause. What shapes the approach:

- The phone follow display is `MapCanvasScreen`'s frame loop (`withFrameNanos`): it re-seeds
  `FollowPrediction` on each new fix, asks it for `predictedPosition(now)`, advances
  `FollowDisplayState` toward that, and draws the frame in hand blitted by
  `FollowPrediction.displayOffsetPx(...)`, clamped to the overrun margin.
- `FollowDisplayState` and `FollowPrediction` (`:core`) are shared with `AutoMapRenderer`
  (Android Auto), so a bound in either reaches both surfaces.
- `MapCanvasViewModel` is the only requester of follow renders: `angleChanged`, a zoom commit, or
  the clamp (`renderFollowFrameAt`, 500 ms throttle); position renders are suppressed while the
  display loop is active (`MapCanvasViewModel.kt:1606`).
- Constraints: the display loop runs in the Compose frame callback on the UI thread — no native
  call and no per-frame allocation (`guidelines/Design.md` §4, `MapRendering.md` §13); the frame
  is anchor-centered and the blit offset is display-only state, measured on the *displayed*
  vehicle position (§1.1, §13).
- Measured baseline (emulator, 2026-10-12): drift 1.00 v, margin 240 px = 74 m = 5.3 s at
  50 km/h, 29-31 % of the time frozen, longest 2.70 s, a 115 px payback lurch, render latency
  0.4-2.7 s on that AVD.

## Goals / Non-Goals

**Goals:**

- Bound the displayed rate end-to-end: the position correction, the speed input, and the moment the
  frame is refreshed (specs `smooth-follow`, `auto-smooth-follow`, `gps-render-coalescing`).
- Keep the marker on the road and the frame content aligned with it — the blit offset stays inside
  the overrun margin, no uncovered strip.
- Keep every new bound host-testable (no device in the loop) and keep phone/car parity.

**Non-Goals:**

- The native render cost, the render mode, the tile path, and the overrun size (`canvasOverrun`).
- The navigation engine, its projection of fixes onto the route, and the marker's position/bearing
  source.
- Any Android component, permission, dependency or persistence change.

## Decisions

### D1 — Follower form: velocity feed-forward plus a rate-clamped position correction

`FollowDisplayState.advance` keeps its signature plus the effective speed, and its body becomes:

```
rate = vEff + clamp(kP * e, -corrMax, +corrMax)      // e = fix base - display
display += rate * dt                                 // kP = 1/tau, tau = 0.3 s
corrMax = max(CORR_FRACTION * vEff, CORR_FLOOR_M_S)  // 0.25, 1.0 m/s
```

The position error is measured against the **current fix base** (the jittery reference) while the
velocity comes from the **filtered estimate** — that split is the whole point: a backward jump of
the base moves the display by at most `corrMax`, i.e. a rate dip, never a stop and never a
reversal. Steady state is `e ≈ 0`, `rate = vEff`; a sustained 5 % speed-estimate bias needs only
`0.05 * v * tau ≈ 0.7 m` of error to null, so the residual error is bounded without an integral
term and without windup. `TELEPORT_SNAP_METERS` keeps its role for jumps beyond the ease scale, and
the frozen-stop path is unchanged (the loop is gated off below the movement threshold, so a
stationary vehicle cannot jitter).

The forward-only hold is removed, together with the `forwardComponent` helper.

**Alternatives eliminated:**

- *Keep the position ease and clamp only its correction rate.* With a pure position ease the rate
  **is** the correction — there is no feed-forward term — so a `-8 m` error drives the rate to the
  negative clamp, i.e. the display slides backwards at 0.25 v instead of dipping: a reversal of
  motion, worse than the freeze it replaces. This is why the fix cannot be a clamp on the existing
  formula.
- *Correct onto the raw fix instead of the extrapolation.* **Eliminated by the implementation's own
  measurement**: the fix is a step function between arrivals (it does not move for a whole fix
  interval), so the correction saturates backwards for most of every interval and the map runs a
  quarter slow — the host-side sweep measured 0.60 v while the vehicle drove at 1.00 v. The
  reference must be continuous, which is why the prediction publishes the extrapolated target and
  the fix enters through the committed speed.
- *Keep the hold and bound only the forward catch-up.* The hold fires whenever the base correction
  exceeds `v * tau` (4.2 m at 50 km/h) — the same scale as real GPS along-track jitter and the
  navigation engine's junction snaps — and it freezes the display for the whole correction
  (`gap / v`). The reported symptom survives.
- *Enlarge `canvasOverrun` (1.2 → 1.5).* Stretches the cycle from 240 px to 600 px (5.3 s → 13 s at
  50 km/h) at roughly 23 MB per frame buffer, and leaves the correction step and the speed step
  unbounded — the 1 Hz components of the pump are untouched.
- *Soft clamp: slow the display gradually as the margin depletes.* It is the same rate pump, merely
  smoothed, and it hides the late refresh instead of fixing it; it also cannot make the display
  advance at the vehicle's speed once the margin is exhausted.

### D2 — Refresh policy: a small stateful policy in `:core`, consumed by the display loop

```
FollowRefreshPolicy:  threshold = share * margin
  share = MAX_SHARE                                                    // latency unknown
  share = min(MAX_SHARE, max(MIN_SHARE, 1 - latency/margin - SAFETY_SHARE))   // latency known
  shouldRequest(drift) -> true once per outstanding request; onFrameLanded() arms it again
```

Constants: `MAX_SHARE` 0.55, `MIN_SHARE` 0.25, `SAFETY_SHARE` 0.10. Semantics: the threshold always
leaves at least 45 % of the margin in reserve, so a frame is in flight before the margin runs out; a
known latency that needs more reserve lowers the threshold; the floor is the point past which
requesting earlier cannot help (the clamp is the last resort, and the guideline §4 note names the
latency bound that leaves). The safety band exists because the render latency is a p95 estimate — a
frame that lands exactly on the clamp freezes the display for the frames in between. One request
outstanding at a time; the existing 500 ms throttle stays as the second guard. The clamp remains the
last resort for a render slower than the margin allows (its behaviour is unchanged), and the
already-valid offset stays applied while the frame is in flight.

**Why pure enough:** the decision is arithmetic on values the loop already holds (`displayOffsetPx`'s
result and the margin), the state is one boolean, so the whole policy is tested device-free in
`:core` and the loop stays the only writer of display-only state.

**Alternatives:** publish "a render is pending" from `MapRenderer` (a wider state surface, and the
display loop would have to cross the renderer boundary per frame); request on every fix
(contradicts the follow render throttle in `guidelines/MapRendering.md` §4, and the emulator runs
showed the clamp, not the fix cadence, is what exhausts the margin).

### D3 — Speed input: rate-limit the change of the effective speed

`FollowPrediction` replaces the `DECEL_CAP = 0.7` instantaneous step with a bounded change of the
effective speed toward the current estimate: `|vEff(t) − vEff(t−dt)| ≤ SPEED_STEP_MS2 * dt`
(nominally 2.5 m/s²). The braking protection survives — while the receiver speed falls, the
estimate itself falls and the bound keeps the prediction from running past a braking vehicle — but
no single fix can step the displayed rate by 30 %. The position-difference average keeps its role
only as the fallback when the receiver reports no speed.

**Alternative:** keep the cap and bound only the display (rejected: the target's own speed still
steps, so the display must either leave the rate band or lag the vehicle for a whole interval).

### D4 — Stop decision reads the base's own age

`FollowPrediction` records the base's refresh time with the base and refuses to declare a stop when
the base has not been refreshed within the hold window, so a stale engine position (a delayed
callback, a paused feed) cannot freeze the prediction at a standstill. The same staleness drives two
neighbouring rules: the committed speed keeps the last value (decaying to zero over
`STALE_FEED_GRACE_SEC` + `STALE_FEED_DECAY_SEC` = 3 + 7 s) so a lost feed dead-reckons instead of
stopping, and the display skips its correction and its snap while the reference is older than
`STALE_REFERENCE_SEC` (`FollowDisplayState`) so it cannot be dragged back onto a fix the vehicle has
left. `STOP_MOVE_METERS` and the hold windows are unchanged, so a genuine stop keeps today's
behaviour.

**Alternative:** lengthen `STOP_HOLD_MS` (rejected: it delays the honest stop at a traffic light,
which is a regression in the other direction, and it does nothing for the lost-feed case). The
bounds were added during implementation because the host-side test showed both failure modes: a
stale reference froze the display (the reproduction's tail), and a correction onto it reversed the
motion.

### D5 — Auto-zoom speed source per surface

`MapCanvasViewModel` feeds `SpeedZoomTable` the phone's own speed in free driving and the
navigation engine's speed while route guidance is active; the `lastValidSpeedKmH = 20.0` seed is
replaced by "unknown speed → the table's own result for an unknown input". The car keeps the engine
speed (it has no other). This is a separate defect on the same per-fix path, kept in this change
because it is one line and the same test surface.

### D6 — Threading, state and lifecycle

Unchanged ownership: `FollowPrediction` and `FollowDisplayState` stay UI-thread-confined
(`MapCanvasScreen`'s frame callback), the refresh policy is pure arithmetic on values already
published by the UI state, and nothing new is allocated per frame. `MapCanvasViewModel` keeps all
render requests. Reset points (follow exit, pan, mode switch) are unchanged, plus the outstanding
request flag is cleared on reset. Android Auto's `extrapolationTick` keeps its schedule and gains
the shared bounds by using the same two primitives; it needs no threshold because it re-anchors its
target on every fix.

## Risks / Trade-offs

- [A constant is wrong and the correction either visibly lags or visibly pumps] → the sweep test
  (below) asserts the rate band and the position error over the measured stimulus shapes; the
  constants live in one place per class so a sweep failure points at one number.
- [The phone's render latency exceeds the margin the threshold leaves] → the latency-aware form
  tightens the threshold, and the clamp stays as the last resort; the phone's latency is measured
  from the `follow` log (`|off|` at a reset ÷ (pxPerMetre × v)) in a task, so the constant can be
  revisited with data.
- [Jitter now shows as a ±25 % rate modulation instead of a freeze] → accepted deliberately: a
  modulation is not a stop, and the band is asserted rather than left to judgement.
- [The correction could push the drift past the margin and paint surface colour] → the offset clamp
  and the `MapRendering.md` §13 rules are unchanged; the band keeps the error small enough that the
  threshold is reached before the margin.
- [Car/phone divergence] → the car inherits both primitives (spec `auto-smooth-follow` — Shared
  follow bounds on the car surface); the parity case runs in the `:auto` suite.
- [The 4-frame-rate emulator cannot represent the phone's smoothness] → the acceptance numbers come
  from the host-side simulation; the emulator is a reproduction of the mechanism, not the metric.

## Migration Plan

No data, API or platform migration. Landing order: (1) the reproducing test, red on HEAD; (2) the
two `:core` primitives; (3) the display-loop refresh policy; (4) the two guards and the auto-zoom
source; (5) the guideline updates; (6) device measurement. Rollback: revert the two primitives and
the two `smooth-follow`/`auto-smooth-follow` requirement texts together — nothing else depends on
the new constants.

## Open Questions

- The phone's actual follow render latency at the threshold (the latency-aware form only tightens
  the threshold, so this can be answered after the change lands, from the measurement task's log).
