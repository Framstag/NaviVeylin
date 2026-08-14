## Context

The JNI bridge already provides the full navigation infrastructure: `OSMScoutClient.startNavigationWithVehicle()`, `NavigationController` (with `stop()` and `processLocation()`), and `NavigationListener` with callbacks for position estimates, route instructions, reroute requests, speed, ETA, and target reached. The `RouteEntry` carries a `routeHandle` (long) used to start navigation on a calculated route.

What's missing is the Kotlin/Compose layer: a `NavigationController` wrapper that exposes state as `StateFlow`, UI composables for next-turn overlay and navigation state, and wiring to start/stop navigation from the existing route summary dialog and route panel.

## Goals / Non-Goals

**Goals:**
- Wire "Start Navigation" button in `RouteSummaryDialog` and `RoutePanel` to JNI navigation
- Add "Stop Navigation" button
- Implement GPS follow mode (map centers on location, rotates to bearing)
- Show next-turn overlay (turn icon, distance, street name, next-next hint)
- Show navigation state (ETA, remaining distance, speed, max speed)
- Handle reroute (recalculate route from current position)
- Expose navigation state as `StateFlow` for Compose

**Non-Goals:**
- Voice guidance or audio instructions
- Android Auto integration
- GPX track playback
- Speed spike filtering (deferred)
- Turn-zoom logic (deferred)

## Decisions

### 1. Kotlin `NavigationController` wrapper as `@HiltViewModel`
**Decision:** Create `NavigationViewModel` (`@HiltViewModel`) that wraps the JNI `NavigationController` and `NavigationListener`. Exposes `NavigationState` as `StateFlow`.

**Rationale:** Hilt manages the ViewModel lifecycle. `NavigationViewModel` lives at the `MapCanvasScreen` level (same scope as `MapCanvasViewModel` and `RoutePanelViewModel`). It owns the `NavigationListener` JNI callbacks and translates them into Compose-friendly state updates.

**Alternatives considered:**
- Singleton service — would outlive screen scope, harder to manage lifecycle
- State in `MapCanvasViewModel` — would bloat it with navigation concerns; separate ViewModel is cleaner

### 2. Navigation state as data class
**Decision:**
```kotlin
data class NavigationState(
    val isNavigating: Boolean = false,
    val currentStepIndex: Int = 0,
    val nextInstruction: RouteInstruction? = null,
    val instructions: List<RouteInstruction> = emptyList(),
    val remainingDistance: Double = 0.0,
    val etaMillis: Long = 0L,
    val currentSpeedKmH: Double = Double.NaN,
    val maxSpeedKmH: Double = Double.NaN,
    val position: NavigationPosition? = null
)
```

**Rationale:** Single data class observed by all navigation UI composables. Each JNI callback updates the relevant fields and emits a new state.

### 3. GPS follow mode in `MapCanvasViewModel`
**Decision:** Add `followMode: Boolean` to `MapCanvasUiState`. When enabled, `MapCanvasViewModel` collects location updates and calls `viewModel.updateCenter()` + `viewModel.updateAngle()` on each GPS fix. `NavigationViewModel` toggles follow mode on start/stop.

**Rationale:** Follow mode is a map concern (centering, rotation) — it belongs in `MapCanvasViewModel`. `NavigationViewModel` signals when to enable/disable it.

**Alternatives considered:**
- Follow mode in `NavigationViewModel` — would require map API access, coupling navigation to map rendering

### 4. Next-turn overlay as composable overlay on `MapCanvasScreen`
**Decision:** `NextTurnOverlay` composable positioned at the top of the map canvas, showing turn icon, distance, street name, and optional next-next hint. Observes `NavigationViewModel.uiState`.

**Rationale:** Overlay on the map canvas is the standard pattern (like `LocationMarkerOverlay`). No dialog or sheet needed — it's always visible during navigation.

### 5. Navigation state overlay as composable
**Decision:** `NavigationStateOverlay` composable positioned at the bottom of the map canvas, showing ETA, remaining distance, speed, and max speed. Observes `NavigationViewModel.uiState`.

**Rationale:** Same pattern as next-turn overlay. Bottom placement avoids clashing with the next-turn overlay at top.

### 6. Reroute via `RoutePanelViewModel.calculateRoute()`
**Decision:** When `NavigationListener.onRerouteRequest()` fires, `NavigationViewModel` calls `RoutePanelViewModel.calculateRoute()` with current position as start and original destination. On success, navigation continues with the new route.

**Rationale:** Reuses existing route calculation logic. The new route's `routeHandle` is passed to `startNavigationWithVehicle()` to resume guidance.

**Alternatives considered:**
- Direct JNI reroute call — would bypass the existing route calculation flow and its UI state updates

### 7. Start/Stop buttons in route summary dialog and route panel
**Decision:** `RouteSummaryDialog` receives `onStartNavigation` and `onStopNavigation` callbacks. `RoutePanel` shows "Start Navigation" button when route is calculated and navigation is inactive, "Stop Navigation" when active.

**Rationale:** Follows the existing callback pattern. The dialog and panel don't need to know about navigation internals — they just fire callbacks.

## Risks / Trade-offs

- **[Risk] Navigation thread is native** → JNI callbacks arrive on a native thread. `NavigationViewModel` must marshal state updates to the main thread via `viewModelScope.launch`. Mitigation: all state updates go through `viewModelScope.launch(Dispatchers.Main)`.
- **[Risk] Reroute during navigation** → If reroute fails (no route found), navigation continues with the old route. Mitigation: `NavigationViewModel` keeps the old route until reroute succeeds; on failure, logs error and continues.
- **[Risk] No turn-zoom yet** → Map doesn't auto-zoom near turns. Mitigation: deferred to future change; follow mode keeps a reasonable zoom level.
- **[Trade-off] Single `NavigationViewModel` per screen** → If the app ever supports multi-window or split-screen, the ViewModel scope needs review. Acceptable for now.
