# Proposal

## Why

Navigation has two independent engines in one process — the phone's Activity-scoped
`NavigationViewModel` and the singleton `AANavigationController` — both driving the shared native
client and both mirroring into `NavigationStateProvider`, whose commands route to the *last*
registrant and whose state mirror picks the *first* navigating source. Ownership of "who is
navigating" is therefore implicit and order-dependent: a car deep-link destination can be routed to
either engine, an idle surface registering can claim the mirror, and the two engines' reroute
policies already disagree (100 m / 30 s tunnel guard + confirmation gate on the phone vs 25 m / 15 s +
a 10 s interval on the car), which contradicts `reroute-trigger`'s existing "Phone and Android Auto
parity" requirement. The same two engines duplicate the listener implementation, the stale-speed
ticker, the road-info throttle and the speed-spike filter.

## What Changes

- **One process-scoped navigation engine.** A single `@Singleton` engine (`:app`, implementing the
  `:core` `NavigationViewModel` interface, reachable from Android Auto through
  `AutoEntryPoint`) owns the native `NavigationController`, the `NavigationListener`, the reroute
  policy and the navigation state. `AANavigationController` is deleted.
- **`NavigationStateProvider` stops being a multi-source broker.** Its source registry, state mirror
  and per-command callback slots are removed with the second engine; the stop broadcast
  (`NavigationStopRequests`) collapses into the engine's own `stopNavigation()`. **BREAKING** for the
  `:core` seam (`NavigationStopRequests` is removed) and for `AutoEntryPoint.autoNavigationController()`.
- **Route acquisition is separated from navigation execution.** The engine exposes an acquire API and
  re-acquires on reroute with the vehicle profile it started with — self-sufficient, so a car-only
  process (AAOS, or projection with the phone UI never opened) needs no surface. The phone's
  `RoutePanelViewModel` remains the acquisition *UI* (vehicle profile, alternatives) and calls into
  the engine; it is no longer the only way a route can exist.
- **Per-surface view state stays per surface.** Follow mode, free driving (`DrivingModeProvider`),
  viewport persistence, auto-zoom, follow display, vehicle anchor presets and rendering remain
  surface-owned. The phone map's follow/mode-restore becomes a reaction to engine state instead of a
  `forceFollowMode` parameter on a start call.
- **The reroute policy is merged into the spec'd phone policy** (10 s confirmation, 50 m fast path,
  25 s cooldown, 100 m accuracy guard, 30 s tunnel guard) for both surfaces, making `reroute-trigger`'s
  parity requirement true instead of aspirational. This *changes car reroute behaviour* and needs
  on-device confirmation.
- **Errors carry their origin surface**, so a car deep-link failure is no longer displayed on the
  phone (today `NavigationState.errorMessage` is one shared field written by whichever engine acted).
- Not in scope: no native/JNI change (the JNI `NavigationController` and `NavigationListener` APIs
  stay as they are, and no libosmscout submodule patch is needed), no rendering change, no UI redesign,
  no settings or storage migration, no manifest change.

## Capabilities

### New Capabilities

- `navigation-engine`: the single process-scoped navigation engine — one native controller per
  process, surface-independent navigation state and position flow, self-sufficient route acquisition
  and reroute, error origin, and the rule that per-surface view state never moves into the engine.

### Modified Capabilities

- `navigation-controller`: the controller contract becomes "one engine per process" — start, stop,
  follow and state exposure are owned by the engine, and the requirement no longer implies a
  ViewModel-scoped controller that each surface instantiates.
- `auto-cross-device-sync`: "Car-only navigation start" goes through the engine's acquisition (not
  conditional on `RoutePanelViewModel` availability), the GPS start moves from "on `NavigationViewModel`
  init" to the engine's navigating lifecycle, and cross-surface start/stop becomes structural instead
  of broker-mediated.
- `reroute-trigger`: the policy is the single engine policy for both surfaces — the car's divergent
  thresholds and interval gate are removed, and the parity requirement gains a scenario that pins the
  single policy.

## Impact

Modules and files:

