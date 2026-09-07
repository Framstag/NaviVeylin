# Design: Auto ETA Stop Button

## Context

See proposal.md — Why. Current state: `NavigationScreen` puts an app-owned
stop action ("x", `NavigationScreenActions.stopAction`) in the navigation map
action strip; the host ETA card shows its own stop button that does nothing.
The host delivers the ETA stop via `NavigationManager.onStopNavigation()`,
which the car-app 1.7.0 `NavigationManager` drops unless (a) the app called
`navigationStarted()` (`mIsNavigating` gate) and (b) a
`NavigationManagerCallback` is registered (callback gate). The app does
neither today. `NavigationSession` owns the navigation lifecycle: it observes
`navigationViewModel.state.isNavigating` and pushes/pops `NavigationScreen`
(see `startObserving`).

## Goals / Non-Goals

**Goals:**
- The host ETA card stop button stops navigation.
- Exactly one visible stop affordance during routing: the ETA card stop
  button. System back remains the secondary leave affordance.
- Correct `NavigationManager` lifecycle: callback registered before
  `navigationStarted()`, cleared only after `navigationEnded()`.

**Non-Goals:**
- No change to the phone UI, the non-navigation `MapScreen` full-screen
  template (keeps its own exit action), or the ETA card itself (host-rendered,
  cannot be hidden).
- No `updateTrip`/cluster/HUD integration — `navigationStarted()` is called
  for the stop-button contract only; trip updates stay out of scope.

## Decisions

### D1: Register the callback in `NavigationSession`, not `NavigationScreen`

The `isNavigating` observer in `NavigationSession.startObserving` is the single
place where navigation start/stop is known. Register the callback and call
`navigationStarted()` when `isNavigating` flips true; call `navigationEnded()`
and `clearNavigationManagerCallback()` when it flips false.

- **Alternative A (chosen)**: session-owned. The callback must be set before
  `navigationStarted()` and cleared only after `navigationEnded()`; the
  session is the only component that sees both transitions reliably (the
  screen is pushed/popped by the session and can be recreated).
- **Alternative B**: screen-owned (`NavigationScreen.init`). The screen is
  recreated on re-entry and does not observe the stop transition (it is
  popped by the session), so callback cleanup would be unreliable.
- **Alternative C**: register once in `onCreate` and never clear. Violates the
  API contract (`clearNavigationManagerCallback` exists for a reason) and
  leaves the app registered as navigating-capable when idle.

### D2: Call `navigationStarted()`/`navigationEnded()` as part of the fix

The host's `onStopNavigation()` is dropped unless `mIsNavigating` is true,
which is only set by `navigationStarted()`. Without it, even a registered
callback never fires — the ETA stop button stays dead.

- **Alternative A (chosen)**: call both, mirroring the `isNavigating` state.
  This is the documented contract for navigation apps and is required for the
  stop button to work.
- **Alternative B**: register the callback only, skip `navigationStarted()`.
  The ETA stop button remains dead (the `mIsNavigating` gate in
  `NavigationManager.onStopNavigation()` returns early).

### D3: Remove the app-owned stop action from the navigation map action strip

The navigation map action strip keeps only the route-description action; the ETA card stop button becomes the single visible stop affordance. The strip construction is extracted into a testable factory (`NavigationScreenActions.navigationMapActionStrip`). `stopAction` itself stays in `NavigationScreenActions` — `FreeDrivingScreen` (free-driving mode, no ETA card) still uses it for its own exit affordance; only the `NavigationScreen` usage is removed.

- **Alternative A (chosen)**: remove from the navigation view. Matches the user requirement (one button) and the host-standard pattern (Google Maps shows only the ETA stop).
- **Alternative B**: keep both. Two buttons for one action — the reported
  problem.
- **Alternative C**: keep the app "x" and disable the ETA one — impossible,
  the ETA stop button is host-rendered and cannot be hidden or disabled.

### D4: Session-destroy cleanup goes through `navigationEnded()` first

`clearNavigationManagerCallback()` throws `IllegalStateException` while
`mIsNavigating` is true. On `onDestroy` (session killed mid-navigation), call
`navigationEnded()` then `clearNavigationManagerCallback()`, wrapped in
`runCatching` — the session may already be torn down.

- **Alternative A (chosen)**: ordered cleanup, best-effort.
- **Alternative B**: skip cleanup on destroy. Leaks the callback registration
  into a dead session; harmless in practice but sloppy.

## Risks / Trade-offs

- [Host does not render the ETA stop button (host-specific)] → Mitigation:
  system back still stops navigation; the spec keeps back as a leave
  affordance. Accepted per user requirement (single button).
- [`navigationStarted()` marks the app as the active navigation app, enabling
  cluster/HUD trip updates] → Mitigation: correct contract for a nav app; the
  app does not call `updateTrip`, so no cluster content is sent. No visual
  change expected on hosts without a cluster.
- [`onStopNavigation()` can fire from another source (e.g., a second nav app
  takes over)] → Mitigation: same handling — `stopNavigation()` is the correct
  response in every case.
- [Callback fires on the main thread; `stopNavigation()` must be main-thread
  safe] → Mitigation: it already is — the current map-strip "x" and the back
  callback call it from the main thread.

## Migration Plan

Single commit, additive behavior fix. Rollback: revert the session callback
wiring and restore the map action strip stop action — the ETA stop button
returns to its previous (dead) state and the app "x" returns.

## Verification

- Unit tests: `NavigationSession` test — callback registered on navigation
  start, `navigationEnded()` + clear on stop, `onStopNavigation()` →
  `stopNavigation()`; `NavigationTemplateFactoryTest` — map action strip has
  one action (route-description), no stop/back; `NavigationScreenActionsTest`
  — stopAction cases removed.
- On-device: AA emulator/head unit — navigate with a travel estimate, tap the
  ETA card stop button, confirm navigation stops and the screen returns to the
  root menu; confirm the map action strip shows no "x".

## Open Questions

None — the deferrable unknowns (hosts without an ETA stop button) are covered
by the back-button fallback and do not change the specs, approach, or tasks.
