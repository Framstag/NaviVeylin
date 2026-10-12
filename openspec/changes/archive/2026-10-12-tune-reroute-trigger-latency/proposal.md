# Proposal

## Why

The reroute trigger feels late, most visibly on the car surface: after leaving the route the vehicle
covers several hundred metres before a reroute starts. The latency is the sum of two stacked,
independently owned gates:

- the native `RouteStateAgent` holds an `OffRoute` state for a fixed **5 s** before it emits a
  `RerouteRequestMessage` (`app/src/main/cpp/libosmscout/libosmscout/src/osmscout/navigation/RouteStateAgent.cpp:84`);
- the app's `RerouteConfirmationGate` then waits a full **10 s** from the first reported request before
  it confirms (`app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt:1068`,
  `MIN_OFF_ROUTE_DURATION_MS`).

Measured worst case ≈ 15 s, i.e. ≈ 420 m at 100 km/h. The distance fast path (50 m) only applies to the
**first** report of an episode (`RerouteConfirmationGate.needsDeviation()`), so a deviation that grows
after that report still waits the whole 10 s. The current `specs/reroute-trigger` text ("within 10
seconds of the first off-route detection") is unreachable given the native hold: the implementation
needs 15 s.

Evidence: `app/src/main/java/com/naviveylin/navigation/RerouteConfirmationGate.kt` (gate),
`NavigationEngine.kt:1065-1071` (constants and wiring), `RouteStateAgent.cpp:76-86` (native hold),
`RouteStateAgent.cpp:84` cited in `NavigationEngine.kt:194`.

## What Changes

- Lower the engine's off-route confirmation floor `MIN_OFF_ROUTE_DURATION_MS` **10 000 → 4 000 ms**, so
  the end-to-end latency (native 5 s hold + confirmation floor) is ≈ 9 s and inside the 10 s bound.
- Lower the distance fast path `FAST_PATH_DISTANCE_M` **50 → 30 m**, so a clear deviation confirms on
  the first report it appears in.
- Keep unchanged, deliberately: `MIN_REROUTE_CONFIRM_COUNT` 2, `REROUTE_CONFIRM_WINDOW_MS` 60 s,
  `REROUTE_COOLDOWN_MS` 25 s (cascade guard), `MAX_REROUTE_ACCURACY` 100 m and
  `TUNNEL_REROUTE_GUARD_MS` 30 s (noise guards).
- Add one coordinate-free diagnostics line on reroute confirmation (`REROUTE` tag:
  `offroute_ms`, `confirm_ms`, `deviation_m`) so the end-to-end latency becomes measurable on device
  instead of assumed. It reuses the already-recorded `lastOnRouteTime`
  (`NavigationEngine.kt:820`), which is currently write-only.
- **No native change.** The submodule's 5 s hold and the 20 m `snapDistanceInMeters`
  (`libosmscout/include/osmscout/navigation/PositionAgent.h`, the OnRoute/OffRoute boundary) stay as
  they are; lowering the 20 m snap distance would trade lateness for false off-route states.

Not a breaking change. Rollback: restore the two constants (and drop the diagnostics line).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `reroute-trigger`: the off-route confirmation floor drops from 10 s to 4 s; the distance fast path
  drops from 50 m to 30 m; the latency requirement and its scenarios are restated so they are
  reachable in the presence of the native engine's 5 s off-route hold.

## Impact

Affected code:

- `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt` — the two constants in the
  companion object (`:1068`, `:1070`), the confirmation path that emits the diagnostics line, and the
  `lastOnRouteTime` read.
- `app/src/test/java/com/naviveylin/navigation/RerouteConfirmationGateTest.kt` — the 10 s expectations.
- `app/src/test/java/com/naviveylin/navigation/NavigationEngineRerouteTest.kt` — the marginal-deviation
  case and the gate-boundary cases.

Affected specification and guideline:

- `openspec/specs/reroute-trigger/spec.md` — requirement and scenario numbers.
- `guidelines/Design.md` §3 (D3 reroute policy numbers, if the section states the floor).

Not affected: the native submodule and its pin, `:auto` / car templates, `:core`, map rendering. The
single process-scoped engine applies the one policy to every surface, so no per-surface change and no
car-side change is needed (spec `reroute-trigger` — Phone and Android Auto parity).

Out-of-scope finding to file rather than fix here (`TODO.md`): the distance fast path is evaluated only
on the first report of an episode, so a deviation that grows afterwards cannot shortcut the
confirmation. Fixing that means changing the fast-path contract and its spec scenario, which is a
separate decision from lowering the thresholds.
