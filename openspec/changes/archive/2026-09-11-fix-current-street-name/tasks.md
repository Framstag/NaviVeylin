# Tasks: fix-current-street-name

Specs: `road-lookup-bearing` (new), `current-road-info` (modified), `auto/free-driving` (modified), `auto/navigation-view` (modified). Design: see design.md — D1 (way info on `NavigationPosition`), D2 (`getRoadAt` JNI), D3 (auto consumes `state.currentRoadInfo`), D4 (`RoadInfo` return), D5 (phone free-driving label), D6 (off-route fallback).

## 1. Native submodule patch (libosmscout)

Specs: road-lookup-bearing (all), current-road-info (on-route + off-route). Design: D1, D2, D4.

- [x] 1.1 In `DispatchPositionEstimate` (`app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp`), read `NameFeature` + `RefFeature` from `positionMessage->position.way` (via `position.typeConfig`) and pass `wayName`/`wayRef` through the `NavigationPosition` constructor; verify the submodule builds and the JNI name mapping compiles
- [x] 1.2 Add `wayName`/`wayRef` fields + constructor params to `NavigationPosition.java` (submodule java) matching the C++ ctor call; verify the JavaScout build compiles
- [x] 1.3 Implement `getRoadAt(lat, lon, bearing)` in `OSMScoutClient.cpp`: load ways in radius (~50 m, skip basemap), nearest point + segment direction per way, rank by bearing match (undirected angle diff ≤ 45° when bearing valid) then distance; read Name/Ref/MaxSpeed features; construct a `RoadInfo` object via JNI; verify it compiles and contains no Android APIs (CI Android-free gate)
- [x] 1.4 Commit the submodule change with a minimal, upstreamable message and verify `git status` of `app/src/main/cpp/libosmscout` is clean afterwards (except the intentional submodule pointer bump in the app repo)

## 2. JNI bridge Java API (app-owned module)

Specs: road-lookup-bearing. Design: D4.

- [x] 2.1 Add `RoadInfo` class (`osmscout-client-java/src/main/java/com/framstag/libosmscout/client/RoadInfo.java`) with `name`, `ref`, `typeName`, `maxSpeedKmH` (NaN when undefined); verify it compiles
- [x] 2.2 Declare native `getRoadAt(double lat, double lon, double bearing)` on the app-owned `OSMScoutClient.java` returning `RoadInfo`; verify JNI name mapping compiles for all ABIs
- [x] 2.3 Update `FakeOSMScoutClient` in `app/src/test/java/com/framstag/libosmscout/client/` with `getRoadAt` (record last call, return configurable `RoadInfo`) and verify existing fake-based tests still pass

## 3. Phone navigation wiring (route-based road info)

Specs: current-road-info (on-route + off-route). Design: D1, D6.

- [x] 3.1 In `NavigationViewModel.onPositionEstimate` (`app/src/main/java/com/naviveylin/navigation/NavigationViewModel.kt`), populate `currentRoadInfo` from `position.wayName`/`wayRef` when on route (skip `getDescription`); verify with a unit test that an OnRoute estimate with way info sets `currentRoadInfo` without a description lookup
- [x] 3.2 Off-route fallback: when `position.state != OnRoute` or way info is absent, call `getRoadAt(position.lat, position.lon, bearing)` (bearing from the estimate, NaN allowed) and populate `currentRoadInfo`; verify with a unit test that an OffRoute estimate triggers `getRoadAt` and sets the info
- [x] 3.3 Keep the existing throttle (2 s / ~50 m) and the `getDescription` path removed or dead-coded; verify the full `NavigationViewModel` test suite passes

## 4. AA controller wiring

Specs: current-road-info, auto/navigation-view. Design: D1, D3, D6.

- [x] 4.1 In `AANavigationController.onPositionEstimate` (`app/src/main/java/com/naviveylin/navigation/AANavigationController.kt`), populate `state.currentRoadInfo` from `position.wayName`/`wayRef` when on route, `getRoadAt` fallback off-route (same rules as the phone VM); verify with a unit test that the AA controller now populates `currentRoadInfo` (previously never set)
- [x] 4.2 Verify the AA controller and phone VM produce identical `currentRoadInfo` for the same position estimate (shared helper or mirrored logic); verify the `:app` module compiles

## 5. Auto NavigationScreen consumes route-based road info

Specs: auto/navigation-view. Design: D3.

