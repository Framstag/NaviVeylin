## Context

See proposal.md — Why. Current state: `RoutePanelViewModel` exposes `routeResultFlow` (the drawn route geometry) and `clearRouteSignal` (bump → `MapCanvasViewModel` calls `mapRenderer.clearRoute()`). The map draw path is a single collector in `MapCanvasViewModel.setRoutePanelViewModel` (L1840-1854): `routeResultFlow.collect { result -> if (result != null) mapRenderer.setRoute(...) }`. Stopping navigation (`MapCanvasScreen` L1543, L1642, L1758, L1774) calls `navigationViewModel.stopNavigation()` + `setNavigating(false)` but never touches the route. Android Auto is already correct: `AANavigationController.stopNavigation()` resets `NavigationState`, and `NavigationScreen.startObserving` clears the AA route when `routeLats`/`routeLons` become null.

## Goals / Non-Goals

**Goals:**
- Stop navigation → route polyline + markers removed from the phone map.
- Route panel state (start, dest, vehicle, summary, steps, `routeResultFlow`) fully preserved after stop — user can restart without recalculating.
- Restarting navigation (any start path) redraws the route.
- The route must NOT reappear via StateFlow re-collection after stop (composition re-entry, nav away/back).

**Non-Goals:**
- Change the panel "Clear" button semantics (full reset stays).
- Change `clearRouteIfNeeded` (user edits start/dest → full clear stays).
- Change Android Auto behavior (already correct).
- Change reroute behavior (route stays visible during reroute; `routeVisible` is already true while navigating).

## Decisions

### D1: `routeVisible` flag + `combine` in the map draw path (not "null the result flow")

Two candidate mechanisms for "keep route data, hide from map":

- **(a) Null `_routeResultFlow` on stop** — rejected. Loses the geometry; restart would need to re-emit it, and `StateFlow` dedupes equal values so re-emission needs a copy or a counter. Also breaks the "route still available" semantic at the data level.
- **(b) `routeVisible: StateFlow<Boolean>` + `combine(routeResultFlow, routeVisible)` in the draw collector** — chosen. `clearRouteFromMap()` sets `visible = false` + bumps `clearRouteSignal` (map clears via the existing signal path); `showRouteOnMap()` sets `visible = true`, which makes `combine` re-emit and redraw. The flag survives composition re-entry (it lives in the activity-scoped `RoutePanelViewModel`), so the re-collection trap is closed: after stop, `combine` emits `(result, false)` → no draw.

```kotlin
// RoutePanelViewModel
private val _routeVisible = MutableStateFlow(true)
val routeVisible: StateFlow<Boolean> = _routeVisible.asStateFlow()

fun clearRouteFromMap() {
    _routeVisible.value = false
    _clearRouteSignal.value = _clearRouteSignal.value + 1
}

fun showRouteOnMap() {
    _routeVisible.value = true
}

// MapCanvasViewModel.setRoutePanelViewModel
viewModelScope.launch {
    combine(vm.routeResultFlow, vm.routeVisible) { result, visible -> result to visible }
        .collect { (result, visible) ->
            if (result != null && visible) {
                mapRenderer?.setRoute(result.routeLats, result.routeLons,
                    result.startLat, result.startLon, result.destLat, result.destLon)
                routeLats = result.routeLats
                routeLons = result.routeLons
                renderMap()
            }
        }
}
```

The `clearRouteSignal` collector stays unchanged.

### D2: `showRouteOnMap()` in `NavigationViewModel.startNavigation()` (single redraw hook)

All three start paths (route panel button, summary dialog button, reroute auto-start) funnel through `NavigationViewModel.startNavigation()` (L129). Adding `routePanelViewModel?.showRouteOnMap()` there covers every restart with one change instead of touching two screen call sites. `NavigationViewModel` already holds the panel VM reference (`setRoutePanelViewModel`, L111) for reroute, so no new coupling. The reroute path is a no-op in practice (`routeVisible` is already true while navigating) but is harmless and future-proof.

### D3: `onSuccess` and `clearRoute()` reset `routeVisible = true`

- `onSuccess` (L269-283): set `_routeVisible.value = true` before emitting the new `RouteResult`. Without this, a user who stops (visible=false), edits start/dest, and recalculates would get a calculated route that never draws.
- `clearRoute()` (L332, panel "Clear" button): reset `_routeVisible.value = true` — it is a full reset to the default state.
- `clearRouteIfNeeded()` (L227): left unchanged — it nulls `routeResultFlow` anyway, and the next `onSuccess` resets the flag.

### D4: Stop call sites — one line each, plus the L1642 hygiene fix

All four stop sites in `MapCanvasScreen.kt` add `routePanelViewModel.clearRouteFromMap()`:

| Line | Site | Also fixes |
|------|------|-----------|
| 1543 | route panel stop | — |
| 1642 | summary dialog stop | adds missing `setNavigating(false)` (currently write-only field, but the call sites are inconsistent) |
| 1758 | nav status bar stop | — |
| 1774 | nav details overlay stop | — |

## Risks / Trade-offs

- **Route reappears on restart but user expected it hidden** → intended behavior: restarting navigation must show the guidance line; spec scenario "Restarting navigation redraws the route" pins it.
- **`combine` re-emits on every `routeVisible` flip** → cheap: one boolean flip per stop/start, draw path gated on the flag; no per-frame cost (collectors only run on state change).
- **`routeVisible` drift** (e.g., a future path sets it false without clearing the map) → the flag is only written by the three methods in D1/D3; the map is cleared via `clearRouteSignal` in the same method that sets it false, so no drift path exists today.
- **Reroute while `routeVisible == false`** → impossible: reroute requires active navigation, and `startNavigation` (which sets it true) is the only way navigation becomes active.

## Migration Plan

Feature fix — no data migration. Rollback: revert the change; stop leaves the route visible as today. The `routeVisible` flag defaults to `true`, so any partial rollout behaves like the current code until `clearRouteFromMap()` is wired.

## Open Questions

None — the behavior decision (Option B: keep route in panel, hide from map) was made by the user before this change was created.
