# Proposal

## Why

A fault confined inside the car renderer's own loops ends that loop **permanently**: the map freezes
with healthy host chrome, no visible error and no restart path until the driver leaves and re-enters
the screen. `TODO.md` §51 records this residual from `fix-car-host-fault-mutation-guards`
("a confined fault in the renderer's own loops ends that loop — the map would freeze instead of
killing the process, and there is no restart path for a dead render loop yet"), and the observation
class is real on the car surface: §92 already saw a ~31 s blank free-driving surface with only the
template chrome drawn.

The confinement itself is correct and must stay — the car-app library rethrows an app exception on the
main thread, which kills the process and takes the templates host down with it. What is wrong is the
unit of confinement. `guidelines/Design.md` §4 states the rule as *"a confined fault ends that piece
of work (one child of the scope's `SupervisorJob`), never a sibling"*; for a loop that owns the frame
pipeline this reads as "the frame pipeline ends". The loops must be confined **per tick**, so a bad
tick costs one frame, not the session.

## What Changes

- **Confinement per tick, not per loop.** A fault raised while easing/blitting the displayed position,
  advancing a zoom transition, or rendering a frame SHALL skip that tick, record it, and leave the
  loop alive. `AutoMapRenderer`'s three loops are the sites: the render signal consumer
  (`renderJob`), the extrapolation/blit loop and the zoom-walk loop.
- **A failed frame must not detach the render consumer.** Today a throw inside `renderFrame()` ends
  the `renderSignal.collect { … }` coroutine, so every later request is dropped and no further frame
  is rendered even though the surface is fine. The guard belongs *inside* the collected body.
- **Diagnosis without coordinates.** Each confined tick SHALL record one diagnostics entry naming the
  loop and the throwable class (never a message, never a position — spec `auto-diagnostics`,
  `AGENTS.md` logging rules), so a frozen pipeline is visible in `adb logcat -s Diag/MAP` instead of
  silent.
- **Sustained-fault policy (decided A+C, see below).** Repeated consecutive faults in one loop SHALL
  first re-create the renderer and re-attach the surface a bounded number of times, then stop retrying
  and surface a host-visible degraded state instead of spinning silently.
- **Guideline amendment.** `guidelines/Design.md` §4 gets the loop carve-out ("a confined fault ends
  the *tick*; work that is a loop continues or is restarted — it is never silently abandoned"), and
  `guidelines/MapRendering.md` §14 (Android Auto renderer — smooth follow) gains the loop-liveness
  rule plus the on-device diagnosis recipe.
- **Not touched:** the phone renderer, the native/JNI layer, the host-mutation guards, and the second
  residual of §51 (the `remove(notice)` dismissal needing the notice still on the stack) — that one
  stays filed in `TODO.md` §51 as its own candidate.

## Capabilities

### New Capabilities
- _None._ No new durable capability: this narrows how existing car-fault isolation behaves for
  per-frame work.

### Modified Capabilities
- `car-host-fault-isolation`: the *No fault escapes into the host path* requirement (its "Rendering a
  car frame fails" scenario) gains the loop-liveness contract — a fault in per-frame car work SHALL
  be confined to that tick, the loop SHALL keep running, a sustained fault SHALL degrade to a reported
  state rather than a silent freeze, and the fault SHALL be recorded once per loop occurrence.
- `auto-map-renderer`: the *Map re-renders on viewport change* and *GPS position marker on car map*
  requirements gain the post-fault guarantee — a transient fault in the renderer's frame pipeline
  SHALL NOT stop subsequent frames, blits or marker extrapolation; the displayed frame continues to be
  updated (or the degraded state of the modified `car-host-fault-isolation` requirement is shown).

## Impact

### Affected files and modules

| Path | Change |
|---|---|
| `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` | guard the tick bodies of `startRenderLoop` (`renderJob` collector, ~:791-805), `startExtrapolationLoop` (`extrapolationTick`, ~:816-844) and `startZoomWalkLoop` (`advanceDisplayedMagnification`/`advanceZoomWalk`, ~:857-874); add the per-loop fault counter/record; the `renderSignal` collector survives a failed frame |
| `auto/src/main/java/com/naviveylin/auto/CarHostGuards.kt` | the per-tick confine helper (loop name, tag, throwable class, no message) next to the existing `carFaultHandler`/`carScreenScope`; the scope-level handler stays as the backstop |
| `auto/src/main/res/values/strings.xml`, `values-de/strings.xml` | only if the sustained-fault state becomes host-visible (option A/B); German parity required in the same change |
| `auto/src/test/java/com/naviveylin/auto/AutoMapRendererRenderCadenceTest.kt`, `AutoMapRendererTest.kt` | fault-injection cases with revert-checks: a throwing tick keeps the loop alive and later frames land; a throw in `renderFrame` does not detach the render signal consumer |
| `auto/src/test/java/com/naviveylin/auto/CarHostGuardsTest.kt` | the per-tick confine records one entry with the loop name and throwable class and rethrows nothing |
| `guidelines/Design.md` §4 | loop carve-out for the confinement rule (this change supersedes that sentence) |
| `guidelines/MapRendering.md` §14 | loop-liveness invariant + diagnosis recipe (`Diag/MAP` entries, render counts) |
| `AGENTS.md` | only if a **new** diagnostics tag is introduced; preferred is reuse of the existing `MAP`/`SCREEN` tags, in which case no AGENTS edit is needed |

### Scope

Android Auto / AAOS car renderer only (`:auto` module). No phone UI, no phone renderer, no
car-screen template changes, no navigation-engine change. The phone surface keeps its current
behaviour; no phone/car parity question arises because the renderer loop is a car-only component.

### Compatibility and rollback

**Additive**, non-breaking: no public API, JNI signature, manifest entry, resource contract or
`auto/`-to-phone interface changes; the change is a confinement boundary plus diagnostics. Rollback is
a plain revert of the single `:auto` commit (plus the two guideline paragraphs), which restores
today's fail-stop behaviour without a data or state migration.

### Native impact

None. No submodule patch and no `:osmscout-client-java` override: the loops are pure Kotlin in `:auto`
and the JNI boundary (`renderInto`, blit) is called exactly as today; a confined tick only discards
its result.

### Decision: sustained-fault policy (owner, 2026-10-01) — **A + C combined**

The escalation ladder, in order; concrete `N`, window and re-creation cap belong to `design.md`:

1. **Per-tick confinement.** A fault in one tick (extrapolation/blit, zoom walk, frame render) skips
   that tick, records it once, and the loop keeps running. No recreation is triggered by a single
   fault.
2. **Bounded recovery (C).** After N consecutive confined faults in one loop within a window, the
   renderer is re-created and re-attaches to the session's surface, once per threshold, up to a capped
   number of re-creations per screen start. Each re-creation is recorded.
3. **Visible degraded state (A).** A re-created renderer that reaches the same threshold again, or a
   reached re-creation cap, stops the retry: the renderer publishes a degraded state and the car
   template shows a short "map unavailable" notice (new string, `values` + `values-de`), recorded in
   diagnostics. A fresh screen start re-arms the ladder.

Constraints the design must honour (already-binding project rules):

- Re-attachment goes through the session's surface ownership — the renderer never releases or adopts a
  surface itself (`car-host-fault-isolation` *Single-owner car surface*, `AGENTS.md`), so a
  re-created renderer attaches like a screen start does and the anti-storm bound is the threshold, not
  a per-fault re-attach.
- The degraded state is host-visible only through the guarded template path (`car*Template` wrappers /
  `guardedHostCall`), never a bare host call from the renderer.
- Diagnostics reuse the existing `MAP` tag (no new tag, so no `AGENTS.md` logging-section edit).
