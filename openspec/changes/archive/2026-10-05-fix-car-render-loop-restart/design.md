# Design

## Context

See `proposal.md` — Why. The relevant current state:

- `AutoMapRenderer` (`auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt`) runs three loops on
  `carScreenScope("AutoMapRenderer", Dispatchers.Default)` — the render-signal consumer
  (`renderJob`, `:791`), the extrapolation/blit loop (`:816`) and the zoom-walk loop (`:857`) —
  all started from `init` (`:309`). Each loop body calls into native render, blit and overlay draw
  code without a per-iteration guard.
- The scope's `carFaultHandler` (`CarHostGuards.kt:98`) confines a fault at **coroutine** granularity:
  it logs and records, and the child job is over. For a loop that is the whole frame pipeline this is a
  permanent stop; the entry is `TODO.md` §51, and §92 recorded the visible shape (a blank car surface
  for ~31 s with healthy host chrome).
- `renderFrame()` already distinguishes a **stale surface** from a failure
  (`reportFailureIfCurrent`, `:1557`) and throttles surface-failure reporting (`:1580`); a dead surface
  stops the render loop deliberately (`surfaceFailed`) and the owning screen asks the host for a fresh
  surface, capped by `MAX_SURFACE_REFRESH_ATTEMPTS` (`MapScreen.kt:223-236`). That machinery is
  unchanged by this design — a *dead surface* and a *faulting loop* are different conditions.
- The renderer is created by each of the four map-owning screens (`MapScreen.kt:281`,
  `NavigationScreen.kt:365`, `FreeDrivingScreen.kt:269`, `DetailsScreen.kt:146`) and published through
  `RendererGate` (`auto/src/main/java/com/naviveylin/auto/RendererGate.kt`), which buffers
  renderer-bound state until readiness, replays it once on `publish`, and clears each slot afterwards.
- `SessionCarSurfaceHost` owns the surface; `attach(owner)` re-delivers the surface it currently holds
  to the owner (`SessionCarSurfaceHost.kt:81-95`), and `hasSurface()` reports whether one is held.

## Goals / Non-Goals

**Goals**

- One faulting frame costs one frame: the frame pipeline survives, and the fault is visible in
  diagnostics.
- A sustained fault storm recovers by re-creating the renderer, bounded, and ends in a state the driver
  can see instead of a frozen surface.
- The recovery is invisible in the map's content: viewport, follow mode, presentation, overlays and pane
  geometry survive it.
- No surface ownership change: the session keeps owning and releasing the surface.

**Non-Goals**

- No change to the dead-surface path (`surfaceFailed` + host re-delivery); it stays as it is.
- No general retry framework and no change to the phone renderer, the session scope or the host-mutation
  guards.
- Not the second §51 residual (the `remove(notice)` dismissal needing the notice still on the stack);
  it stays filed in `TODO.md` §51.
- No fault injection into a release build: the on-device verification is the non-fault regression plus
  the diagnostics path (see Verification).

## Decisions

### D1 — Per-iteration confinement inside the loops, not at the scope

Each loop body runs inside a small guard so a throwing iteration is skipped and the loop continues:

```kotlin
// AutoMapRenderer, per loop
while (isActive) {
    if (loops.supervise(RenderLoop.ZOOM_WALK)) advanceZoomWalk()   // returns false on a fault
    delay(...)
}
```

`renderJob`'s `renderSignal.collect { … }` body is guarded the same way — today a throw in
`renderFrame()` ends the collector, so *every* later `requestRender()` is dropped
(`renderSignal.value` changes but nothing consumes it).

- **Chosen**: a `RenderLoopSupervisor` (new, `:auto`, no Android dependency beyond logging seams)
  holding the consecutive-fault counters, the window, the threshold, the re-creation cap and the
  recovery/degraded callbacks; the renderer's three loops call it.
- **Alternative — restart from the fault handler**: `carFaultHandler` has no knowledge of which loop
  died and cannot re-enter the loop body; it would need a loop registry per scope, re-implementing
  supervision inside the guards file.
