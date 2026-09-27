# Design — Fix Follow-Mode Vehicle Jumps

## Context

See `proposal.md` — Why. Follow mode currently has two independent centers of truth: the per-fix path in `MapCanvasViewModel` re-commits the follow center to the raw fix (or engine `navPos`) on every fix, while the display loop (`MapCanvasScreen` + `FollowPrediction`) extrapolates and eases a separate displayed position. The two disagree at every fix arrival (J1 sawtooth, J2 dual render-center race), the prediction base is the raw GPS fix while frames render on the engine position (J3), and the stop gate resets the offset/marker path (J4).

Current state (unchanged by this design): renderer pipeline (overrun buffer, sub-region blit, debounce), anchors (`anchorCenter`, visible-area resolution), auto-zoom, and the Android Auto follow path. `FollowPrediction` is shared core between phone and AA.

## Goals / Non-Goals

Goals:
- One follow center: the display loop's displayed position is the only follow center the renderer receives.
- Monotonic forward display: no backward correction slide at fix arrival; bounded residual lead.
- Same position source: prediction base == render source (engine position during guidance).
- Seamless stop/resume: offsets and marker frozen, never reset.

Non-Goals:
- Changing anchor presets/resolution (covered by in-flight `anchor-per-surface-visible-area`, `fix-phone-vehicle-anchor-framing`).
- Changing the Android Auto follow path (AA keeps raw-fix behavior).
- Altering GPS fix acquisition, the engine position filter, or the renderer's tile/blit internals (only their inputs change).

## Decisions

### D1 — Display position becomes the single follow center, owned in the ViewModel

**Decision**: The displayed follow position becomes a ViewModel-owned value (`@Volatile` plain property, NOT a `StateFlow` field — per-frame 60 Hz writes must not trigger recomposition). `MapCanvasScreen`'s display loop writes it every frame; `MapCanvasViewModel` reads it as the center source for every follow-mode render request (`renderFollowFrameAt` generalized to `(lat, lon, mag, angle)`). The per-fix path stops calling `prepareViewport`/`submitDebounced` with an anchor-center derived from the fix — it only updates prediction state, anchor, speed/zoom inputs, and the navigation engine feed.

- **Alternatives**
  - A: Per-fix path keeps re-centering on the raw fix; display loop only offsets (status quo) — rejected: this IS the dual-center race (J2) and the J1 sawtooth.
  - B: Screen owns the center and both paths are called from the screen — rejected: zoom/auto-zoom and anchor logic live in the VM; splitting render entry points across layers re-introduces a race.
  - C: Chosen — VM owns the single mutable display position, screen writes, VM renders. One writer (screen, per frame), one consumer (VM render paths). Auto-zoom renders (mag changes) keep the same display center, eliminating the zoom/center alternation.

**Threading/lifecycle**: display write happens on the Compose UI thread inside `withFrameNanos`; rendered on the VM's render path (main → renderer scope). `@Volatile` gives cross-thread visibility; no coroutine needed. Per guidelines/Design.md §4: the display value is transient frame state, cleared with the `MapCanvasScreen`/VM lifecycle — VM owns it so it survives recomposition, and it is NaN-initialized until the first display frame.

Note: `renderFollowFrameAt` exists at VM L2797 — it becomes the single follow render entry; per-fix zoom renders pass it too.

### D2 — Forward-only display (monotonic advance)

**Decision**: `FollowPrediction` gains a forward-only convergence rule on fix arrival: the display never moves backward. Implementation: a small pure `FollowDisplayState` class in `core` (shared, unit-testable) that owns `displayLat/Lon` and the ease loop. On fix arrival, if `eased-target < current display` (the fix line lies behind), the display holds (target = current display); the next extrapolation from the new fix advances past it. When the fix is ahead, ease forward with the existing tau=0.3 s. Residual lead bounded: ≤ v·tau while moving, held position ≤ one fix interval behind the fix line after an overshoot — no accumulation because every fix restarts the prediction base.

