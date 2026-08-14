## 1. NavigationViewModel

- [x] 1.1 Create `NavigationViewModel` (`@HiltViewModel`) with `NavigationState` data class (isNavigating, currentStepIndex, nextInstruction, instructions, remainingDistance, etaMillis, currentSpeedKmH, maxSpeedKmH, position)
- [x] 1.2 Implement `startNavigation(routeEntry: RouteEntry, vehicle: Vehicle)` — calls `OSMScoutClient.startNavigationWithVehicle()` with route handle, stores returned `NavigationController`
- [x] 1.3 Implement `stopNavigation()` — calls `NavigationController.stop()`, clears state, disables follow mode
- [x] 1.4 Implement `NavigationListener` callbacks: `onPositionEstimate`, `onNextRouteInstruction`, `onRouteInstructions`, `onArrivalEstimate`, `onCurrentSpeed`, `onMaxAllowedSpeed`, `onTargetReached`, `onRerouteRequest`, `onError`
- [x] 1.5 Marshal all JNI callbacks to main thread via `viewModelScope.launch(Dispatchers.Main)` and update `NavigationState`
- [x] 1.6 Implement reroute: on `onRerouteRequest`, call `RoutePanelViewModel.calculateRoute()` with current position as start, original destination; on success, call `startNavigation()` with new route handle

## 2. GPS Follow Mode

- [x] 2.1 Add `followMode: Boolean` and `mapAngle: Double` to `MapCanvasUiState`
- [x] 2.2 Add `setFollowMode(enabled: Boolean)` and `setMapAngle(angle: Double)` to `MapCanvasViewModel`
- [x] 2.3 In `MapCanvasViewModel`, when follow mode is enabled, collect GPS location and call `updateCenter()` + `setMapAngle()` on each fix
- [x] 2.4 Wire `NavigationViewModel` to toggle follow mode on start/stop navigation
- [x] 2.5 Pass `mapAngle` to `MapRenderer.render()` so map rotates to driving direction

## 3. NextTurnOverlay Composable

- [x] 3.1 Create `app/src/main/java/com/naviveylin/ui/navigation/NextTurnOverlay.kt`
- [x] 3.2 Implement composable showing turn type icon (emoji), distance (formatted), and street name from `NavigationState.nextInstruction`
- [x] 3.3 Add "next next" hint row when `RouteInstruction.hasNextNext()` is true
- [x] 3.4 Position overlay at top of map canvas in `MapCanvasScreen`

## 4. NavigationStateOverlay Composable

- [x] 4.1 Create `app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt`
- [x] 4.2 Implement composable showing ETA (formatted from `etaMillis`), remaining distance, current speed, and max allowed speed
- [x] 4.3 Position overlay at bottom of map canvas in `MapCanvasScreen`

## 5. Wire Start/Stop in RouteSummaryDialog

- [x] 5.1 Add `onStartNavigation` and `onStopNavigation` callbacks to `RouteSummaryDialog`
- [x] 5.2 When navigation is inactive, show "Start Navigation" `Button` (existing, wire to `onStartNavigation`)
- [x] 5.3 When navigation is active, show "Stop Navigation" `Button` instead
- [x] 5.4 In `MapCanvasScreen`, wire callbacks to `NavigationViewModel.startNavigation()` / `stopNavigation()`

## 6. Wire Start/Stop in RoutePanel

- [x] 6.1 Add `isNavigating: Boolean` to `RoutePanelUiState`
- [x] 6.2 In `RoutePanel`, when route is calculated and navigation is inactive, show "Start Navigation" button
- [x] 6.3 When navigation is active, show "Stop Navigation" button instead
- [x] 6.4 Wire buttons to `NavigationViewModel` via callbacks

## 7. Compose Navigation Overlays in MapCanvasScreen

- [x] 7.1 Add `NavigationViewModel` parameter to `MapCanvasScreen`
- [x] 7.2 Compose `NextTurnOverlay` when `NavigationState.isNavigating` is true
- [x] 7.3 Compose `NavigationStateOverlay` when `NavigationState.isNavigating` is true
- [x] 7.4 Pass `NavigationState.activeStepIndex` to `RouteSummaryDialog` for step highlighting during navigation

## 8. Build & Verify

- [x] 8.1 Run `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` — verify compilation
- [x] 8.2 Run `./gradlew test` — verify existing tests still pass
- [x] 8.3 Manual smoke test: calculate route → start navigation → verify follow mode, next-turn overlay, state overlay → stop navigation → verify clean state
