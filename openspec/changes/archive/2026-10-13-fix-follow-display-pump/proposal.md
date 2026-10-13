# Proposal

## Why

The phone follow map does not scroll at a bounded rate: at a steady 50 km/h it repeatedly stops
dead and then lurches forward. Measured on `emulator-5554` with a **clean** injected track
(1 Hz, exactly 50.0 km/h, no GPS noise, `adb emu geo fix … 27.0`), 60 s per run:

| run | stimulus | frozen frame intervals | longest freeze | net scroll |
|---|---|---|---|---|
| A | clean, constant 50 km/h | 75 / 257 (29 %) | 2.70 s | 2550 px |
| B | + ±3 m along-track jitter per fix | 87 / 285 (31 %) | 2.47 s | 2548 px |
| C | speed alternating 50/37 km/h | 70 / 225 (31 %) | 2.23 s | 2482 px |

The stalls are **stimulus-independent** — injected jitter and a speed dip do not change them — so
the dominant stall is structural, not a response to GPS noise. The drift (`off`, px) grows at
45 px/s = exactly 1.00 v (3.06-3.24 px/m at mag 16), saturates at the overrun margin
(240 px = 74 m = 5.3 s at 50 km/h), the drawn content is pinned there for the whole render
(byte-identical frames, correlation 1.00) and the new frame then pays the stall back as a
**115 px lurch in one frame**.

**Root cause.** No component on the phone follow display path bounds the rate of the displayed
motion, and the frame refresh is triggered only *after* the overrun margin is already exhausted:

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt:1606` — in a steady-heading drive
  the only follow render trigger is `shouldRender = (positionChanged && !displayActive) ||
  angleChanged`: position renders are suppressed while the display loop runs, and a constant heading
  never sets `angleChanged`.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt:775-781` — the follow re-render is
  requested when `offset.clamped` is already true (500 ms throttle), i.e. after the margin is gone;
  a full render is then on the critical path of the freeze.
- `core/src/main/java/com/naviveylin/core/FollowDisplayState.kt` (`advance`, the forward-only hold)
  — a backward correction freezes the display outright instead of correcting at a bounded rate.
- `core/src/main/java/com/naviveylin/core/FollowPrediction.kt` (`effectiveSpeed`, `DECEL_CAP = 0.7`)
  — a >5 % receiver-speed dip steps the prediction speed down by 30 % for one whole fix interval;
  the ease then closes that gap at up to `gap/tau` (measured in run C: `eff` 37 → 23-28 km/h with
  `dec=true`, followed by a catch-up at 1.5 v = 67 px/s).

**Evidence (repro)** — scratch dir `.pi/logs/follow-pump/` (untracked):

```bash
bash .pi/logs/follow-pump/run.sh clean A 60        # record + inject the clean 50 km/h track
python3 .pi/logs/follow-pump/shift.py A.mp4        # -> intervals=257 stalls=75 (29%), longest stall=2.70s
adb logcat -s MapCanvasScreen | grep 'follow '      # off=0,0,-240,0 clamped=true, reset to -38..-65 px
```

A host-side test that is red on HEAD does not exist yet: the defect is currently only proven
against the emulator reproduction above. The change's first task writes that test (a fix-stream
simulation over the measured stimulus shapes, asserting a rate band) and records its red-on-HEAD
run before the fix lands.

**Loop eligibility — stated, not chosen silently.** This change carries one contract defect but
three contributing mechanisms (refresh trigger, correction step, speed step) and two guards. The
freeze half has a device reproduction; the correction and cap halves are proven as *mechanisms*
(code + run C numbers) but their phone magnitude is not measured. Per
`.pi/skills/fix-loop` eligibility (one root cause → one fix) this is therefore **not eligible as a
single loop item**, and it is not being routed through that loop: it is a reviewed change whose
first task establishes the red case host-side.

## What Changes

- **Bound the rate of the displayed motion end-to-end** (one fix, three parts):
  - the follow correction becomes a bounded-rate correction in both directions — a backward
    correction moves the display backward at a limited rate instead of freezing it, and the forward
    correction may not exceed a bounded multiple of the vehicle speed (today up to ~4 v);
  - the follow frame is refreshed on a **drift threshold** (about 55 % of the overrun margin;
    latency-aware variant: `margin − v · pxPerMetre · p95RenderLatency`, floored at 25 %), not only
    once the offset is already clamped, so a frame is in flight while margin remains;
  - the prediction speed is **rate-limited** per update instead of capped to 70 % in one step (the
    braking protection stays; the 30 % step disappears).
- **Two guards on the same path**: `isStopped` may not be decided from a base older than the
  current fix (today a stale base declares a false stop and freezes the prediction at
  `FollowPrediction.kt`, `isStopped`/`lastMoveTimeMs`); the free-drive auto-zoom takes the phone's
  own speed, not `NavigationViewModel.state.currentSpeedKmH` (undefined outside navigation while
  `MapCanvasViewModel.kt:554` seeds `lastValidSpeedKmH = 20.0`, pinning the free-drive zoom target
  to the 20 km/h value — at 90+ km/h the free-drive map stays ~4× too close).
