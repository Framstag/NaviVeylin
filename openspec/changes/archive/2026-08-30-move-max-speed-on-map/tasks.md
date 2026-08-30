# Tasks — move-max-speed-on-map

## 1. Speed widget composable

- [x] 1.1 Create `app/src/main/java/com/naviveylin/ui/map/SpeedWidget.kt` with a `SpeedWidget` composable (current-speed badge + round max-speed sign below, mirroring AA `SurfaceIndicators` visualisation) and verify it compiles
- [x] 1.2 Implement overspeed color rule (red when `currentSpeedKmH > maxSpeedKmH + 5`, else normal) and verify a Compose unit test covers both branches
- [x] 1.3 Implement hidden state (no speed data → nothing drawn) and verify a Compose unit test covers NaN/negative/zero-unknown inputs

## 2. Follow-mode speed data

- [x] 2.1 Add `currentSpeedKmH` and `maxSpeedKmH` fields to `MapCanvasUiState` and verify the data class compiles
- [x] 2.2 Populate `currentSpeedKmH` from the GPS fix `speedKmH` in `MapCanvasViewModel`'s location collection and verify the state updates on a fix
- [x] 2.3 Resolve `maxSpeedKmH` via `client.getMaxSpeedAt(lat, lon)` off the main thread, throttled on position change, and verify a unit test covers the throttle and NaN-on-failure path

## 3. Wire widget into map screen

- [x] 3.1 Place `SpeedWidget` in the top-right overlay Column below `MapCompassBlock` in `MapCanvasScreen` (both orientations) and verify it renders in a Compose test
- [x] 3.2 Wire visibility: show when (follow mode active OR navigating) AND speed data available; hide otherwise, and verify a Compose test covers follow-mode-on, navigating, and browsing states
- [x] 3.3 Wire speed sources: navigation state (`navState.currentSpeedKmH`/`maxSpeedKmH`) when navigating, `MapCanvasUiState` fields in follow mode, and verify the widget updates from both sources

## 4. Remove speed from routing status

- [x] 4.1 Remove `currentSpeedKmH`/`maxSpeedKmH` params and the speed column from `NavigationStatsRow` in `NavigationStateOverlay.kt` and verify the file compiles
- [x] 4.2 Remove speed params from `NavigationStateOverlay` and `NavigationDetailsOverlay` signatures and update `MapCanvasScreen` call sites, and verify `./gradlew :app:compileMobileDebugKotlin` passes
- [x] 4.3 Update any existing tests asserting speed in the status card or details view and verify `./gradlew test` passes

## 5. Verification

- [x] 5.1 Run `./gradlew :app:assembleMobileDebug` and verify the build succeeds
- [x] 5.2 Run `openspec validate move-max-speed-on-map --type change` and verify the change is valid
