# Design

## Context

See `proposal.md` — Why for the latency budget. What the design has to work with:

- The reroute policy is one app-owned state machine, `RerouteConfirmationGate`
  (`app/src/main/java/com/naviveylin/navigation/RerouteConfirmationGate.kt`), constructed once in
  `NavigationEngine` (`:195-201`) from constants at `NavigationEngine.kt:1065-1071`. It is pure Kotlin
  and takes an injected clock, so its behaviour is unit-testable without a device.
- Upstream of the gate, the native `RouteStateAgent` holds `OffRoute` for 5 s before it emits
  `RerouteRequestMessage`, then emits on every later fix (`RouteStateAgent.cpp:76-86`). The app cannot
  observe the state-transition instant today: it only sees `onPositionEstimate` and
  `onRerouteRequest`. The engine does record `lastOnRouteTime` on every `OnRoute` estimate
  (`NavigationEngine.kt:820`) but never reads it.
- The engine's confirmation path runs on `Dispatchers.Main` (state publication, `guidelines/Design.md`
  §4); the deviation computation runs on the injected compute dispatcher
  (`dispatchers.compute`, `NavigationEngine.kt:944`). Diagnostics go through
  `com.naviveylin.core.DiagnosticsLog`, which mirrors to logcat on the calling thread and owns the
  file on a daemon worker — safe from the main thread and reachable on a head unit without adb.

## Goals / Non-Goals

**Goals:**

- Bring the end-to-end reroute latency (off-route state beginning to reroute start) inside the 10 s
  the `reroute-trigger` spec already promises, using only app-owned code.
- Make the confirmed deviation latch on the first report that shows a clear deviation.
- Make the latency measurable on device, with numbers and no coordinates.

**Non-Goals:**

- No change to the native submodule, its pin, or the 20 m `snapDistanceInMeters` OnPath/OffRoute
  boundary.
- No change to the distance fast path being evaluated only on the first report of an episode.
- No change to the cooldown, the GPS-accuracy guard, or the tunnel/no-signal guard.
- No new UI, no new setting, no per-surface knob.

## Decisions

### Decision 1: Remove the latency in the app gate, not in native

The three ways to shorten the chain, and why the first is chosen:

- **(a) Lower the app's confirmation floor (`MIN_OFF_ROUTE_DURATION_MS`) — chosen.** The gate is
  app-owned, pure Kotlin, and unit-tested; the blast radius is two constants and the tests that pin
  them. The floor is the one delay the app fully controls.
- **(b) Lower the native 5 s hold in `RouteStateAgent.cpp`.** Rejected for this change: it is a
  vendored upstream file, so it means a submodule patch on the `naviveylin-local` branch, a submodule
  pin bump and a 3-ABI native rebuild — the "necessary" work the user asked to avoid — and the 5 s
  hold is the native layer's own stability filter. It stays available as a follow-up if the measured
  latency is still too high.
- **(c) Anchor the episode clock at the `OffRoute` position estimate** so the existing 10 s floor
  counts from detection and the spec bound is met without changing the floor. Rejected here: it turns
  a two-constant tuning into a state-machine change plus a listener change, and it loads semantics
  onto `onPositionEstimate` that the gate was deliberately kept out of. Recorded as a follow-up.

Risk assessment: (a) is reversible by restoring the constants; (b) and (c) are not, or not cheaply.

### Decision 2: Floor 4 s, fast path 30 m

- **Floor 4000 ms over 5000 ms.** 5 s + 5 s = exactly 10 s would leave no margin for report jitter
  between the native hold and the first app-visible request; 4 s gives ≈ 9 s end-to-end, inside the
  bound with one second of headroom. It stays far above a single GPS fix, and the two-report minimum
  plus the 60 s window still reject a lone noisy report.
- **Fast path 30 m over keeping 50 m or dropping to 20 m.** 20 m is the native snap distance — the
  same threshold that decides OnRoute/OffRoute — so a 20 m fast path would confirm on the very
  boundary that defines off-route and make the "marginal deviation" band empty. 30 m keeps a
  meaningful 20–30 m band and roughly halves the distance a clear deviation must travel before it can
  latch. Keeping 50 m was rejected because with the 4 s floor the fast path no longer carries the
  latency budget on its own, but it still shortens the clear cases.
- Deliberately unchanged: `MIN_REROUTE_CONFIRM_COUNT` 2, `REROUTE_CONFIRM_WINDOW_MS` 60 s,
  `REROUTE_COOLDOWN_MS` 25 s, `MAX_REROUTE_ACCURACY` 100 m, `TUNNEL_REROUTE_GUARD_MS` 30 s. Lowering
  the cooldown to 15 s and the tunnel guard were both considered and rejected: they buy nothing for
  the first trigger and each raises the false-positive or cascade risk the guards exist to stop.

### Decision 3: Measure through `DiagnosticsLog`, not `Log.d`

- **Chosen: one `DiagnosticsLog.log(REROUTE_TAG, …)` entry on confirmation**, carrying
  `offroute_ms` (now − `lastOnRouteTime`, the already-recorded last OnRoute instant),
  `confirm_ms` (the floor actually elapsed) and `deviation_m`. It reaches the on-device
  `filesDir/diagnostics/app.log`, which the car can share without adb, and it satisfies the
  coordinate rule (numbers only) that `checkNoCoordinatesInLogs` enforces.
