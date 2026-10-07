# Design

## Context

See `proposal.md` — Why. What shapes the approach:

```
Native engine (background thread)
  RouteStateAgent.cpp:103    TargetReachedMessage on every fix inside 30 m of the
                             target while PositionState::OnRoute  (not one-shot)
  OSMScoutClient.cpp:2908    JNI CallVoidMethod onTargetReached(bearing, distance)

App
  NavigationEngine.kt:864    the listener's onTargetReached is a no-op  <-- the only drop site
  NavigationEngine.kt:672    stopNavigation(): native stop, release nav lease, state reset
  NavigationEngine.kt:294    startInternal(): the single start path, used by BOTH a new
                             navigation and the engine's own reroute
                             (keepRerouteCooldown == fromReroute at every call site)
  core/NavigationState.kt    the one shared state struct; core/NavigationViewModel is the
                             only engine seam a surface has (state / stopNavigation)
  NavigationSession.kt:268   car session onDestroy; presence goes false at :283-285,
                             controller.onDestroy() at :289-291, scope.cancel() at :299
  NavigationSession.kt:642   isNavigating observer -> start/end host navigation + screens,
                             deferred while the session is stopped (SessionHostGate)
```

Constraints that decide the shape: `NavigationListener` callbacks arrive on the native thread and
every state write is marshalled to the main dispatcher (`guidelines/Design.md` §4); the car session
is destroyed exactly when the driver leaves the car, so the fact must outlive the session; the
`:auto` host rules (`AGENTS.md`) confine every lifecycle step and forbid bare host mutations.

## Goals / Non-Goals

**Goals:**

- The arrival fact is part of navigation, not of a surface: readable after the car session is gone,
  and reusable by the phone surface later without a second mechanism.
- The car exit goes through the one existing stop path, so every already-specified consequence
  (host navigation session ends, nav view leaves, notification removed, lease released, mode
  restored) holds without a second implementation.
- The exit is confined: no fault escapes the session lifecycle callback and no host mutation is
  attempted during teardown.

**Non-Goals:**

- No native, JNI or `libosmscout` submodule change — `onTargetReached` is already delivered.
- No change to the reroute path: reaching the destination does not suppress a later reroute
  (owner decision, 2026-10-07). The arrival fact is retained across a reroute instead.
- No new setting (unconditional), no phone-side end rule, no free-driving hand-off, no UI change
  while the session is live.
- No new component, DI binding or persistence: the fact is in-memory, like the rest of the
  navigation state.

## Decisions

### D1 — The arrival fact lives in the shared `NavigationState`

`NavigationState` gains `hasReachedDestination: Boolean = false`.

*Alternatives:* (a) a private engine field plus a new member on the `core.NavigationViewModel`
interface — a second stream to observe and to keep in step with the state; (b) a separate
`StateFlow<Boolean>` on the engine — same problem plus a lifetime to own; (c) put the fact in the car
session — impossible, the session is the thing that ends.

*Why:* the fact is a property of the navigation session, and the shared state is the one contract
every surface already reads (spec `navigation-engine` — One navigation state shared by all
surfaces). It is coordinate-free, needs no persistence, and the car session can read it
synchronously (`.value`) at the moment it is destroyed. Costs one field and one `data class` equality
change, which is also what conflates repeated reports (see D6).

### D2 — The end decision stays in the car session, as a pure predicate plus one file-local seam

`NavigationSession.onDestroy` ends navigation through two small file-local helpers beside the
existing ones (`shouldRestoreFreeDriving`, `NavigationSession.kt:1192`): the predicate
`shouldEndNavigationOnSessionEnd(state)` — `state.isNavigating && state.hasReachedDestination` — and
the executor `endNavigationAfterArrival(state, stop)`, which calls `stop` only when the predicate
holds and reports whether it ended navigation.

*Alternatives:* (a) a shared `NavigationEndPolicy` component observing
(`hasReachedDestination`, `CarSessionPresence.active`) with its own scope, started by the car session
— reusable for the phone step, but adds a component, a scope and a lifetime whose failure mode is a
leaked collector, for a rule that is one conjunction today; (b) the engine injecting
`CarSessionPresence` and stopping itself — the engine is surface-less by design and would have to
distinguish "car left" from "phone backgrounded", which is view state the engine must not own
(spec `navigation-engine` — Per-surface view state never moves into the engine); (c) an observer on
`CarSessionPresence.active` in the car session — reads a value the session writes two lines earlier
in the same method, i.e. indirection without an independent source; (d) only the predicate, with the
call to `stopNavigation()` inline in `onDestroy` — the call itself would then be reachable only on a
device, because the session needs a host `CarContext` and cannot be constructed in Robolectric.

*Why:* the session that ends is the only component that knows the session ended, and the rule needs
nothing else — but the *call* is what has the consequence (the session stops navigation), so it gets
its own seam that a unit test drives with a fake `stop` lambda. The pair is named after the rule
(`…OnSessionEnd`), not the car, so the phone step can move it to `:core` and reuse it when a second
caller appears.

### D3 — The trigger is session end (`onDestroy`), not visibility loss

Owner decision (2026-10-07). `onStop` only means "not visible" — the driver may have switched to the
radio app and can come back — while `onDestroy` is the session ending, the same moment
`CarSessionPresence` goes false.