- `:app` — new engine (extracted from `app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt`
  and `app/src/main/java/com/naviveylin/navigation/AANavigationController.kt`, which is deleted);
  `app/src/main/java/com/naviveylin/navigation/NavigationStateProvider.kt` reduced or deleted;
  `app/src/main/java/com/naviveylin/ui/route/RoutePanelViewModel.kt` (acquisition caller);
  `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` (`setNavigationViewModel`, position
  flow, mode-restore), `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` (start/stop call
  sites), `app/src/main/java/com/naviveylin/service/NavigationNotificationService.kt` and
  `app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt` (observe the engine).
- `:core` — `core/src/main/java/com/naviveylin/core/NavigationState.kt` (vehicle, error origin),
  `NavigationViewModel.kt` interface, `NavigationStopRequests.kt` (removed),
  `core/src/main/java/com/naviveylin/core/AutoEntryPoint.kt`, `AutoNavigationController.kt` (removed).
- `:auto` — `auto/src/main/java/com/naviveylin/auto/NavigationSession.kt` (warmup resolves the engine,
  observation unchanged), `NavigationManagerController.kt`.
- `:app` DI — `app/src/main/java/com/naviveylin/di/AutoServiceModule.kt` (engine binding, the car
  controller provider goes away), `NavigationViewModelModule.kt`.
- Android components: `NaviVeylinCarAppService` and the car session lifecycle are unaffected in
  manifest terms; the session's warmup step that resolves the car controller now resolves the engine.
- Tests: `app/src/test/java/com/naviveylin/navigation/*` (8 files), `app/src/test/java/com/naviveylin/ui/map/MapCanvas*` (7 files),
  `auto/src/test/java/com/naviveylin/auto/*` (4 files referencing the controller), plus
  `NavigationStateProviderTest` and `SessionLogTest`'s warmup-step expectation.

Guidelines affected: `guidelines/Design.md` §3 (state ownership — one engine, per-surface view state)
and §4 (threading/lifecycle of a process-scoped scope); `guidelines/UI.md` §8 (phone/car parity of
navigation presentation, unchanged but re-checked after the merge). `guidelines/MapRendering.md` is not
affected (no render path change).

Scope: **general** — the engine serves phone, Android Auto and Android Automotive OS; no surface-only
feature.

Additive/breaking: behaviourally a **merge** (car reroute timings change, phone behaviour preserved);
**breaking** for the `:core` navigation seams (`NavigationStopRequests`, `AutoNavigationController`)
and for the `NavigationState` shape.

Rollback: revert the change as one commit — the deleted classes are restored from git and
`NavigationStateProvider`'s registry returns; no data, settings or native artefact is involved, so
there is no partial-rollback hazard.

## Decisions and Open Questions

### Decided

1. **One process-scoped engine, not an ownership lease over two engines.** The two implementations are
   already ~95 % the same listener/ticker/throttle/filter logic; an ownership field would keep two
   copies of the reroute policy and the split state mirror alive. Chosen despite the larger test
   migration.
2. **The engine is self-sufficient on reroute** (re-acquires with the vehicle profile it started with)
   rather than delegating to a surface route provider: an AAOS standalone process has no surface to
   ask, so a surface-registered provider could not be the only path; one path for both surfaces is
   simpler than two.
3. **The phone's reroute policy wins.** `reroute-trigger` already specifies 10 s confirmation,
   50 m fast path, 25 s cooldown, 100 m accuracy and a 30 s tunnel guard, and already requires phone/car
   parity; the car's 25 m / 15 s / 10 s interval was the older, simpler path.
4. **The engine holds the GPS subscription only while navigating** (see the sibling change
   `shared-resource-arbitration`, which introduces the refcounted lease the engine uses as one
   consumer).
5. **Errors get an origin surface** instead of being one shared field.
6. **No native change.** The JNI surface is unchanged — no submodule patch and no local override in
   `:osmscout-client-java`.

### Open

- Whether the phone `NavigationViewModel` disappears entirely (map screen observes the engine directly)
  or stays as a thin surface adapter holding the view-state reactions. Detail for `design.md`; it does
  not change the capabilities.
- The exact engine API shape for acquisition (one method vs profile-aware variants). Detail for
  `design.md`.