- **BREAKING (spec-level only).** `openspec/specs/smooth-follow/spec.md` — "Correction easing" and
  `openspec/specs/auto-smooth-follow/spec.md` — "Correction easing" currently *mandate* the
  forward-only hold ("SHALL NOT move backward at fix arrival"; "SHALL hold at its current
  position"). That rule is what produces the freeze; it is replaced by a bounded backward rate.
  No app API, manifest, resource, permission or persisted format changes. **Rollback path:** the
  two requirement texts plus the two `:core` primitives (`FollowPrediction`, `FollowDisplayState`)
  revert together; no data migration, no native rebuild.
- **Scope: phone and car.** `FollowPrediction` and `FollowDisplayState` are shared `:core`
  primitives also used by `core`-side AA follow (`AutoMapRenderer.extrapolationTick`), so the
  correction and speed bounds reach Android Auto too — parity is required, and the AA case is
  covered by the same primitive tests plus the existing car follow test. The *refresh trigger* half
  is phone-only: the car re-anchors its render target on every fix (`setViewport` +
  `reengageFollow`), so its blit is already refreshed per fix.
- **Not in scope:** the tile/full-render cost and the render mode, the free-driving mode semantics,
  the overrun size (`canvasOverrun`), the route/navigation engine, and anything native.
- **No native or JNI change** — the libosmscout submodule, `:osmscout-client-java` and the CMake
  inputs are untouched; the whole delta is Kotlin in `:core` and `:app`.

### Decision (2026-10-12, owner)

Allowing a bounded backward correction **reverses a rule that was added deliberately** (the
monotonic-forward rule was introduced to remove a per-fix "moved forward, jumped backward"
sawtooth). Decided: **the bounded backward rate** — a correction never exceeds ~0.2 v backward, so
the residual slide is at most a few metres spread over a second — bounded, unlike the sawtooth the
hard hold replaced, and it removes the freeze.

Rejected: *keep the hold and bound only the forward catch-up* — no rule reversed, but a fix whose
target falls behind still freezes the map for that interval, i.e. the reported symptom survives.
The spec delta therefore rewrites the two "Correction easing" requirements (the BREAKING note
above) instead of adding a bound around the hold.

## Capabilities

### New Capabilities

None. The change bounds behaviour that existing capabilities already own.

### Modified Capabilities

- `smooth-follow`: "Correction easing" (MODIFIED) — the forward-only hold is replaced by a bounded
  correction rate; "Follow frame refresh before the overrun margin is exhausted" (ADDED) — the
  drift threshold becomes the refresh trigger, so the clamp is no longer it; "Stop decision
  against a current base" (ADDED) — the stop decision may not be taken from a base older than the
  current fix.
- `auto-smooth-follow`: "Correction easing" (MODIFIED) — parity with the shared
  `FollowDisplayState`/`FollowPrediction` bounds; "Shared follow bounds on the car surface"
  (ADDED) — the car inherits both bounds and needs no threshold of its own.
- `gps-render-coalescing`: "Follow-mode render throttling" (MODIFIED) — the drift-threshold refresh
  joins the trigger set.
- `auto-speed-zoom`: "Auto-zoom speed source per surface" (ADDED) — each surface feeds the
  speed-to-magnification table a speed it knows, and an unknown speed is not replaced by a seed.

## Impact

- `core/src/main/java/com/naviveylin/core/FollowDisplayState.kt` — bounded correction rate.
- `core/src/main/java/com/naviveylin/core/FollowPrediction.kt` — rate-limited effective speed; the
  `isStopped` age guard.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — the display loop's refresh trigger
  (drift threshold in addition to `offset.clamped`).
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — the free-drive auto-zoom speed
  source (line 1644); the follow render trigger at line 1606 is read, not changed, unless the
  threshold needs a VM-side publication.
- Tests: `core/src/test/java/com/naviveylin/core/FollowPredictionTest.kt`,
  `FollowDisplayStateTest.kt` (new cases), plus a new fix-stream simulation test that sweeps the
  measured stimulus shapes (0.5/1/2 Hz, ±1/±3/±8 m jitter, 5/15/30 % speed steps, render latencies
  0.1/0.5/3 s) and asserts the rate band. `auto/` tests run unchanged as the parity check.
- Guidelines to update in the same change: `guidelines/MapRendering.md` §4 "Render Coalescing /
  Throttling", §7 "Bearing Smoothing (location layer)" (the deceleration cap), §11 "Auto-Zoom"
  (the free-drive speed source), §13 "Overrun Display Window (pan and follow)" (the clamp and the
  refresh trigger), and "Known Pitfalls" (the new regression to re-check). `guidelines/UI.md` is
  not affected (no UI text or control changes).
- No Android component change: no manifest, no resource, no permission, no dependency, no Gradle
  configuration. No native/JNI change.