- **Alternative — a generic `RenderLoop` abstraction replacing the three loops**: a bigger refactor of
  the follow pipeline that is already patched twice (`TODO.md` §111); rejected as scope creep.
- **Alternative — swallow everything with inline `runCatching`**: no shared counter, no threshold, and
  three copies of the policy; a fault storm would spin forever.
- Constraint: the happy path must stay allocation-free (spec `auto-map-renderer` — *Marker drawing
  allocates no per-frame objects*), so the guard is a `try`/`catch` plus a counter increment, not a
  captured lambda per frame.

### D2 — Fault counters: aggregate per renderer, with the loop named in the record

The threshold counts consecutive confined faults **across the renderer's loops**, and the record names
which loop faulted. Rationale: the three loops share one surface, one overrun buffer and one native
client, so faults spreading across loops are the same broken renderer; a per-loop counter would let
`render:1, blit:1, zoom:1` accumulate forever.

- **Chosen values**: threshold `3` consecutive faults, window `60 s`, re-creation cap `2` per started
  screen period; a clean iteration resets the count. Constants live next to the existing render-cadence
  constants and are pinned by tests.
- **Alternative — per-loop thresholds**: a zoom-walk fault storm would never re-create the renderer,
  although the shared state (surface, native client, overrun buffer) is a plausible common cause.
- **Alternative — one fault triggers a re-creation**: every transient failure would pay a full renderer
  rebuild and a surface re-attach.
- Record throttle: the first fault of a streak is recorded immediately, further skips at most once per
  `5 s` window (the same throttle idea as `SURFACE_FAILURE_LOG_INTERVAL_MS`), so a storm cannot flood
  the 7-day diagnostics log.

### D3 — Recovery = re-create through `RendererGate`, replaying the state slots

`RendererGate` already owns the renderer's lifetime and the pre-readiness buffering, so it becomes the
recovery owner: on the supervisor's recovery callback (posted to the main thread, see Threading) it
shuts the current instance down and publishes a new one.

State survival is achieved by **making the gate's state slots replayable**: `publish` currently clears
each slot after applying it (`RendererGate.kt:154-186`), so a second `publish` would deliver a
default-state renderer. The slots are split in two:

- **state slots** (replayed on every publish): surface DPI, dark presentation, follow mode/anchor,
  pane insets + RTL, viewport (center, integer zoom, fractional magnification, rotation, walk flag),
  GPS marker with fix time, favorites, route, destination marker, overlay drawer;
- **one-shot slots** (still cleared): `reCenter`, `reengageFollow`, `resume`, `invalidateStyle`,
  `invalidateData`, `requestRender` — edge-triggered intents whose repetition would be wrong.

- **Chosen**: split the slots, keep one writer (the main thread) and replay in the existing documented
  order (design D5 of `fix-client-dpi-surface-leak`).
- **Alternative — a separate `lastApplied` snapshot object**: duplicates every field the gate already
  holds, and two structures can drift.
- **Alternative — re-push state from the screen on recovery**: couples each of the four screens to the
  recovery path and re-derives state the gate already has.
- A re-created renderer deliberately does **not** re-run the initial-viewport resolution (file I/O plus
  a native bounding-box call, `MapScreen.kt:260-272`); the replayed viewport replaces it, which also
  keeps renderer construction free of native work on the main thread (spec
  `auto-map-renderer` — *Renderer initialization off the car-app main thread*).

### D4 — Surface re-attach goes through the session, never through the renderer

After a re-creation the new renderer must draw on the surface the session already holds. The screen
re-attaches its surface owner through the existing session API:

```kotlin
// screen-side, on the recovery callback path (main thread)
guardedHostCall("attach surface (renderer re-created)") { surfaceHost.attach(surfaceOwner) }
```

`attach` re-delivers the held surface to the owner (`SessionCarSurfaceHost.kt:81-95`), which pushes it
into the gate as usual; the renderer itself neither holds, nor releases, nor adopts a surface.

- **Chosen**: re-attach through `attach` — the session's single-owner rule (spec
  `car-host-fault-isolation` — *Single-owner car surface*) is untouched, and a superseded instance can
  never be adopted because only the session's `active` surface is delivered.
