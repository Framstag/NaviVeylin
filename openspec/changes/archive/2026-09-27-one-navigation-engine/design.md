# Design

## Context

See `proposal.md` — Why. What shapes the approach:

- The two engines are near-parallel implementations: `NavigationViewModel`
  (`app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt`, 645 lines, Activity-scoped
  `@HiltViewModel`) and `AANavigationController`
  (`app/src/main/java/com/naviveylin/navigation/AANavigationController.kt`, 490 lines, `@Singleton`).
  Both hold a JNI `NavigationController`, both implement the same `NavigationListener` callbacks
  (`processLocation`, `updateRoadInfoFromPosition`, `onPositionEstimate`, `onLaneUpdate`,
  `onNextRouteInstruction`, `onRouteInstructions`, `onArrivalEstimate`, `onCurrentSpeed`,
  `onMaxAllowedSpeed`, `onTargetReached`, `onRerouteRequest`), and both own a stale-speed ticker, a
  2000 ms road-info throttle and a speed-spike filter.
- Their **policies diverge**, so the merge is a behaviour decision, not an extraction:
  `MAX_REROUTE_ACCURACY` 100.0 m (phone) vs 25.0 m (car), `TUNNEL_REROUTE_GUARD_MS` 30 s vs 15 s,
  reroute gating `RerouteConfirmationGate` (2 confirmations / 60 s window, ≥10 s off-route, 25 s
  cooldown, 50 m fast path) vs `REROUTE_MIN_INTERVAL_MS` 10 s, speed spike 150 km/h (phone; a 250.0
  constant sits unused in the same file) vs 250 km/h.
- Route acquisition also differs: the phone acquires through
  `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt` (vehicle profile, alternatives,
  panel state) and then calls `startNavigation(routeEntry, vehicle, forceFollowMode)`
  (`MapCanvasScreen.kt:1882`, `:1987`); the car acquires inline in
  `AANavigationController.startDirectRoute`.
- `NavigationStateProvider` (`app/src/main/java/com/naviveylin/navigation/NavigationStateProvider.kt`)
  exists *only* because there are two sources: a registry, a "navigating source wins" mirror, a
  last-registrant `navigateTo`/`reportError` slot pair, and `NavigationStopRequests`
  (`core/src/main/java/com/naviveylin/core/NavigationStopRequests.kt`) as the stop broadcast.
  Consumers: `app/src/main/java/com/naviveylin/service/NavigationNotificationService.kt` (:49),
  `app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt` (:36),
  `app/src/main/java/com/naviveylin/di/NavigationViewModelModule.kt` (binds it to the `:core`
  interface `AutoEntryPoint.navigationViewModel()`).
- The phone surface consumes engine state as an observer already:
  `MapCanvasViewModel.setNavigationViewModel` (`MapCanvasViewModel.kt:2580`) collects `positionFlow`
  and `state` for follow mode, mode restore and route fit, and `MapCanvasScreen.kt:156` installs
  `setFollowModeCallback` and `setRoutePanelViewModel`.
- Car-side warmup resolves the car controller as its own step
  (`auto/src/main/java/com/naviveylin/auto/NavigationSession.kt:403-408`, step string
  "Activating navigation controller", asserted in `auto/src/test/java/com/naviveylin/auto/SessionLogTest.kt:105`).
- Constraints: `guidelines/Design.md` §3 (single owner per piece of state, view state per surface) and
  §4 (threading/lifecycle: native calls off the main thread, main-thread state publication, explicit
  scope ownership); `guidelines/UI.md` §8 (phone/car presentation parity). The sibling change
  `shared-resource-arbitration` supplies the refcounted location lease
  (`LocationService` acquire/release by consumer) that the engine uses as one consumer.
- No native change is involved: the JNI `NavigationController` / `NavigationListener` surface is
  unchanged, and no libosmscout submodule patch or `:osmscout-client-java` override is touched.

## Goals / Non-Goals

**Goals:**

- One owner for navigation state in the process, with every surface a pure observer of it.
- Route acquisition that works with a surface UI (phone panel) and without one (AAOS standalone,
  car deep link, reroute with no screen).
- A single reroute policy, so `reroute-trigger`'s parity requirement describes the implementation.
- The phone's rendering, follow, auto-zoom and mode-restore behaviour unchanged by the merge.

**Non-Goals:**

