# Design: Fix phone reroute route not drawn on map

## Context

See proposal.md — Why. On the phone, `confirmReroute` (NavigationViewModel.kt:469) calls `vm.setStartLocation(currentLoc)` + `vm.setDestLocation(destLoc)`. Both funnel through `clearRouteIfNeeded` (RoutePanelViewModel.kt:200), which nulls `_routeResultFlow` and bumps `_clearRouteSignal` — removing the drawn route from the map before the reroute calculation even starts. `MapCanvasViewModel.setRoutePanelViewModel` (MapCanvasViewModel.kt:1729) is the only phone path that draws a route: `routeResultFlow` collector → `mapRenderer.setRoute`, `clearRouteSignal` collector → `mapRenderer.clearRoute`. If the native reroute calc then fails, `onError` (RoutePanelViewModel.kt:267) sets `RouteState.Error` but never re-emits a `RouteResult`; the map stays route-less and the failure is silent (no `Log.e`, panel hidden during nav, phone never renders `NavigationState.errorMessage`). The car/AAOS path is structurally different and works: `AANavigationController.startNavigation` stores `routeLats`/`routeLons` in `NavigationState` (AANavigationController.kt:209-210) and its map draws from that state, so a failed recalc leaves the last route on screen.

## Goals / Non-Goals

**Goals:**
- The last drawn route stays visible through a reroute calculation; a failed recalc leaves it on screen instead of an empty map.
- Failed reroute calculations are visible on the phone (logcat + snackbar) while navigating.
- Shared `NavigationState` carries route geometry on the phone, matching the car controller.
- Panel-driven edits keep the current clear behavior.

**Non-Goals:**
- No native changes (no JNI, no submodule patch).
- No change to reroute *trigger* timing — `fast-reroute-trigger` (uncommitted change `fast-reroute-trigger`) is independent, only `confirmReroute`'s location-update call site changes.
- No AA/AAOS code changes — the car is already correct; only parity alignment of phone state.
- No changes to the car-screen error rendering (`NavigationState.errorMessage` path).

## Decisions

### D1: Don't clear the drawn route for programmatic reroute updates

**Chosen:** Add `RoutePanelViewModel.updateLocationsForReroute(start: LocationEntry, dest: LocationEntry)` that copies `startLocation`/`destLocation` into `_uiState` **without** calling `clearRouteIfNeeded`. `confirmReroute` calls this single method instead of `setStartLocation` + `setDestLocation`. On success the new `RouteResult` flows through `mapRenderer.setRoute`, which overwrites `routeLats`/`routeLons` (MapRenderer.kt:270) — the old polyline is replaced, not overlaid. On failure, `routeResultFlow` still holds the previous `RouteResult`, so the map keeps drawing the last route; `routeEntry` in `uiState` also keeps the old value, so reopening the route panel after stopping navigation shows the last route with an error state instead of an empty route.

**Alternatives considered:**
- *Keep the clear, re-emit the old `RouteResult` on error (stash-and-restore)* — rejected: requires stashing the previous `RouteResult` in `RoutePanelViewModel`, a second emission path on `onError`, and the map still blinks to empty for the duration of the calculation. Keeping the emission alive is strictly less state and zero flicker.
- *Add a `keepRouteOnMap` parameter to `setStartLocation`/`setDestLocation`* — rejected: muddies two public panel APIs for a single programmatic caller; every future caller must reason about the flag. A dedicated reroute method carries intent and keeps the panel API unchanged.
- *Clear via `clearRoute()` (full reset) after success only* — rejected: `clearRoute()` resets the entire panel state including start/dest; the reroute needs `routeState` preserved through `calculateRoute()`'s Calculating→Done cycle.

**Risk:** With the clear skipped, `clearRouteIfNeeded`'s `routeState == Done` branch no longer runs for reroute; `calculateRoute` (RoutePanelViewModel.kt:227) already sets `RouteState.Calculating` at entry, so the panel never shows a stale `Done` with mismatched start/dest for more than one frame. During navigation the panel is hidden anyway. Low risk.

### D2: Surface reroute-calc failures on the phone via snackbar + log

**Chosen:** `RoutePanelViewModel` gains a fire-and-forget event flow `routeErrorEvent: SharedFlow<String>` (`extraBufferCapacity = 1`, `tryEmit` from `onError`). `onError` also gains `Log.e(TAG, "Route calculation failed: $message")`. `MapCanvasScreen` collects the flow in a `LaunchedEffect(routePanelViewModel)`; on each message, if `navigationViewModel.state.value.isNavigating`, it calls the existing `viewModel.showSnackbar(message)` (MapCanvasViewModel.kt:2101 → `state.snackbarMessage` → existing `LaunchedEffect(state.snackbarMessage)` at MapCanvasScreen.kt:483).

**Alternatives considered:**
- *Route through `NavigationViewModel.reportError` → `NavigationState.errorMessage`* — rejected: the phone UI does not render `errorMessage` (car screen only), so this would need brand-new phone error UI; it also couples `RoutePanelViewModel` to `NavigationViewModel`. The event flow keeps the VMs independent.
- *Android `Toast`* — rejected: the app's established phone notification pattern is the map snackbar (MapCanvasScreen.kt:482-488); a toast splits the pattern.
- *Show the panel's `routeState == Error` text* — rejected: the panel is hidden during navigation, which is exactly why the error is currently invisible.

