## Context

See proposal.md — Why. Current state relevant to the approach:

- Speed flows through one choke point, `LocationService.toGpsFix` (`LocationService.kt:545`), which already runs the pure `BearingFilter` before emitting `GpsFix` (speedKmH = `hasSpeed() ? speed*3.6 : NaN`). All three surfaces (follow widget, `processLocation` to native, AA display) consume the same fix.
- The provider goes silent at standstill: `MIN_DISTANCE_M = 5.0` (1 s interval) means no fixes while parked, so any fix-level logic alone cannot zero the display — silence must be handled by the consumers, which already track the last fix (timestamp on `processLocation` calls, `fix.time` in collectors).
- Native `SpeedAgent::Process` (`SpeedAgent.cpp`) prefers GPS-reported speed, clears its FIFO below 0.5 m/s in that branch only, and in the fallback branch (speed unknown) has no displacement floor — see proposal for the archived decision's acknowledged gap.
- `filterSpeed` in `NavigationViewModel` and `AANavigationController` returns `lastValidSpeedKmH` forever when the input is unknown (NaN/negative via the `-1.0` contract), freezing stale values.
- Precedent: the existing `gps-speed-priority` change is committed in the `Framstag/libosmscout` submodule HEAD (`13c158e3a`) — native edits here follow the same pattern: minimal, upstreamable commit + pointer bump.

## Goals / Non-Goals

**Goals:**
- Enforce "standing still ⇒ displayed speed 0" on all three surfaces (phone follow, phone nav, AA) with one shared, unit-testable rule set.
- Kill the two real-device mechanisms: provider-silent pin (stale last fix) and residual-velocity noise (fresh fix with small bogus value).
- Close the native fallback hole so GMS-less devices (AAOS, Huawei, sideload) cannot derive faux speed from stationary drift.
- Keep speed sources unchanged (GPS speed preferred, position-diff fallback) — only gate the extremes.