- **Alternative — the gate caches the last delivered surface and calls
  `renderer.onSurfaceCreated(...)` directly**: the gate would have to second-guess whether the cached
  instance is still current, which is exactly the failure mode `reportFailureIfCurrent` exists to avoid.
- **Alternative — `invalidate()` so the host re-delivers a surface**: that is the dead-surface path; it
  asks the host for a new surface and counts against `MAX_SURFACE_REFRESH_ATTEMPTS`, which this
  condition must not consume.
- If no surface is held (`surfaceHost.hasSurface() == false`, e.g. the screen is stopped or a
  transition is in flight), the re-attach is skipped and the next delivery is drawn normally; a
  re-creation while stopped is not attempted at all (the loops only run while resumed).

### D5 — Degraded state is owned by the gate, rendered by the screen

`RendererGate` exposes `rendererState: StateFlow<RendererState>` (`LIVE`, `RECOVERING`, `DEGRADED`).
It survives instance swaps, which is where the state belongs. Each map-owning screen's existing
`rendererGate.renderer.collect { … }` block (`MapScreen.kt:216-241` and the three siblings) also
collects `rendererState`, keeps a `mapDegraded` flag in screen state and re-posts a template refresh on
change; the map/navigation/free-driving templates add a short row/text built inside the existing
`car*Template` wrappers, reading a new `R.string.map_unavailable` (`values` + `values-de`).

- **Chosen**: gate owns the state, screen renders it — mirrors `onSurfaceFailed` (renderer reports,
  screen refreshes the template on the main thread) while keeping the state alive across the instance
  swap that produces it.
- **Alternative — a renderer callback (`onRendererDegraded`)**: it dies with the instance that
  reported it, which is the one instance guaranteed to be replaced.
- **Alternative — the session's error notice (`showError`/`SessionScreenStack`)**: it takes the screen
  stack, so a degraded map would hide navigation guidance; a map that cannot draw must not take the
  guidance away.
- **Alternative — the `SafeScreen` error template**: same objection, plus it drops the map's controls.

### D6 — Diagnostics: reuse the `MAP` tag

Faults are recorded with `DiagnosticsLog.log("MAP", …)`: the loop name, the throwable class, the
streak count and the attempt count. No message text, no position, no pixels (spec `auto-diagnostics` —
*Diagnostics carry no coordinates*). Reusing the existing tag avoids a new tag and therefore an
`AGENTS.md` logging-section edit.

### D7 — Re-creation cap and re-arm

The cap (`2`) is per **started screen period**: the screen's `onStart` resets the budget and clears the
degraded state, `onStop` leaves it as it is. A fresh start is a new chance; a driver who leaves and
returns is not permanently degraded by a previous period's fault storm.

## Risks / Trade-offs

- **[A fault storm pays repeated full rebuilds]** → threshold `3` consecutive faults inside a `60 s`
  window, cap `2` per started period, and a successful iteration resets the count.
