# Design: Animated auto-zoom entry on the car surfaces

## Context

See `proposal.md` — Why. Prerequisite and sequencing: `aa-follow-framing-and-zoom-parity` (27/27, awaiting archive) introduced the display-frame magnification easing (P3) and its delta spec for `auto-speed-zoom` is not yet synced; this design builds on that mechanism and does not touch the requirement text that change rewrites.

```
CURRENT ENTRY (free driving, measured on the AAOS emulator)
  browse map displayed mag 13.000
  -> FreeDrivingScreen.onGpsFix -> autoZoomTarget(...) -> AutoZoomController.onSpeed(speed)
       committedTarget == NaN            (AutoZoomController.kt:35-36 -> :76-78)
       -> stepped = target = 17.000      (no convergence, one step)
  -> commit: rendererGate.setViewport(..., zoom = 17.000)   (FreeDrivingScreen.kt:457-469)
  -> native render at 17.000, one frame later the surface shows 16x the area

WHAT THE RENDERER CAN AND CANNOT ABSORB (AutoMapRenderer.kt)
  ZOOM_BLIT_LIMIT   = 0.25 mag  (:1759)   largest scale the overrun blit serves
  ZOOM_EASE_TAU_SEC = 0.25 s   (:1762)    display-side easing time constant
  OVERRUN_FACTOR    ~ 1.2                 overrun margin 108/60 px
  :899  |target - frame| > ZOOM_BLIT_LIMIT -> full native render, NO easing
  => a 4-level entry (13 -> 17) is 16x area: unreachable from the frame in hand,
     and every commit larger than 0.25 mag lands hard. The transition must therefore
     be walked in <= 0.25 mag steps, each served by a render + an eased blit.
```

The phone solves the same problem outside the geometry: Compose scales the composited front buffer, so `smooth-zoom` queues the native render and animates the scale meanwhile (`openspec/specs/smooth-zoom/spec.md` — "the debounced native render SHALL be queued while the animation plays"). The car renderer has no scaling layer; its analogue of "queued render + animated displayed scale" is a sequence of blit-serviceable commits.

## Goals / Non-Goals

**Goals**

- An entry into a follow surface never changes the displayed magnification by more than the blit cap in one frame — regardless of how far the speed target is from what is on screen.
- The transition starts from what the driver can currently see, not from a placeholder, and ends on an exact native render at the speed-appropriate target.
- Reuse the existing easing/cap machinery; no new component, no new lock, no allocation in the draw path, no phone behaviour change.

**Non-Goals**

- The follow render target, the heading deadband, the fractional-magnification bookkeeping (owned by `aa-follow-framing-and-zoom-parity`).
- The speed→magnification table, the 0.5/update convergence cap for *speed* changes, and the epsilon deadband (unchanged for the steady-state path).
- The car zoom gesture and manual-zoom suspension semantics (`auto/map-interaction`).
- Any phone, settings, persistence, JNI or native change.

## Decisions

### D1 — The transition starts from the renderer's displayed magnification (chosen)

The renderer owns the walk, and its starting point is the magnification of the frame it currently displays. No controller state is involved.