**Risk:** `SharedFlow.tryEmit` drops events when no collector is active. On the phone the collector is active for the whole `MapCanvasScreen` lifetime, and during navigation the snackbar path applies; the in-panel error text remains the fallback when the panel is open. The car screen keeps its existing `errorMessage` path. Low risk. Snackbar coalescing already prevents message storms (repeated failures overwrite the pending message).

### D3: Phone `startNavigation` stores route geometry in `NavigationState` (car parity)

**Chosen:** In `NavigationViewModel.startNavigation` (NavigationViewModel.kt:144), extend the `_state.value.copy(...)` with `routeLats = routeEntry.latitudes, routeLons = routeEntry.longitudes`, mirroring `AANavigationController.startNavigation` (AANavigationController.kt:209-210). `stopNavigation` already resets to `NavigationState()` (routeLats/routeLons default null), so stop clears them.

**Alternatives considered:**
- *Leave phone state without geometry* — rejected: the shared `NavigationState` contract (core/NavigationState.kt, "Route polyline for map rendering") would be honored on car but not phone; any shared-state consumer (future phone AA-style map, diagnostics) sees null on phone.
- *Set geometry in `NavigationViewModel` only on reroute* — rejected: inconsistent — the initial route would be missing from state.

**Risk:** The phone map still draws from `routeResultFlow`, so this is additive state, not a second draw path — no double-render risk. Note the phone's map does not currently consume `state.routeLats`; wiring that is deliberately out of scope (would create a second source of truth; `routeResultFlow` remains authoritative for the phone renderer).

## Threading & Lifecycle

- `updateLocationsForReroute` is called from `confirmReroute` on `Dispatchers.Main` (existing `onRerouteRequest` handler pattern); it performs no I/O — a plain `_uiState` copy.
- `onError` callback already marshals to `viewModelScope` (Main). `Log.e` is synchronous. `routeErrorEvent.tryEmit` is thread-safe and non-suspending (fire-and-forget; `SharedFlow` with `extraBufferCapacity = 1`).
- Snackbar collection runs in a `LaunchedEffect` on the composition's main dispatcher; reading `navigationViewModel.state.value` is a non-suspending StateFlow access.
- No new coroutine scopes or lifecycle holders. `updateLocationsForReroute` is a plain method on the existing `RoutePanelViewModel`.

## Risks / Trade-offs

- [Failed reroute leaves a *stale* route on screen] → Mitigation: this is strictly better than an empty map; the snackbar tells the user the recalc failed; the old route is still valid geometry for reaching the destination. Behavior matches the car.
- [Double error display (snackbar + panel text) when user opens panel during/after failure] → Acceptable: panel shows the last `RouteState.Error`, snackbar shows transiently; both agree on the message.
- [SharedFlow drop if collector not active] → Only affects snackbar; logcat + in-panel error remain. Collector lifetime covers `MapCanvasScreen` composition, which is the whole phone map session.
- [Regression risk to normal route panel flow] → Mitigation: D1 adds a method and changes one call site (`confirmReroute`); `setStartLocation`/`setDestLocation`/`clearRouteIfNeeded` are untouched for panel callers. Covered by unit tests below.

## Migration Plan

- Three app files change (`NavigationViewModel.kt`, `RoutePanelViewModel.kt`, `MapCanvasScreen.kt`) plus new unit tests. No data migration, no native rebuild, no ABI change. Rollback: revert the three files; behavior returns to the current silent-clear behavior.

## Verification

- **Unit tests** (`app/src/test/java/com/naviveylin/ui/route/RoutePanelViewModelRerouteTest.kt`): `updateLocationsForReroute` updates start/dest, keeps `routeResultFlow` value, does NOT bump `clearRouteSignal`, keeps `routeEntry`; `onError` (via `FakeOSMScoutClient.deliverRouteError`) emits on `routeErrorEvent` and leaves `routeResultFlow` intact.
- **Unit tests** (`app/src/test/java/com/naviveylin/navigation/NavigationViewModelTest.kt` or new file): `startNavigation` with a fake `RouteEntry` sets `state.routeLats`/`routeLons`; `stopNavigation` clears them. (Follow existing `NavigationViewModelTest` fixture pattern; must run under Robolectric per the classloader rule — see AGENTS.md "JNI stub for unit tests".)
- **Build**: `./gradlew :app:assembleMobileDebug` compiles (build-app skill); `./gradlew test` green (run-tests skill), including `RoutePanelComposeTest.kt` and `RoutePanelViewModelSearchHistoryTest.kt`.
- **On-device**: `adb logcat -s NaviVeylin` during a scripted deviation (GPX replay or mock-GPS app). Observe: (a) old route stays visible during the reroute calc; (b) on deliberate recalc failure, logcat shows `route calculation failed: <msg>` and a snackbar appears; (c) `startNavigation` populates state geometry (add a temporary `Log.d` of `_state.value.routeLats?.size` if needed).