- **Alternatives**
  - A: Rate-limit backward easing (display may slide back but at most at vehicle speed) — rejected: more state, tuning, and a visible backward drift on curves that the hold-then-advance avoids.
  - B: Clamp the target to `max(fix, predicted)` along the path — rejected: leaves a permanent forward lead after an overshoot with no convergence mechanism.
  - C: Chosen — hold-then-advance. Simple invariant (monotonic along travel), bounded lead, no per-fix jerk.

**AA parity**: `FollowPrediction.update`/`predictedPosition` stay behavior-identical for AA (raw fix base, no display state — `FollowDisplayState` is phone-only; AA's `auto-smooth-follow` keeps its own easing). Adding the position-source parameter to `update()` is additive with a default.

### D3 — One position source

**Decision**: The VM passes the position it renders with (engine `navPos` while navigating, else raw `fix`) into the display loop's prediction update — same value the follow center would have used. `MapCanvasScreen` reads one source from `location.collect`'s fix + `_navPosition` through UI state (already available as the marker source at VM L944).

- **Alternatives**
  - A: Keep raw fix as prediction base and add a lateral correction term for the navPos offset — rejected: duplicates the engine's snap math, fragile, no testable benefit.
  - B: Chosen — same position object feeds render center and prediction. The snapped position is what the driver should see; extrapolating from it keeps the vehicle visually on the road through junction snaps (J3 dies by construction).

### D4 — Frozen stop/go (no offset/marker reset)

**Decision**: The display loop's gate (`speed ≤ 1.8 km/h` or missing fix) freezes `followDisplay` and the follow offset instead of zeroing them; `followActive` stays true for projection purposes (marker keeps projecting the frozen displayed position against the existing viewport). Resume continues from the frozen position. Remove the marker fallback to the raw-fix projection on gate-off.

- **Alternatives**
  - A: Keep reset-to-raw-fix fallback (status quo) — rejected: J4 snap at every stop/resume, worst case traffic-light cycles.
  - B: Hide the marker entirely on gate-off — rejected: vehicle position is the core navigation cue; freezing is less disruptive than hiding.
  - C: Chosen — freeze. Simplest behavior consistent with the marker "stationary when stopped" requirement.

## Risks / Trade-offs

- [Display position in VM property, 60 Hz writes] → Plain `@Volatile`, not StateFlow; no recomposition; write cost negligible. Covered by a unit test on the state class + Compose smoke test.
- [Forward-only hold leaves display ahead of the true fix after a curve overshoot] → Lead bounded by one fix interval; next fixes' extrapolation crosses it; no accumulation. Monitor with the existing `follow ... pred/disp/off` logcat diagnostics on GPX replay.
- [AA regression through shared `FollowPrediction`] → Additive parameter with default = current behavior; AA keeps raw-fix base; run the AA renderer unit/instrumented tests and an AA GPX check.
- [Interaction with in-flight anchor changes (`anchor-per-surface-visible-area`, `fix-phone-vehicle-anchor-framing`)] → This change touches only the follow CENTER source, not anchor resolution; the anchor is applied inside `followRenderTarget`/`anchorCenter` exactly as today. Coordinate task ordering in apply so anchor-visible-area lands first (it is 28/36 done) or rebase this change's center edits on top.
- [Auto-zoom per-fix renders still fire with center = display] → Correct by D1: zoom frames share the display center; verify no "wrong zoom" or center alternation frames in the zoom/center scenario test.
- [Ease tau tuned for curves no longer correct] → Hold-then-advance keeps the easing only for the fix-ahead case; verify lead/lag feel on device (GPX replay with curves + a stop).

## Migration Plan

- Deploy: single app release (phone flavor carries the follow changes; AA path untouched but ships in the same APK — guard any shared-code change behind the additive parameter).
- Rollback: revert the follow-loop changes (Screen display loop, VM center source, `FollowDisplayState`); no data, schema, or API migration — rendering/gestures unaffected.
- Verification: unit tests (`FollowPredictionTest` additions, new `FollowDisplayStateTest`) + on-device GPX replay with the existing `follow` logcat diagnostics (check per-fix `pred`/`disp`/`off` for no backward delta and no `clamped` churn), plus AA instrumented tests for the shared-core regression guard.

## Open Questions

None — remaining unknowns (exact stop threshold, ease feel) are tuned in tasks/device verification and do not change the specs or the approach.