- Not unifying the renderers, the anchors, the per-surface viewports or free driving: those stay
  surface-owned (spec `navigation-engine` — Per-surface view state).
- Not moving the engine into `:core`: it needs `OSMScoutClient` and `LocationService`, and the latter
  is an `:app` singleton today.
- Not changing the notification's identity, content or foreground-service contract, and not changing
  the car templates.
- Not a route-planning redesign: the panel keeps its alternatives/profile UI; only *who executes* the
  route changes.

## Decisions

### D1 — One `@Singleton` engine in `:app`, implementing the `:core` interface

Chosen: a new `NavigationEngine` (`:app`, `@Singleton`) implements `core.NavigationViewModel`, owns the
native controller, the listener, the reroute gate, the merged state and the position flow, and is
bound in DI to the `:core` interface so `AutoEntryPoint.navigationViewModel()` returns it. The car's
`autoNavigationController()` entry point and `AANavigationController` are deleted.

Alternatives:

- *Ownership lease over the two engines* (a `PHONE|CAR|NONE` owner field in the provider): keeps both
  listener implementations, both tickers, both policies; the parity requirement would still be false
  for whichever surface does not own the session, and a handover would have to re-create the native
  controller mid-session. Rejected.
- *Engine in `:core`*: `LocationService` is an `:app` `@Singleton`
  (`app/src/main/java/com/naviveylin/location/LocationService.kt:382`) and the engine needs
  `OSMScoutClient`; placing it in `:core` would force `LocationService` (and its Play Services
  dependency) into `:core` for no benefit. Rejected.

### D2 — Route acquisition is an engine API; reroute is self-sufficient

Chosen: the engine exposes acquisition (`acquire(destination, profile)` plus the surface-driven
`start(routeEntry, vehicle)` the panel and the map screen already use) and on a confirmed reroute
re-acquires with the **retained** vehicle profile and destination, with no surface involvement. The
phone's route panel calls acquisition for the profile/alternatives UI and then starts navigation; the
car's deep-link path calls acquisition directly. This is what makes an AAOS standalone process
(the phone UI unreachable — `MainActivity` trampolines to `CarAppActivity`) work with zero surface
participation.

Alternatives:

- *Engine emits a reroute request and a surface's route provider answers*: preserves today's exact
  per-surface reroute code paths, but leaves the car-only case as a fallback branch inside the engine
  anyway (no provider registered), i.e. two paths for one rule. Rejected.
- *Panel owns acquisition only* (engine never acquires): car-only start and reroute would need their
  own acquisition code again — the duplication this change removes. Rejected.

### D3 — The phone's reroute policy is the merged policy

Chosen: one policy, the phone's numbers — `MAX_REROUTE_ACCURACY` 100 m, `TUNNEL_REROUTE_GUARD_MS` 30 s,
`RerouteConfirmationGate` (2 confirmations / 60 s, ≥10 s off-route, 25 s cooldown, 50 m fast path),
`MAX_PLAUSIBLE_SPEED_KMH` 250 (resolving the phone's unused-constant/150-inline mismatch in favour of
one constant), road-info throttle 2000 ms. The car's 25 m / 15 s / 10 s-interval path is deleted.

Consequence to verify on a device: the car becomes *less* trigger-happy on marginal deviations (a
confirmation + cooldown instead of a 10 s interval) while the 50 m fast path keeps large deviations
immediate. This is the one user-visible behaviour change of the merge.

Alternatives:

- *Car policy wins*: 25 m accuracy would ignore reroutes the phone accepts, degrading the phone —
  and the car's interval gate has no cooldown/confirmation, so it can cascade. Rejected.
- *Per-surface policy parameters*: contradicts the one-engine premise (the policy would have to be
  swapped per rendering surface) and re-introduces "two rules for one session". Rejected.

### D4 — Errors carry an origin, and surfaces filter by it

Chosen: `NavigationState` gains `errorOrigin: SurfaceOrigin` (`ENGINE`, `PHONE`, `CAR`), set when the
error is raised; a surface presents `ENGINE` and its own origin only. The engine's own failures (route
calculation, no GPS) are `ENGINE` and therefore visible everywhere — today's behaviour for the cases
that matter.

Alternatives:

- *Two separate error fields* (`phoneError`, `carError`): duplicates the state shape and forces every
  consumer to know which field to read; the notification and the car template would need both.
  Rejected.
