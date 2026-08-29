## 1. LocationService: GpsFix + BearingFilter

- [x] 1.1 Define `GpsFix` data class (lat, lon, accuracy, speedKmH, `smoothedBearing`, `markerBearing`, time) in `app/src/main/java/com/naviveylin/location/LocationService.kt` and change `location` to `StateFlow<GpsFix?>` — spec: `gps-bearing-smoothing` (Fix carries both bearings)
- [x] 1.2 Implement `BearingFilter` inside `LocationService`: course history ring buffer, EMA low-pass (fast/slow alpha), turn reset, teleport reset, segment bearing — move the algorithm verbatim from `MapCanvasViewModel` (lines ~1022-1115) — spec: `gps-bearing-smoothing` (LocationManager path fakes Fused quality)
- [x] 1.3 Fused path: `markerBearing` = `loc.bearing` as delivered (no smoothing), `smoothedBearing` = light EMA on `loc.bearing` — spec: `gps-bearing-smoothing` (Fused path adds no bearing lag)
- [x] 1.4 LocationManager path: `smoothedBearing` = course-from-track + EMA, `markerBearing` = latest segment bearing — spec: `gps-bearing-smoothing` (LocationManager path fakes Fused quality)
- [x] 1.5 Emit `GpsFix` from both `startFusedUpdates` and `startManagerUpdates` paths; keep the `shouldEmit` dedup — spec: `gps-bearing-smoothing` (Same shape for both providers)
- [x] 1.6 Verify `./gradlew :app:compileMobileDebugKotlin` compiles

## 2. MapCanvasViewModel: consume GpsFix, drop course logic

- [x] 2.1 Remove course history, EMA, turn reset, teleport reset, and segment-bearing state/functions from `MapCanvasViewModel` (course ring buffer, `addCoursePoint`, `computeCourseBearing`, `smoothCourseBearing`, `resetCourseHistory`, `lastSegmentBearing`, `lastSmoothedBearing`, `lastCourseBearing`, `courseStable`) — spec: `gps-bearing-smoothing` (Course derived from raw provider positions)
- [x] 2.2 Consume `GpsFix`: marker arrow = `fix.markerBearing`, map rotation input = `fix.smoothedBearing` — spec: `gps-bearing-smoothing` (Marker tracks freshest bearing, Marker and map rotation decoupled)
- [x] 2.3 Keep deadband vs rendered angle (`MIN_BEARING_DELTA_DEG`), rate clamp (`MAX_ANGLE_RATE_DEG_PER_RENDER`), and render throttle/coalescing (`GPS_FOLLOW_RENDER_INTERVAL_MS`) unchanged — spec: `gps-bearing-smoothing` (Map rotation does not re-render on small bearing changes)
- [x] 2.4 Verify `./gradlew :app:compileMobileDebugKotlin` compiles

## 3. AutoServiceModule: consume GpsFix

- [x] 3.1 Map `GpsFix` → `AutoPosition` in `app/src/main/java/com/naviveylin/di/AutoServiceModule.kt` with bearing = `smoothedBearing` — spec: `gps-bearing-smoothing` (Car map uses smoothed bearing)
- [x] 3.2 Verify `./gradlew :app:compileMobileDebugKotlin` compiles

## 4. Unit tests

- [x] 4.1 Add `BearingFilter` tests to `app/src/test/java/com/naviveylin/location/LocationServiceTest.kt`: Fused path passes `loc.bearing` through as `markerBearing` unchanged; LocationManager path derives `smoothedBearing` from a straight track; turn reset clears history on sharp turn; teleport reset clears history on jump — spec: `gps-bearing-smoothing` (Fused path adds no bearing lag, LocationManager path fakes Fused quality)
- [x] 4.2 Update `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelFollowModeTest.kt` to feed `GpsFix`: small `smoothedBearing` change within deadband does not trigger a render; large change renders at clamped rate; marker arrow reflects `markerBearing` immediately — spec: `gps-bearing-smoothing` (Map rotation does not re-render on small bearing changes, Marker bearing has no added lag)
- [x] 4.3 Verify `./gradlew :app:test` passes (all existing tests still green)

## 5. Verification

- [x] 5.1 Run `./gradlew :app:test` and confirm all new and existing tests pass
- [x] 5.2 Build both flavors: `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug`
- [x] 5.3 On-device check: follow mode marker arrow turns immediately at corners, map rotation lags smoothly, no render storm on straight roads (`adb logcat -s NaviVeylin`)

## 6. Marker no-lag follow-up (found during on-device use)

- [x] 6.1 Update `uiState.gpsMarker*` on every distinct fix (`updateMarkerState` in `MapCanvasViewModel`) so the marker tracks the vehicle immediately, independent of the render cadence; frame collector no longer overwrites the marker fields — spec: `gps-bearing-smoothing` (Marker bearing has no added lag)
- [x] 6.2 Preserve the turn-triggering segment as `lastSegmentBearing` in `BearingFilter` so the marker points the new direction at the turn fix itself — spec: `gps-bearing-smoothing` (LocationManager path fakes Fused quality)
- [x] 6.3 Add tests: `markerBearingUpdatesOnEveryFixWithoutRender` (FollowModeTest), `managerPath_turnResetKeepsFreshSegmentAsMarkerBearing` (LocationServiceTest); verify `./gradlew :app:testMobileDebugUnitTest` and both flavor builds pass
- [x] 6.4 Add scenario tests: `managerPath_derivesBearingFromRawProviderPositions` (LocationServiceTest — bearing independent of navigation state), `autoLocationProvider_mapsGpsFixToAutoPosition_withSmoothedBearing` (new `AutoServiceModuleTest` — car map uses smoothed bearing); verify `./gradlew :app:testMobileDebugUnitTest` passes
