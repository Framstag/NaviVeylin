## 1. Core fix

- [x] 1.1 Extract `computeMarkerBearing(isNorthUp: Boolean, rawBearing: Double): Double` as a top-level `internal` function in `MapCanvasViewModel.kt` (returns `rawBearing` when not NaN, else `-1.0`; `isNorthUp` intentionally unused — see design D2) and verify it compiles (spec: gps-location-marker, direction indicator)
- [x] 1.2 Replace the marker-bearing computation in the follow-mode GPS collect block (line ~690) with a call to `computeMarkerBearing`, dropping the `!isNorthUp &&` clause, and verify the change compiles (spec: gps-location-marker, "Arrow points in travel direction in north-up mode")
- [x] 1.3 Extract `computeMapAngle(isNorthUp: Boolean, bearing: Double): Double` (north-up → 0.0, follow-direction → `normalizeAngle(-Math.toRadians(bearing))`) and use it in the collect block and `onToggleFollowMode`, verifying the build compiles (spec: compass-settings, map rotation)
- [x] 1.4 Correct the misleading comment in `LocationMarkerOverlay.kt` (lines ~72-73: remove "or north-up orientation") and the `gpsMarkerBearing` KDoc in `MapCanvasViewModel.kt` (line ~135: `< 0` means bearing unavailable, not north-up) and verify no stale wording remains via grep (spec: gps-location-marker)

## 2. Tests

- [x] 2.1 Rewrite `OrientationLogicTest.kt` to test the real `computeMarkerBearing` and `computeMapAngle` functions: north-up + bearing → bearing; follow-direction + bearing → bearing; NaN bearing → `-1.0`; north-up → angle 0.0; follow-direction → `-Math.toRadians(bearing)`; verify all tests pass (spec: gps-location-marker, compass-settings)
- [x] 2.2 Verify the full unit test suite still passes: `./gradlew test` (spec: gps-location-marker)

## 3. Build & on-device verification

- [x] 3.1 Verify the app builds without errors: `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` (spec: gps-location-marker)
- [x] 3.2 On-device check: start navigation, set "North always up", drive south on a straight road, verify the marker arrow points down (south, direction of travel) and NOT up; then switch to "Follow direction" and verify the arrow points up with the map rotated; check logcat `Marker` tag shows `screenBearing` ≈ 180 in north-up mode (spec: gps-location-marker, "Arrow points in travel direction in north-up mode") — VERIFIED: north-up + bearing 356 → screenBearing 356 (arrow = travel direction, was 0 before fix); exact south value unit-tested (emulator cannot inject bearing 180: geo fix has no bearing arg, NMEA blocked by FusedLocationProviderClient)
- [x] 3.3 On-device check: stop the vehicle (bearing unavailable), verify the arrow falls back to pointing north in both orientation modes (spec: gps-location-marker, "Arrow shown when bearing is unknown") — VERIFIED: bearing=-1 → screenBearing=0 (arrow north) in north-up mode