- *Error events only, no field in the state*: the car template and the notification read state, so a
  pure event stream would need an extra per-surface store anyway. Rejected.

### D5 — `NavigationState` grows a vehicle profile; the position flow moves to the engine

Chosen: add `vehicle` to `NavigationState` (the profile the session runs with, needed by the
self-sufficient reroute and by both destinations/details views) and move the navigation position
stream (`positionFlow`, today `NavigationViewModel.positionFlow`) onto the engine. `forceFollowMode`
disappears as a *parameter*: the phone map enables follow because it observes `isNavigating`
(`map-modes` restore logic in `MapCanvasViewModel.kt:2580` already reacts to state transitions).

Alternatives:

- *Keep `forceFollowMode`*: a UI concern passed into an engine call — exactly the coupling the change
  removes. Rejected.
- *Leave the position flow on a phone-side adapter*: the car's follow path would then need a second
  source for the same positions. Rejected.

### D6 — Lifecycle: process-lifetime scope, explicit release points

Chosen: the engine owns `CoroutineScope(SupervisorJob() + Dispatchers.Main)` created once, never
cancelled (its lifetime *is* the process), mirroring today's `NavigationStateProvider` scope. Native
calls (route calculation, controller start/stop, road lookup) run on `Dispatchers.Default`; state
publication stays main-thread confined. `stopNavigation()` releases the native controller, clears the
reroute gate and stale-speed state, and releases the location lease. The engine acquires the location
lease only while navigating — it does **not** call `startLocationUpdates()` in `init` (both current
implementations do, which is one half of the asymmetry the sibling change removes).

Alternatives:

- *Activity-scoped engine* (today's phone shape): dies with the Activity, so car-only navigation
  cannot survive a phone rotation. Rejected.
- *Engine owns a location subscription for its whole lifetime*: holds GPS whenever the process lives,
  defeating the lease. Rejected.

### D7 — The phone keeps a thin surface adapter, not the engine's state ownership

Chosen: a small `@HiltViewModel` (`NavigationSurfaceViewModel` or the reduced `NavigationViewModel`)
exposes the engine's state/position flow to `MapCanvasScreen` and owns the *surface* reactions: follow
callback, route-panel wiring, and the mode snapshot/restore. `MapCanvasViewModel.setNavigationViewModel`
keeps its current contract, taking that adapter.

Alternatives:

- *Delete the phone VM entirely and inject the engine into Compose*: the map screen would need a Hilt
  entry point or a `hiltViewModel()`-provided holder anyway, so the adapter shape returns with a
  different name; keeping it also preserves the existing test seams. Rejected.
- *Move the surface reactions into `MapCanvasViewModel`*: mixes navigation-session reactions into the
  map view model, which the mode-restore code already coordinates. Rejected.

### D8 — Delete the broker, keep the `:core` interface

Chosen: `NavigationStateProvider` and `NavigationStopRequests` are removed;
`NavigationViewModelModule` binds the engine to `core.NavigationViewModel`;
`NavigationNotificationService` and `NavigationNotificationController` inject the engine (and keep
using `DrivingModeProvider` for free driving). `AutoEntryPoint.autoNavigationController()` is removed
and the car warmup keeps a step that resolves the engine (required so that a car-only process
instantiates it), with the step's log string updated.

Alternatives:

- *Keep the provider as a pass-through*: dead indirection whose whole comment block explains a
  two-source problem that no longer exists. Rejected.
- *Keep `NavigationStopRequests`*: with one engine a broadcast has exactly one subscriber; the
  notification's stop action calls the engine directly (the seam's requirements live in
  `navigation-controller` — Stop navigation — and `auto/navigation-view`, which the stop path still
  satisfies). Rejected.

## Threading and lifecycle (`guidelines/Design.md` §4)

- **Scope**: one `SupervisorJob + Dispatchers.Main` scope on the engine, created in the constructor,
  never cancelled while the process lives; no `@PreDestroy` release (the process is the owner).
- **Dispatcher split**: native calls (`calculateRouteWithProfile`, `startNavigationWithVehicle`,
  `NavigationController.stop`, road lookups) on `Dispatchers.Default`; `NavigationState` writes and
  the state/position flows on the main dispatcher, so surface collectors stay main-confined.