- **[Snapshot replay misses a field, so a recovery flashes the default viewport]** → the replayed set is
  exhaustive by construction (the gate's slots are the only state it ever applies); a test asserts the
  replay of every state slot and that the one-shot slots are *not* replayed.
- **[Re-attaching the surface re-triggers host traffic]** → the re-attach uses `attach`, which touches
  no host API and no `ScreenManager`; it is still routed through `guardedHostCall`, so a rejection
  degrades to a logged no-op.
- **[Two renderers alive at once (the old one not shut down)]** → recreation shuts the previous
  instance down before publishing, and the existing `RendererTestRule` fails a test when a tracked
  renderer still reports active background work.
- **[Confinement hides a real defect]** → every fault records the loop and throwable class, and a
  storm reaches a visible degraded state; a silent skip-forever is not reachable because the count
  never resets without a clean iteration.
- **[The per-frame guard costs on the hot path]** → a `try` around the iteration and one integer
  increment on the fault path only; no allocation and no lock on the happy path.
- **[Degraded state is hard to reach on a device]** → the recovery and degraded transitions are proven
  by unit tests at the supervisor and gate seams; the on-device pass verifies the non-fault regression
  and that the notice renders (see Verification).

## Threading and lifecycle

- The three loops and the supervisor live on the renderer's `carScreenScope(..., Dispatchers.Default)`
  scope; `RenderLoopSupervisor`'s counters are only touched from that scope.
- `RendererGate` and its slots stay **main-thread only** (its documented contract). The supervisor
  therefore reports the recovery request to the gate through a main-thread post (`postTemplateRefresh`,
  as `onSurfaceFailed` already does), never by calling the gate from a render thread.
- Recreation happens on the main thread: shutdown the old instance, `publish` the new one (which
  replays state and re-attaches the surface via the screen), and let the renderer's own loops start in
  its `init`.
- The screen's `onStart`/`onStop` lifecycle keeps its current meaning: `onStart` resets the recovery
  budget and attaches the surface owner; `onStop` pauses the renderer and detaches the owner. A fault
  observed while stopped cannot trigger a re-creation (the loops are paused).

## Verification

**Unit (`:auto`, Robolectric, `RendererTestRule` for teardown)**

- `RenderLoopSupervisorTest` (new, pure): fault skips the iteration and keeps the loop; the counter
  resets on a clean iteration; the threshold fires the recovery callback once; the cap stops recovery;
  the degraded callback fires once per period; a fresh period re-arms. Revert-check: with the confine
  removed, the "loop keeps running after a fault" case fails.
- `AutoMapRenderer` fault cases (extend `AutoMapRendererTest` / `AutoMapRendererRenderCadenceTest`):
  with a fault injected into the frame path, a later `requestRender()` still produces a frame (this is
  the collector-survival case, the regression that a `return@collect`-shaped guard would miss); with a
  fault injected into the extrapolation and zoom-walk ticks, the loops keep ticking.
- `RendererGateTest` (extend): a second `publish` replays every state slot and does not replay the
  one-shot intents; `rendererState` transitions `LIVE → RECOVERING → LIVE` on a successful recovery and
  `→ DEGRADED` when the cap is exhausted; the degraded state survives an instance swap.
- i18n: the new string exists in `values` and `values-de` and the German-completeness test stays green.

**On-device (AAOS AVD or head unit, `guidelines/Build.md` §10)**

- Regression drive: the follow pipeline renders as before, `adb logcat -s Diag/MAP` shows no new fault
  entries, and `adb logcat -s NaviVeylin` shows no renderer fault lines.
- The re-attach path: leave and re-enter the map screen (background round trip, screen push/pop) and
  confirm the surface is still drawn and no `HOST` rejection is recorded.
- The degraded notice: reachable only through the seam that unit tests drive; the on-device pass
  therefore checks that the notice renders when the state is set (a temporary debug-only
  `rendererState` override, removed before the change lands) and leaves a note in the change's tasks.
- Recorded as a known limitation: end-to-end fault injection on a device needs an instrumentation
  harness that this repo does not have (`fix-navigation-engine-fault-isolation` task 6.3 reached the
  same blocker).

**Guidelines**

- `guidelines/Design.md` §4: the confinement rule gets the loop carve-out (a confined fault ends the
  *iteration*; work that is a loop continues or is re-created, it is never silently abandoned).
- `guidelines/MapRendering.md` §14 (Android Auto renderer — smooth follow): the loop-liveness invariant
  and the diagnosis recipe (`Diag/MAP` fault entries, renderer re-creation attempts).

## Migration Plan

Single change, single commit in `:auto` plus the two guideline sections. No persisted state, no native
artifact, no database or manifest change, so there is nothing to migrate; both flavors and all three
ABIs build as before. Rollback is a revert of that commit: the fail-stop behaviour returns and no state
needs undoing. Ship with the next regular release (no Play-specific step beyond the usual track
uploads).

## Open Questions

- Whether the degraded state should also be visible outside the map screen (rail widget / notification)
  — deferrable: the spec only requires the car screen to show that the map is unavailable, and no
  requirement about the rail widget changes.
- Whether the diagnostics screen should show the renderer's state explicitly (it already shows the
  diagnostic entries) — deferrable, no spec impact.