- [x] 5.1 In `NavigationScreen.resolveStreetName` (`auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt`), read `state.currentRoadInfo` (name + ref) instead of `getAddressAt`; keep the `StreetNameUpdater` throttle and the ETA-card fallback; verify with a screen test that a `currentRoadInfo` state update changes the street label
- [x] 5.2 Off-route/absent fallback: when `currentRoadInfo` is null, call `getRoadAt` with the last known bearing; verify with a screen test that the fallback path is used when no road info is in state
- [x] 5.3 Format the label as "ref name" (phone's `currentRoadText` format) and verify the label test covers ref-only, name-only, and ref+name cases

## 6. Auto FreeDrivingScreen uses getRoadAt

Specs: auto/free-driving, road-lookup-bearing. Design: D2, D4.

- [x] 6.1 In `FreeDrivingScreen.resolveStreetName` (`auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt`), replace `getAddressAt` + `getMaxSpeedAt` with one `getRoadAt(pos.lat, pos.lon, pos.bearing)` call; label from name+ref, limit sign from `maxSpeedKmH`; verify with a screen test that the label shows "ref name" and the sign uses the same road's limit
- [x] 6.2 Keep the `StreetNameUpdater` throttle and failure semantics (failures keep the last label, unnamed roads clear it); verify the existing free-driving tests still pass

## 7. Phone free-driving street label

Specs: current-road-info (free-driving requirement). Design: D5.

- [x] 7.1 Add `resolveCurrentRoad(lat, lon, bearing)` to `MapCanvasViewModel` mirroring `resolveMaxSpeed` (move threshold + cooldown, off-main-thread, updates uiState with a `RoadInfo`); verify with a unit test that movement triggers a `getRoadAt` call and the state updates
- [x] 7.2 Add a bottom-center street-label overlay to `MapCanvasScreen` (Compose, dark pill, "ref name" text), shown only when no route is active and road info exists; verify with a Compose test that the label appears in free driving and is hidden during navigation
- [x] 7.3 Verify the label does not overlap the right-side widget column or the attribution overlay in portrait and landscape (existing layout tests)

## 8. Tests and build gates

Specs: all. Design: all.

- [x] 8.1 Add/extend unit tests: `NavigationViewModel` (on-route way info, off-route `getRoadAt`), `AANavigationController`, `MapCanvasViewModel` (free-driving lookup), auto `NavigationScreen`/`FreeDrivingScreen` (label + fallback), `StreetNameUpdater` (ref handling); verify `./gradlew :core:test :auto:test :app:testMobileDebugUnitTest` passes
- [x] 8.2 Add JavaScout tests for `getRoadAt` (bearing disambiguation, NaN bearing fallback, name/ref/maxSpeed extraction) gated on a DB fixture like the existing POI search tests; verify `cd JavaScout && mvn test` passes
- [x] 8.3 Verify the native change compiles for all target ABIs: `./gradlew :app:assembleMobileDebug` (arm64-v8a, armeabi-v7a, x86_64) without errors and the CI Android-free gate passes
- [x] 8.4 Verify the full `./gradlew test` suite passes (existing + new)

## 9. Guidelines and documentation

- [x] 9.1 Update `guidelines/UI.md` if it documents the Auto street-label source or the phone road-info row (parity: both surfaces show "ref name"); verify the doc reflects the route-based source
- [x] 9.2 Check `guidelines/MapRendering.md` and `guidelines/Design.md` for road-info source lists and update if they enumerate the lookup mechanism (no change = note "no guideline update required")
- [x] 9.3 Confirm `openspec validate fix-current-street-name` passes and all spec scenarios map to tasks

## 10. On-device verification

Specs: all. Design: R1–R5.

- [x] 10.1 Phone navigation: drive a route with refs (e.g. B-road) — the road row shows "ref name" from the route, updates on street change, no side-street picks at intersections; inspect `adb logcat -s NaviVeylin` for the road-info source
- [x] 10.2 Phone free driving: no route active — the street label shows the driven street with ref, updates on street change, hidden when no road found
- [x] 10.3 Auto free driving (AA emulator/head unit): label shows "ref name", main road preferred over side street at a junction, limit sign consistent with the label's road
  - Verified: car app runs on the AAOS emulator (CarAppActivity + template host); the `getRoadAt` flow is exercised on-device via the car app's off-route fallback (logcat: `FALLBACK state=false wayName='Steinstraße'`). The FreeDrivingScreen UI itself is unit-tested (label format, limit sign); direct UI automation is blocked because the AA template host renders into a SurfaceView that uiautomator cannot inspect and rotary/D-pad input does not reach the host.
- [x] 10.4 Auto navigation: street name from the route (not `getAddressAt`), ref shown, ETA-card fallback still works when the map label is not safe
  - Verified on-device: deep link `geo:` → car session starts navigation; logcat shows `ROUTE WAY state=true wayName='Schwanenwall' wayRef='B 54'` — the NavigationScreen's street name comes from the route way with the ref. Required fixing a pre-existing bug: the MainActivity automotive trampoline dropped the incoming intent, so deep links never reached the car session (now forwarded).
- [x] 10.5 Off-route during navigation: road info switches to the bearing-aware lookup and back to the route way on re-join; no stale label
- [x] 10.6 Tune the `getRoadAt` radius/bearing-threshold constants against the emulator if side-street picks persist; update the constants and re-run the unit tests
  - Tuning found a real bug: `getRoadAt` ranked ALL way types, so `barrier_wall`/`railway`/`highway_service` could win over the road. Fixed with a drivability filter (access feature `CanRouteCar` + highway-type fallback) — verified on-device: only roads resolve now. Bearing disambiguation (45° threshold) not exercisable on the emulator (fixes carry NaN bearing); covered by unit tests.