- **Tickers**: one stale-speed ticker (1 Hz, `Dispatchers.Default` with a real clock — the existing
  comment about not feeding the test scheduler applies) instead of two.
- **Lifecycle hooks**: the engine has no `onCleared`; `stopNavigation()` is the release point.
  The car session keeps cancelling its own scope in `onDestroy` and stopping its observations
  (`car-host-fault-isolation`); the session's state observation is unchanged because the engine is the
  observed object.
- **Location**: acquired via the lease on navigation start, released on stop/arrival; no location call
  in `init` (D6).

## Risks / Trade-offs

- [Car reroute behaviour changes (D3): a 25 m accuracy guard and a 10 s interval are replaced by a
  confirmation + cooldown, so some marginal-deviation reroutes are delayed] → the 50 m fast path keeps
  clear deviations immediate; the change carries an on-device AAOS check (deviation, tunnel, reroute
  cascade) with `adb logcat -s NaviVeylin`.
- [Test migration is broad: 8 `app/src/test/java/com/naviveylin/navigation/*` files, 4 `auto` files, and
  `NavigationStateProviderTest` becomes obsolete] → the engine takes over the listener/ticker/policy
  tests; the provider-specific tests (registry, mirror, last-registrant routing) are deleted with the
  class; the stop-path tests retarget onto the engine. Plan explicitly sequences engine tests before
  deletion of the old suites.
- [A car-only process now instantiates the engine during warmup, which resolves `LocationService` and
  `OSMScoutClient`] → both are already resolved in that path today (the car controller injects them),
  and the warmup step stays inside the existing bounded warmup
  (`auto-startup-hardening`); the engine does no work in `init` beyond allocating its scope and state.
- [`MapCanvasScreen` currently calls `startNavigation(entry, vehicle)` after the panel calculated the
  route] → the surface-driven acquisition API keeps this call path, so the map screen change is a
  parameter/receiver swap rather than a redesign.
- [Removing `NavigationStopRequests` touches the notification path pinned by
  `NavigationNotificationServiceActionTest` and `NavigationViewModelStopPathTest`] → tests are updated
  in the same change and a new test pins "stop from notification ends navigation and clears the route
  panel", keeping the requirement (route panel + route drawing cleared) covered.
- [Error-origin filtering could hide a genuine engine error on one surface] → `ENGINE` origin errors
  are visible everywhere; only surface-attributed errors are filtered, and a test pins both cases.

## Migration Plan

1. `:core`: extend `NavigationState` (vehicle, error origin); keep the `NavigationViewModel` interface;
   remove `NavigationStopRequests` and `AutoNavigationController` only when their consumers are gone.
2. `:app`: add the engine by extracting the listener/ticker/throttle/filter from `NavigationViewModel`
   and the policy from the phone path; then delete `AANavigationController`.
3. `:app`: point acquisition callers (`RoutePanelViewModel`, `MapCanvasScreen`) and observers
   (`MapCanvasViewModel`, `NavigationNotificationService`, `NavigationNotificationController`) at the
   engine; delete `NavigationStateProvider` and the DI binding to it.
4. `:auto`: warmup resolves the engine through the existing entry point; update the warmup step string
   and its `SessionLogTest` expectation.
5. Tests: engine listener/policy/reroute/error-origin/two-surface tests, retargeted stop-path tests,
   delete the provider tests; run `:app`, `:auto`, `:core` suites.
6. Docs: `guidelines/Design.md` §3 gains the engine/surface state split sentence; `guidelines/UI.md`
   §8 parity note re-checked.
7. On-device: phone navigation (start, stop, reroute, notification stop, follow), then AA/AAOS
   (car-only deep-link start, connect mid-navigation, reroute timing, session disconnect mid-navigation);
   `adb logcat -s NaviVeylin` for the engine's step/reroute lines.
8. Rollback: revert the single change commit; the deleted classes return from git. No data, settings,
   native artefact or manifest is involved, so there is no partial-rollback hazard. Ordering note: the
   sibling change `shared-resource-arbitration` (location lease) should land first or with this one —
   the engine uses the lease for D6.

## Open Questions

- Whether the engine's acquisition API should expose route alternatives to the panel (today the panel
  computes them itself via `calculateRouteWithProfile`) or only accept a destination and return a
  route. Both satisfy the specs; the narrower API (destination in, route out) keeps the panel's
  existing presentation code and can be widened later if the car ever needs alternatives.