- **Alt A (chosen): the renderer walks its committed magnification from the displayed one.** `displayMag`/`overrunMag` already carry what the driver sees; a request farther than the blit window becomes a walk target, and the first step starts from the committed value, which equals the displayed value at entry.
- **Alt B: seed the controller with the displayed magnification — rejected during implementation.** The renderer walk is render-paced (~1.3-3 s, ~16 renders for a 4-level entry); a seeded controller converges at the *fix* cadence (≤0.5/update, gain 0.3), which paces the walk off the fixes instead: 8 fixes x 2 steps ≈ 8 s for the same 16 renders — no visual gain, and it violates the transition's ~4 s bound. It is also dead state: the walk starts from the displayed magnification either way. Each car screen additionally owns its own `RendererGate`/`AutoMapRenderer`/`AutoZoomController` (`FreeDrivingScreen.kt:172,194`), so no "browse magnification" is carried into a controller to begin with.
- **Alt C: scale the composited frame instead of walking renders (the phone's mechanism).** Rejected: the car renderer has no scaling layer and the overrun geometry cannot express a 16x area change (Context).

Risk: the walk starts from a *display* value, so an uninitialised/stale display magnification needs a rule — `displayMag = 0.0` (no frame yet) is the cold-start case and is excluded from the walk (D4).

### D2 — The walk runs on its own tick, render-synchronously (chosen)

- **Alt A (chosen): a dedicated loop in the renderer**, gated on a usable surface (`resumed`, not failed, surface present) — deliberately *not* on movement — advancing one step per **landed** render. Two constraints force this shape: `extrapolationGateActive` (`AutoMapRenderer.kt:758-760`) requires `lastFixSpeedMs > MOVEMENT_SPEED_MS` (0.5 m/s) and the extrapolation tick is only reached from that loop (`:744`), so a transition started while parked (entering free driving at standstill) would never advance and the map would keep the old magnification until the vehicle moves — worse than the cut being fixed. Second, the walk must also ease `displayMag` on the same tick while the extrapolation loop is gated off, otherwise the frames land at the new magnification while the displayed scale is left behind. Render-synchronous stepping (a step is committed only once the previous step's frame has landed, `overrunMag` caught up with the committed value) keeps the committed value at most one step ahead of the frame, so the display never exceeds the blit window and never snaps.
- **Alt B: on the extrapolation tick (as first designed).** Rejected — movement-gated (above); a parked entry would stall.
- **Alt C: fix-cadence convergence.** Rejected — same 16 renders spread over ~8 s (D1 Alt B evidence), and it would couple the transition to the GPS cadence.
- **Alt D: a compositor/Canvas scale layer for the car surface.** Rejected — a new rendering path for one transition, and the car surface is drawn into the host surface.

Risk per Alt A: an extra loop in the renderer (one more coroutine on the existing `scope`). Mitigation: it is cheap when idle (a pending-target check and a NaN-guarded easing), shares the renderer's existing lifecycle flags, and takes no new lock.

### D3 — Keep the overrun factor and the blit cap; measure the burst before changing geometry (chosen)

- **Alt A (chosen): `OVERRUN_FACTOR` 1.2 and `ZOOM_BLIT_LIMIT` 0.25 stay.** A 4-level entry costs ~16 renders over the entry; measured render time (25-300 ms) puts the transition in the ~1.5-4 s range. The cost is paid once per entry, on a surface that is switching context anyway.
- **Alt B: raise the overrun factor to ~1.45** (cap ~0.5, 8 renders for the same entry). Rejected as the default because the overrun size is paid by *every* native render for the whole session (the buffer is rendered at overrun size), not just during the entry — a permanent pixel cost for a one-off transition.
- **Alt C: temporarily larger cap during the entry.** Rejected — the overrun margin is a property of the frame in hand, not of the transition; a larger cap would expose empty edges.

Risk: if device measurement shows the entry burst competing with the follow loop (dropped ticks, transition visibly stalling), Alt B becomes the data-driven fallback; the decision is therefore recorded as a measurement task, not as a constant.

### D4 — The jump survives as the target commit; the walk bounds what a frame lands

- **Alt A (chosen): the controller's first commit still jumps to the speed target**, and that request is what the walk aims at. The jump is only visible as a cut when no frame is displayed (cold start, `displayMag = 0.0`) — there the request lands directly, which is exactly what the spec's "jumps directly to the target instead of smoothing from the default map zoom" describes.
- **Alt B: seed or narrow the controller** (D1 Alt B). Rejected.
- **Alt C: never jump.** Rejected — a cold start from the default map zoom (5) would ease for ~12 s at the blit cap.

### D5 — The re-enable path applies the target immediately, from the last known speed

- **Alt A (chosen):** when `autoZoomEnabled` flips to true, the screen applies the auto-zoom target computed from its last known speed (`currentSpeedKmH`, already tracked per fix, `FreeDrivingScreen.kt`) instead of waiting for the next fix; the transition then walks. This satisfies the spec's "begins immediately" without giving the controller any display state.
- **Alt B: wait for the next fix (~1 s).** Rejected — contradicts the scenario wording.
- **Alt C: no transition when the re-enabled delta is inside the blit window.** Adopted as a refinement rather than an alternative: small deltas (the navigation view's routing-sensible 15.0 start) keep the current single-eased-blit path and need no extra render.

Consequence: the spec's "Auto-zoom re-enabled" scenario wording ("immediately adjusts") has to be read as "the transition begins immediately, the target is reached across the transition" — flagged in `proposal.md` Open Questions 1 for confirmation, since it is a wording decision on an existing requirement.

**Extension 2026-09-19 (section 7, from the verification report):** "the speed-appropriate level" now also exists before any speed was reported. `AutoZoomController` is seeded with `DEFAULT_SPEED_KMH = 20.0` (the spec's "Speed unknown" default; the phone seeds its filter the same way, `MapCanvasViewModel.lastValidSpeedKmH`), and the shared gate in `FreeDrivingScreen.autoZoomTarget` passes an unknown speed through instead of rejecting it — so the re-enable path's "no usable speed yet" case applies the 16.0 level rather than doing nothing, and the first position estimate already yields a reasonable initial zoom (which the entry transition then walks to, D1/D2). The seed is confined to the TARGET computation: `AutoFixDerivation` keeps returning "unknown", because that value feeds the follow extrapolation and the movement gate (task 7.2).

## Threading / Lifecycle

No new component; `guidelines/Design.md` §4 conventions are unchanged.

- The walk mutation happens on the renderer's existing `CoroutineScope(SupervisorJob() + Dispatchers.Default)`, in a dedicated loop next to the render and extrapolation loops (D2). It touches only `@Volatile` fields the renderer already owns across threads (`viewportZoomFraction`, `viewportZoom`, `overrunMag`, `displayMag`) and requests renders through the existing `requestRender()`. No new lock, no lock-scope change (`surfaceLock` usage untouched).
- The screens' commit stays on the car host's main thread, next to the existing `autoZoomTarget(...)` call; it now carries a transition-eligible flag through `RendererGate`.
- Lifecycle: the new loop shares the renderer's guards (`isShutdown`, `paused`, `surfaceFailed`, `surface == null`) and never requests a render without a live surface; a pause/surface loss simply stops the walk, which resumes (or lands the target when the gate permits) without assuming a fix arrives.

## Risks / Trade-offs

- **Render burst during the transition** → bounded by the cap-derived step count; measured on device; `OVERRUN_FACTOR` raise held in reserve (D3).
- **A transition started while parked** → the dedicated tick is deliberately not movement-gated (D2), and it eases the displayed magnification itself while the extrapolation loop is closed, so the displayed scale cannot be left behind.
- **Uninitialised display magnification** (`displayMag = 0.0`, no frame yet) → excluded from the walk; the request lands directly, which is the spec's cold-start case (D4).
- **Mid-transition re-target** → the controller's own convergence can request a value on the far side of the walk's current value (display at 15.5 walking toward 17.0, then a descending request of 16.5). The walk takes the newest request and is monotonic per leg, never passing the requested value, ending exactly on the last requested one. The spec scenario is worded that way; an earlier draft ("SHALL NOT overshoot past 15.0") was unachievable while `auto-speed-zoom`'s own convergence produces intermediate requests above the eventual target.
- **Easing softness at large steps** → each step is ≤ the blit cap, the same bound the existing transition was verified against on device; the final frame is a native render at the exact target (no accumulated resampling).
- **Observable-contract drift vs the phone** → the spec states the observable behaviour (animated, anchored, ends on an exact render), not the mechanism; `guidelines/UI.md` records that the mechanisms differ (Compose scale vs render walk).

## Verification

Unit (host JVM, `:auto`):

- `AutoMapRendererTest`: a far transition-eligible commit becomes a walk (the frame does not land the whole difference); each step is ≤ the blit cap; steps are render-synchronous (no step is committed before the previous frame landed); the walk ends on a render at the exact requested magnification; a mid-walk re-target is monotonic and never passes the requested value; a delta inside the blit window stays on the single-eased-blit path; a walk completes while the vehicle is stationary (the extrapolation gate is closed); no render is requested after the surface is gone.
- `FreeDrivingScreenTest` / `NavigationScreenTest`: the auto-zoom commit is transition-eligible; the re-enable path applies the target from the last known speed without waiting for a fix; the commit cadence is otherwise unchanged.
- `MapPanHandlerTest`: the shared commit gate is unchanged.
- `AutoZoomControllerTest`: unchanged (the controller is untouched by this change; its existing cases are the regression guard).

On device (AAOS AVD; method recorded in `TODO.md` §29 — do not re-derive):

- Browse → free driving: read the magnification sequence from the existing `FreeDrivingScreen: autoZoom commit speed=… mag=…` lines and the follow diagnostic (` dMag=`, ` last=blit|render`); assert no single-frame change above the blit cap and an exact landing on the target.
- Render accounting: `adb logcat -d -t 2000 | grep -c 'lock OK'` around the entry — the transition's render count against the D3 budget.
- Marker/target anchoring through the transition (screen recording), navigation-start entry, and re-enable (`Alt`/settings) entry.
- Regression: the render rate on a turning stretch stays below ~1.0 per fix (the `aa-follow-framing-and-zoom-parity` 7.1 measurement).

## Migration Plan

No data, settings or API migration. Implementation order, each step independently revertable:

1. The renderer walk + its own tick, plus the transition-eligible flag on the auto-zoom commit path (both car screens, `RendererGate`).
2. The re-enable fast path (last known speed) — small, independent.
3. D3 measurement on device; raise `OVERRUN_FACTOR` only if the burst proves costly.
4. Guidelines (`MapRendering.md` §1.1, `UI.md` parity note).

Rollback: revert the `:auto` edits; the entry returns to the current single-frame jump and the spec delta reverts with the change.

## Open Questions

- Transition length is derived from the cap-derived step count, not a separate constant; whether that duration needs a tuning constant is answerable from the on-device recording without changing specs, the approach or the task breakdown.