**Non-Goals:**
- No changes to how speed is *acquired* (provider choice, update interval, min distance — that's `gps-provider-selection` territory and a separate decision).
- No auto-zoom behavior change beyond what already consumes the corrected speed (zoom benefits automatically).
- No widget rendering changes — the badge already renders any value ≥ 0; correctness moves upstream so rendering stays untouched (avoiding UI.md churn).

## Decisions

### Decision 1: Single speed-sanitizing filter at the `toGpsFix` choke point
**Chosen:** Extend `LocationService` with a pure `SpeedSanity` filter — same shape as the existing `BearingFilter` — run inside `toGpsFix` so every surface inherits sanitized `fix.speedKmH`.

Rules inside `SpeedSanity.process(lat, lon, reportedSpeedKmH, time)`:
- Track the previous fix (lat/lon/time). If consecutive fixes (gap ≤ 3 s) moved < **2 m** AND reportedSpeedKmH ≤ **8 km/h** → effective speed = **0** (stationary evidence beats residual velocity — the dead-band covers the observed up-to-7 km/h residual).
- Otherwise pass the reported value through unchanged (real crawling with displacement keeps its value).

**Alternatives:**
- Sanitize per-consumer in the VMs → three copies of the rule, drift between surfaces; rejected.
- Sanitize in the native engine only → follow-mode widget and the raw fix stream stay dirty; rejected.
- Displacement-only rule without the speed dead-band → a fresh fast fix with jitter would get zeroed; the dead-band guards the threshold.

Rationale: one choke point, one test surface, inherited by all consumers including the native feed (`processLocation` gets an honest 0 instead of 7 km/h at standstill, so the engine's GPS branch reports 0 too). The 2 m displacement floor is the actual crawl guard: a genuinely moving vehicle covers > 2 m per 1 s fix once it exceeds ~7 km/h, so a dead-band of 8 km/h never masks real movement that has position evidence.

### Decision 2: Consumer-side staleness ticker for provider silence
**Chosen:** A tiny shared `SpeedStaleness` guard (pure, clock-injectable): `given(lastFixTimeMs, nowMs) → Boolean` (stale when `now - lastFixTime > 3 s`). Each state holder that displays speed runs a 1 Hz ticker (viewModelScope / controller scope, active only while the respective surface is live): on stale → `currentSpeedKmH = 0`.

- `MapCanvasViewModel` — follow-mode speed, using the `fix.time` it already receives (lastGpsTime).
- `NavigationViewModel` — nav-mode speed (`onCurrentSpeed` value) via the timestamp param of `processLocation` calls (also feeds auto-zoom: zoom settles to the 0-target instead of freezing).
- `AANavigationController` — AA display speed, from its `location.collect`.

**Alternatives:**
- LocationService emits synthetic zero-speed fixes after silence via a watchdog → pollutes the GPS-marker position stream with fake positions; rejected.
- Widget hides after staleness → spec demands a readable 0, not a hidden badge; rejected.

Rationale: the provider going silent is a *consumer-visible* event only; each surface already knows its last fix time, so the guard is a few lines per surface sharing one tested class.

### Decision 3: Native fallback gains a per-segment displacement floor
**Chosen:** In `SpeedAgent.cpp`'s fallback branch, when a new segment is pushed: if `segmentDistance < Meters(2)` (and the gap is < 10 s as today), push **zero distance** instead of the drift distance. Standstill jitter (typically 0.5–2 m per 1 s segment ≈ the observed 7 km/h) then yields 0; real movement ≥ 2 m/s (7 km/h) still computes. Minimal, behavior-isolated, upstreamable patch in the submodule; commit + bump pointer (precedent: existing `gps-speed-priority` fix).

**Alternatives:**
- Compute total FIFO displacement and zero only when below threshold → a 3 s window masks a fast start; per-segment is the honest gate (matches the spec scenario wording).
- Reject fixes below an accuracy floor instead → loses genuine low-speed movement on noisy fixes; rejected. (The existing `horizontalAccuracy < 100 m` gate already applies.)

### Decision 4: `filterSpeed` decays instead of freezing
**Chosen:** Rework `filterSpeed` (both `NavigationViewModel` and `AANavigationController`) to record `lastValidSpeedKmH` together with its timestamp, and: unknown input (NaN/negative) + `now - lastValidTime > 3 s` → return **0** (not the frozen last value). Unknown input within the window → return the last known good (spike-filter semantics unchanged for the transient case). Defaults when nothing ever valid received stay as today (20 km/h auto-zoom default unchanged).

**Alternatives:**
- Keep freezing (status quo) → the pinned 7 km/h the user saw; rejected.
- Return 0 immediately on any unknown → transient gaps (tunnel reacquire) flash 0 → 60 repeatedly; the decay window keeps transient behavior while curing the standing freeze.

## Risks / Trade-offs

- [Dead-band hides genuine stationary-crawl that moves < 2 m/fix] → Accepted: such a crawl reads 0 briefly (bounded to the fix cadence — the next fix with > 2 m displacement restores it). This is the price of snapping residual velocities up to 7 km/h at standstill; real movement beyond ~2 m/fix is never masked.
- [NETWORK/PASSIVE fixes on GMS-less devices refresh lastFixTime while parked, defeating staleness, and network position jitter can exceed the displacement threshold] → The dead-band still requires reported speed < 2.5 km/h, and the native per-segment floor catches the engine-side value; full fix (provider-side) for this edge is deferred — see Open Questions.
- [Native submodule patch must stay upstreamable] → Keep it minimal and isolated to the fallback branch, matching existing style; CI gate (`Check libosmscout Android-free`) untouched since no Android logging is added.
- [Ticker adds tiny UI-thread wakeups] → 1 Hz, active only while a speed surface is live; negligible.

## Migration Plan

1. Kotlin: add `SpeedSanity` + `SpeedStaleness` classes (+ tests); wire `toGpsFix`, the three state holders, `filterSpeed` decay.
2. Native: patch `SpeedAgent.cpp` fallback segment floor; commit in submodule (minimal, upstreamable); bump submodule pointer.
3. Build + run unit suites (`./gradlew test`), instrumented pass on emulator with GPX replay including a stop.
4. Real-device verification: park, watch widget drop to 0 within ~3 s; drive, watch normal values; logcat `LocationService` + `NavigationVM` confirm which mechanism dominated.
5. Rollback: revert Kotlin edits (additive, no API change) and re-pin the previous submodule ref; prior APKs unaffected.

## Open Questions

- Exact threshold tuning (staleness 3 s, dead-band 8 km/h, displacement 2 m) validated against live logcat during apply — numbers are starting points, spec wording tolerates ±.
- Whether a fresh fix with `hasSpeed() = false` should also reset the displacement tracker (today it can, since position still updates); answer deferred — the native floor covers the fallback value regardless.
- GMS-less standalone behavior for the NETWORK/PASSIVE jitter edge: decide after real-device data whether `SpeedSanity` needs a source-aware variant.