*Alternatives:* arm on `onStop` and decide at `onDestroy` (earlier decision point, more bookkeeping,
no different visible result since both live in the same teardown); decide on `onStop` (ends guidance
on a brief in-car app switch).

### D4 — The exit runs inside `onDestroy`, ordered after the host navigation teardown

The call site sits after `carSessionPresence().setActive(false)` and after
`navigationManagerController?.onDestroy()`, and before `sessionDestroyed = true`, `stopObserving()`
and `scope.cancel()`; it is wrapped in the same `guardedHostCall(...)` fault confinement the other
teardown steps use.

*Alternatives:* stop navigation before the controller teardown — the session's `isNavigating`
observer is still alive and `publishTrip` would still consider the controller navigating, so the
host could receive a trip built from the reset state; stop it after `scope.cancel()` — the engine
write is independent of that scope, but leaving it inside the confined block keeps the teardown
readable.

*Why:* `stopNavigation()` is main-thread safe (it is already called from the host's stop path and
from the phone UI) and performs no host call. The observer reaction it provokes is deferred by
`SessionHostGate` because the session is stopped, so no screen push/pop is attempted during
teardown — the reason no `armScreenPush`-style seam is needed here.

### D5 — Arrival survives a reroute by being preserved in `startInternal`

`startInternal` is both the fresh-start and the reroute path, so it sets
`hasReachedDestination = fromReroute && current.hasReachedDestination`. Today's
`keepRerouteCooldown` parameter carries exactly that meaning at every call site
(`start()` passes false, `calculateAndStart` passes `fromReroute`); the design makes it explicit
(a named parameter or a derived local) rather than relying on `copy` semantics.

*Alternatives:* (a) let `_state.update { it.copy(...) }` preserve the field implicitly — a fresh
navigation started while another session runs would then inherit a stale arrival; (b) clear the fact
and let the next fix re-set it — arrival is only reported inside the 30 m circle, so a reroute that
reaches the same destination would leave the fact false exactly when it matters; (c) suppress the
reroute after arrival — rejected by the owner (D7).

### D6 — The listener guards the write

`onTargetReached` sets the fact on the main dispatcher, only while `isNavigating` is true and only
when it is not already set.

*Alternatives:* write unconditionally — the native message repeats on every fix inside the circle, so
this would post a main-thread write per fix; rely on `MutableStateFlow` equality conflation alone —
correct but still a dispatch per fix; skip the `isNavigating` guard — a late report of a session that
was already stopped would publish an arrival on a stopped state (spec scenario "Late arrival report
after a stop").

### D7 — No reroute suppression, no setting (owner decisions)

Recorded so the rejected options stay visible: suppressing the reroute after arrival would remove
the "turn around, N m to destination" guidance a driver gets while looking for parking, at the cost
of a guard in the reroute path; it is out of scope here. A car setting was rejected in favour of the
unconditional behaviour; the delta spec therefore has no disabled-case scenario.

### D8 — Diagnostics seam

One `SESSION`-tagged line at the decision (`arrival reached=true/false → navigation ended/kept`,
with the remaining distance in metres when known), placed with the other session lines so the
on-device recipe in `guidelines/Build.md` §10 keeps one stream per concern. No coordinates, no new
tag; the value shape follows the existing diagnostics entries.

## Risks / Trade-offs

- [The host may not destroy the session promptly when the driver walks away, so the phone keeps
  guiding for a while] → the exit is late, never wrong; measure the gap on device with the
  `SESSION` decision line against the session-destroy line and record the observed delay.
- [A process kill instead of a session destroy] → nothing to end: all navigation state is in memory,
  so no persisted state can claim navigation is running (spec `navigation-ongoing-notification` — "No
  lying notification after a kill").
- [A drive-by within 30 m already counts as arrival, and with reroute unsuppressed the driver can be
  guided back afterwards] → accepted semantics: the fact means "the destination was reached once for
  this session". Documented in the spec, not silently assumed.
- [One engine per process: the car exit also ends phone guidance and removes the notification] →
  intended and stated in the proposal; the phone-side rule for a session that never reaches the car
  is a separate change.
- [The arrival fact leaking into a later navigation] → `startInternal` clears it except on a reroute
  (D5), with a unit case per path.
- [A reroute running while the session ends: `isNavigating` is true and the fact is true, so the exit
  also cancels the reroute attempt] → correct: `stopNavigation()` already ends a running calculation
  attempt; the unit case covers a stop during an in-flight reroute.
- [Working-tree coordination] → `NavigationEngine.kt`, `NavigationEngineStopPathTest.kt` and
  `openspec/specs/navigation-engine/spec.md` currently carry uncommitted edits; land them before this
  change is applied to avoid a spec/engine collision.

## Migration Plan

No data or state migration: the new field is in-memory and defaults to false, and nothing is
persisted. Deploy is an ordinary app update; a rollback is a revert of the change (the behaviour
returns to "navigation ends only when the driver stops it"). No ordering constraint with the map
data or the native build — the change compiles against the already-shipped JNI.

## Open Questions

- Whether the phone surface should get its own end rule on the same fact (e.g. app stopped after
  arrival) — a later change; it does not alter this design's seams.
- Whether the car should hand the ended session over to free driving rather than the map root — can
  be decided when a driver asks for it; it does not change the arrival fact or the exit.
