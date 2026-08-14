## 1. Speed spike filtering

*Spec: speed-spike-filtering. File: `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt`*

- [x] 1.1 Add `lastValidSpeedKmH` field (default 20.0) and `filterSpeed(rawSpeedKmH: Double): Double` method that rejects speed > 150 km/h and uses last good speed
- [x] 1.2 Wire `NavigationViewModel.state` collection in follow-mode loop to read `currentSpeedKmH` and pass through `filterSpeed()` before any zoom computation
- [x] 1.3 Verify: speed spike of 392 km/h is rejected, last good speed used; first speed spike uses default 20 km/h

## 2. Speed-to-magnification mapping

*Spec: auto-speed-zoom. File: `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt`*

- [x] 2.1 Define `SpeedZoomLevel` data class and `SPEED_ZOOM_TABLE` with breakpoints: 0→17, 6→16.5, 15→16, 30→15, 60→14, 90→13, 130→12
- [x] 2.2 Implement `computeSpeedZoom(speedKmH: Double): Double` with linear interpolation between breakpoints, clamping at edges
- [x] 2.3 Implement `findSpeedBandIndex(speedKmH: Double): Int` returning the table index for current speed (used for band-change detection)

## 3. Auto-zoom in follow-mode handler

*Spec: auto-speed-zoom. File: `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt`*

- [x] 3.1 Add auto-zoom state fields: `autoZoomEnabled`, `autoZoomSuspended`, `lastSpeedBandIndex`, `currentTargetMag`
- [x] 3.2 In follow-mode location handler: compute target mag from filtered speed, apply smooth transition (max ±1 per update toward target), call `updateMagnification()` + `renderMap()`
- [x] 3.3 Add `autoZoomEnabled` to `MapCanvasUiState` (default true)
- [x] 3.4 Add `onToggleAutoZoom(enabled: Boolean)` method to `MapCanvasViewModel`
- [x] 3.5 Set initial magnification to 15.0 when navigation starts (routing-sensible default)

## 4. Turn-aware zoom boosting

*Spec: auto-turn-zoom. File: `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt`*

- [x] 4.1 Implement `computeTurnBoost(turnDistanceMeters: Double): Double` returning 16.0 if ≤ 300m, 15.0 if ≤ 600m, 0.0 otherwise
- [x] 4.2 In follow-mode handler: read turn distance from `NavigationViewModel.state.nextInstruction.distance`, compute turn floor, final target = `maxOf(speedTarget, turnFloor)`
- [x] 4.3 Track turn waypoint passage: when distance crosses from positive to negative (past turn), continue boost for 600m past, then revert to speed-only

## 5. Manual zoom suspension

*Spec: auto-speed-zoom. File: `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` and `ZoomControls.kt`*

- [x] 5.1 In `updateMagnification()`: if user-initiated zoom change detected, set `autoZoomSuspended = true` and capture `lastSpeedBandIndex`
- [x] 5.2 In follow-mode handler: if `autoZoomSuspended` and speed band changed, re-engage auto-zoom
- [x] 5.3 Wire `ZoomControls` and pinch-zoom gesture to report manual zoom events (distinguish programmatic vs user zoom)

## 6. Auto-zoom toggle UI

*Spec: auto-speed-zoom. Files: `app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt`, `MapCanvasScreen.kt`*

- [x] 6.1 Add auto-zoom toggle button to `NavigationStateOverlay` composable (icon: zoom-in/out with auto label)
- [x] 6.2 Wire toggle to `MapCanvasViewModel.onToggleAutoZoom()`
- [x] 6.3 Show toggle only when navigation is active and follow mode is enabled

## 7. Build verification

- [x] 7.1 Run `./gradlew :app:assembleDebug` and verify compilation succeeds
- [x] 7.2 Run `./gradlew test` and verify all existing tests pass
- [x] 7.3 Manual test: start navigation with GPX track, verify zoom adjusts as speed changes
- [x] 7.4 Manual test: manually zoom while auto-zoom active, verify zoom stays at manual level until speed crosses threshold
- [x] 7.5 Manual test: disable auto-zoom toggle, verify zoom stays fixed regardless of speed
- [x] 7.6 Manual test: approach a turn, verify zoom boosts to ≥ 16 within 300m