- **Alternative: extend the existing `Log.d(TAG, "onRerouteRequest: rerouting vehicle=…")` line.**
  Rejected: `Log.d` on a head unit needs adb, and the report came from the car surface.
- **Alternative: surface the latency in the UI.** Rejected as out of scope — the requirement is a
  latency, not a display.

Threading and lifecycle: no new component. The entry is written from the existing confirmation
callback on `Dispatchers.Main`; `DiagnosticsLog` never touches the filesystem on the caller and
returns immediately. The deviation stays computed on `dispatchers.compute`. `lastOnRouteTime` is
already reset in `resetGuards` (`NavigationEngine.kt:790`), so no lifecycle change is needed; a
session that never reported `OnRoute` must be guarded against `lastOnRouteTime == 0`.

### Decision 5: The gate's clock is the engine's injected time source

Found during apply, after task 1.3 tried to move the clock at the engine level and could not:
`NavigationEngine` built the gate without a `now`, so `RerouteConfirmationGate` kept its
`System::currentTimeMillis` default and its floor, window and cooldown were decided on the wall
clock while every other engine timing decision reads `EngineTimeSource` (`NavigationEngine.kt:100`).
That contradicts the seam's own contract ("A component SHALL NOT read the system clock at the site
of a timing decision").

- **Chosen: pass `now = timeSource::nowMillis` to the gate.** One line, production-neutral
  (`EngineTimeSource.System` is the JVM wall clock), and it makes the 4 s floor verifiable at engine
  level instead of only in the pure gate test.
- **Alternative: leave the gate on the wall clock and verify the floor only in
  `RerouteConfirmationGateTest`.** Rejected: it leaves a component on a system clock the project
  forbids, and it would have narrowed task 1.3's specified verification.
- **Alternative: wait on the wall clock in the engine test.** Rejected: refused by
  `checkNoWallClockWaits`, and it is exactly the test shape the seam exists to avoid.

### Decision 4: The two tests that pin the numbers move with them

`RerouteConfirmationGateTest` asserts the 10 s/5 s/50 m behaviour by advancing a fake clock, and
`NavigationEngineRerouteTest.marginalDeviationNeedsTheConfirmationGate` asserts the engine-level
boundary. They are verification, not architecture, but lowering the constants without updating them
leaves them red by construction. The new cases assert the 4 s floor and the 30 m boundary for both
the gate and the engine-level paths; one case per modified spec scenario.

## Risks / Trade-offs

- **[More reroutes than before]** → the 4 s floor still needs continuous off-route reporting, the
  cooldown stays 25 s, the accuracy guard stays 100 m, the tunnel guard stays 30 s, and the fast path
  still needs 30 m of deviation. If on-device evidence shows cascades, the environment variable is the
  floor, not the guards.
- **[A transient GPS artefact near a junction now confirms]** → both a duration floor (4 s) and a
  two-report minimum must be met; a single noisy fix cannot confirm, and the fast path additionally
  needs 30 m.
- **[The 10 s spec bound silently depends on the native 5 s hold]** → a future native change would
  break it unnoticed. Mitigation: the new diagnostics line measures the end-to-end latency on device,
  and the dependency is stated in the spec requirement text.
- **[The default constructor call in `RerouteConfirmationGate` still lists the old default]** → a
  reader may take the class defaults for the policy. Mitigation: `NavigationEngine` passes every value
  explicitly, and the class KDoc names the defaults; the tests that change pin the policy, not the
  class defaults.
- **[Diagnostics line cost]** → one entry per confirmed reroute, bounded by the cooldown, on a
  buffer that drops rather than blocks.

## Verification

- Unit: `app/src/test/java/com/naviveylin/navigation/RerouteConfirmationGateTest.kt` (floor, fast
  path, window, cooldown, reset) and
  `app/src/test/java/com/naviveylin/navigation/NavigationEngineRerouteTest.kt` (fast path, marginal
  boundary, cooldown, guards, parity of the two surfaces through one engine). Run the `:app` unit
  suite for the `com.naviveylin.navigation` package, then one both-flavor gate before completion.
- On device: `adb logcat -s NaviVeylin | grep REROUTE` for the diagnostics line, and the same line from
  `filesDir/diagnostics/app.log` on the car. The measurement to record is `offroute_ms` and
  `confirm_ms` for a real deviation; the 10 s end-to-end bound cannot be produced by a stationary
  emulator, so a GPX replay or a drive is required and the task must say so rather than imply device
  proof.

## Migration Plan

No migration. The constants ship compiled; there is no persisted state, no format change and no API
change. Rollback is restoring `MIN_OFF_ROUTE_DURATION_MS` and `FAST_PATH_DISTANCE_M` (and removing the
diagnostics entry) — a revert of this change, not a data migration.

## Open Questions

- Should the distance fast path be re-evaluated on every report of an episode instead of only the
  first? Deferred: it changes the fast-path contract and its spec scenario, and the 4 s floor already
  covers the growing-deviation case within the bound.
- Should the native 5 s hold drop to 3 s as well? Deferred until the diagnostics line reports what the
  new numbers actually produce on a drive.
