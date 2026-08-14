## Context

Current state: `MapCanvasScreen.kt` shows search panel → `LocationDetailsSheet` with favorite controls. JNI bridge has `calculateRouteAsync()`, `cancelRoute()`, `RouteCallback`, `RouteEntry`, `RouteInstruction`, `RoutingProfile`, `Vehicle` — all exist in `OSMScoutClient.java`. `MapRenderer` has `renderWithRoute()` accepting route coords + POI types `_route_start`, `_route_end`. No route UI exists.

See `proposal.md` — Why for motivation. Specs at `specs/route-panel-ui/spec.md`.

## Goals / Non-Goals

**Goals:**
- Route panel as modal bottom sheet with start/dest fields, vehicle selector, swap, calculate/clear/cancel
- Reuse existing `SearchPanel` for route location picking
- Reuse existing `FavoritesSheet` or create lightweight picker for fav selection
- Route polyline + markers rendered on map via `MapRenderer.renderWithRoute()`
- Turn-by-turn instruction list in panel after calculation
- Route button on `LocationDetailsSheet` prefills start

**Non-Goals:**
- Turn-by-turn navigation (GPS follow mode, auto-rerouting, next-turn overlay — deferred)
- Route avoidance options (tolls/ferries — deferred, `RoutingProfile` supports them)
- Route persistence across app restarts
- Multiple route alternatives

## Decisions

### Decision 1: Route panel as ModalBottomSheet (same pattern as LocationDetailsSheet)

**Choice:** `ModalBottomSheet` with `skipPartiallyExpanded = false` for draggable dismiss.

**Rationale:** Consistent with existing `LocationDetailsSheet` and `SearchPanel`. Material 3 bottom sheet is the established pattern in this app. No nav graph changes needed — sheet lives on top of map via boolean state flag.

**Alternatives considered:**
- Full-screen composable with nav route — rejected, adds nav graph complexity and breaks the map overlay pattern
- Dialog — rejected, less flexible sizing and no drag-to-dismiss

### Decision 2: RoutePanelViewModel as @HiltViewModel (injected via hiltViewModel())

**Choice:** `RoutePanelViewModel` is a `@HiltViewModel` obtained via `hiltViewModel()` in `MapCanvasScreen` and passed to both `MapCanvasViewModel` (via `setRoutePanelViewModel()`) and the `RoutePanel` composable.

**Rationale:** Keeps route state separate from `MapCanvasViewModel` which is already large. Route panel has its own lifecycle (async calculation, cancel, instruction state). Hilt handles injection. Using `hiltViewModel()` ensures the ViewModel is scoped to the screen's lifecycle.

**State exposed:**
```kotlin
data class RoutePanelUiState(
    val startLocation: LocationEntry? = null,
    val destLocation: LocationEntry? = null,
    val vehicle: Vehicle = Vehicle.CAR,
    val routeState: RouteState = RouteState.Idle,
    val routeEntry: RouteEntry? = null,
    val routeInstructions: List<RouteInstruction> = emptyList(),
    val error: String? = null
)

sealed interface RouteState {
    data object Idle : RouteState
    data object Calculating : RouteState
    data object Done : RouteState
    data class Error(val message: String) : RouteState
}
```

### Decision 3: Route state lives in MapCanvasViewModel, not RoutePanelViewModel

**Choice:** `MapCanvasViewModel` holds the route data needed for map rendering (`routeStart`, `routeDest`, `routeCoords`). `RoutePanelViewModel` handles calculation logic and passes results up.

**Rationale:** `MapRenderer` lives in `MapCanvasViewModel`. Route polyline + markers need to be passed to the renderer. Rather than two-way communication, `RoutePanelViewModel` exposes a `StateFlow` that `MapCanvasViewModel` collects.

**Flow:**
```
RoutePanelViewModel.calculateRoute() → calls client.calculateRouteAsync()
  → onSuccess callback → updates RoutePanelUiState
  → MapCanvasViewModel collects routeCoords → calls renderer.renderWithRoute()
```

### Decision 4: Inline search-on-type in route panel (not SearchPanel reuse)

**Choice:** Route panel fields are `OutlinedTextField` composables that trigger search-on-type internally via `RoutePanelViewModel`. Search results appear inline below the active field, with "Current Location" and "Select Favorite" as the first two entries.

**Rationale:** Simpler UX — user doesn't need to tap a separate search icon. Search results appear in context. The `RoutePanelViewModel` manages its own debounced search via `OSMScoutClient.searchLocations()`.

**Alternatives considered:**
- Reuse `SearchPanel` with `onResultSelectedOverride` — rejected, adds complexity and breaks the inline UX flow

**Implementation:**
```kotlin
// In RoutePanel.kt
RouteSearchField(
    value = if (active) searchQuery else location.label,
    onValueChange = { viewModel.onSearchQueryChanged(it) },
    ...
)
// Results appear below with Current Location, Select Favorite, then search hits
```

### Decision 5: Favorite picker as lightweight dialog, not full FavoritesSheet

**Choice:** Create a simple `FavoritePickerDialog` composable — a modal bottom sheet showing favorite groups and locations as a flat list. User taps one to select.

**Rationale:** `FavoritesSheet` is a full-screen management UI with CRUD operations. For route picking, we only need selection. A lightweight picker is simpler and avoids the complexity of embedding the full sheet.

**Alternatives considered:**
- Reuse `FavoritesSheet` with a selection mode — rejected, would require refactoring the existing sheet
- Dropdown from the route field — rejected, favorites can be many items, needs scrolling

### Decision 6: Current location and favorites as first entries in search results

**Choice:** When a route field is active, the search results list shows "Current Location" as the first entry (if GPS available) and "Select Favorite" as the second entry, followed by OSM search results. Tapping "Current Location" sets the field to the device's GPS coordinates. Tapping "Select Favorite" opens the `FavoritePickerDialog`.

**Rationale:** Matches user's description. Current location is the most common start point. Favorites are the second most common. Both are always accessible without typing. GPS availability check hides "Current Location" when no fix.

### Decision 7: Route polyline via MapRenderer.renderWithRoute()

**Choice:** `MapRenderer` already has `renderWithRoute()` accepting `routeLats`/`routeLons` arrays. `MapCanvasViewModel` passes route coordinates when `RouteState.Done`.

**Rationale:** No new native rendering code needed. The JNI bridge already supports route overlay rendering. `_route_start` and `_route_end` POI types are already registered.

**Implementation:**
```kotlin
// MapCanvasViewModel
fun onRouteCalculated(entry: RouteEntry) {
    val routeLats = entry.coords.map { it.latitude }.toDoubleArray()
    val routeLons = entry.coords.map { it.longitude }.toDoubleArray()
    mapRenderer?.setRoute(routeLats, routeLons, 
        startLat, startLon, destLat, destLon)
    renderMap()
}
```

### Decision 8: Instruction list in route panel

**Choice:** After successful calculation, `RouteInstruction[]` from callback is displayed as a scrollable list in the route panel below the controls.

**Rationale:** Turn-by-turn text is already available from the JNI `RouteInstruction` class (primary text, distance). Displaying in the panel is straightforward — no map interaction needed.

## Risks / Trade-offs

- **[Risk] Async route calculation blocks UI thread** → `calculateRouteAsync()` runs on native thread with progress callback. UI stays responsive. Cancel via `cancelRoute()`.
- **[Risk] Route calculation takes long on large maps** → Progress indicator shown. Cancel button available. No timeout — user decides.
- **[Risk] No route found (disconnected graph, island)** → Error state displayed in panel. User can try different vehicle or locations.
- **[Trade-off] Route state not persisted** → Route cleared on app restart. Acceptable for v1 — persistence adds complexity (Room table for routes).
- **[Trade-off] Single route only** → No multi-route comparison. Acceptable for v1 — matches JavaScout behavior.
