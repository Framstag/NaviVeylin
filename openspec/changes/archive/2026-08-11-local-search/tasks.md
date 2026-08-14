# Tasks — Admin-Region-Scoped Local Search

Specs: `specs/location-search/spec.md` (requirements "Search scoped by current admin region", "Admin region follows user movement"). Design: `design.md` (D1–D5).

## 1. JNI Bridge — admin region resolver + handle plumbing

- [x] 1.1 `OSMScoutClient.java` (libosmscout-client-java): declare `native long resolveAdminRegion(double lat, double lon)` returning an opaque handle (0 = none), `native void releaseAdminRegion(long handle)`, and extend `searchLocations` to `LocationEntry[] searchLocations(String query, int limit, long adminRegionHandle)`; update javadoc
- [x] 1.2 `OSMScoutClient.cpp`: add `std::map<int64_t, osmscout::AdminRegionRef>` admin-region handle store to `ClientData`; implement `Java_..._resolveAdminRegion` — inside `RunSynchronousJob`, walk `LocationService::VisitAdminRegions`, load `region.object` geometry via `Database::GetWayByOffset`/`GetAreaByOffset`, bounding-box pre-check + ray-casting point-in-ring test, descend into children, keep deepest containing region; store ref, return handle
- [x] 1.3 `OSMScoutClient.cpp`: `Java_..._searchLocations` — accept handle param; when handle != 0, resolve ref from store and call `param.SetDefaultAdminRegion(ref)` on the `LocationStringSearchParameter`; keep unconstrained path when handle == 0
- [x] 1.4 `OSMScoutClient.cpp`: implement `Java_..._releaseAdminRegion` (erase from store); clear the store in `close()` path to avoid leaks
- [x] 1.5 Verify native build compiles: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` (spec 1: scoped search; design D1/D2)

## 2. ViewModel — GPS gating, region cache, movement threshold

- [x] 2.1 `MapCanvasViewModel.kt`: hold resolved-region state (handle + coordinate it was resolved at); expose internal helper `resolveSearchAdminRegion()` gated on `GpsFixQuality == GOOD` (reuses existing 5s freshness / 50m accuracy classification), handle 0 → no scoping (spec: "No GPS fix falls back to unconstrained search"; design D3)
- [x] 2.2 Debounced search flow: on each query, reuse cached handle while `distance(position, resolvedCoord) <= 500m`, else re-resolve (or clear when fix no longer GOOD); pass handle to `searchLocations(query, 20, handle)` (spec: "Region re-resolved after significant movement", "No re-resolution on minor movement", "Stable region during a typing session"; design D4)
- [x] 2.3 Lifecycle: `releaseAdminRegion` on panel close/clear/ViewModel clear, and before replacing a handle; guard against calls when `isInitialized()` false or client closed
- [x] 2.4 Verify app builds + search still works unconstrained without GPS: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`

## 3. Tests

- [x] 3.1 Update `app/src/test/java/com/framstag/libosmscout/client/FakeOSMScoutClient.kt` for the new `searchLocations` signature + `resolveAdminRegion`/`releaseAdminRegion` overrides (spec: search scoping; design D5)
- [x] 3.2 Add ViewModel unit tests: GOOD fix → handle passed; NONE/POOR → handle 0; movement ≤ threshold → handle reused (no re-resolve); movement > threshold → re-resolve; resolve failure → unconstrained; release called on clear (spec: all scenarios of both requirements; design D3/D4)
- [x] 3.3 Run full unit suite: `./gradlew test` — all existing tests pass (design D5)

## 4. Verification

- [x] 4.1 `./gradlew :app:assembleDebug` (all ABIs) builds clean
- [x] 4.2 Manual smoke check on device: with GPS on, search "Hauptstraße 12" (incomplete) returns region-scoped matches; with GPS off, search behaves as before

## 5. Region name display (extension)

- [x] 5.1 Native + Java: add `String getAdminRegionName(long handle)` — declaration in both `OSMScoutClient.java` files (local fork + submodule), C++ impl in `OSMScoutClient.cpp` (handle-store lookup → `AdminRegion::name`, null for unknown/empty) (spec: "Resolved region name shown in search panel"; design D6)
- [x] 5.2 `MapCanvasViewModel.kt`: track `searchAdminRegionName` (fetch after successful resolve, clear on release), expose via `MapCanvasUiState.searchAdminRegionName` (spec: region name shown / no name without region / name follows re-resolution; design D6)
- [x] 5.3 UI: `SearchPanel` displays the region name above the search input when set; wire param through `MapCanvasScreen`
- [x] 5.4 Tests: `FakeOSMScoutClient.getAdminRegionName` override; ViewModel tests for name set on resolve, cleared on release/lost fix, follows re-resolution
- [x] 5.5 Verify: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` + `./gradlew test`

- [x] 5.6 Eager region resolution: resolve on search panel open + on GPS GOOD transition while panel open, so the region name appears without a typed query (fix reported in test)

- [x] 5.7 Replace hand-rolled containment walk with canonical `LocationDescriptionService::ReverseLookupRegion` (first implementation returned no region on real maps — geometry walk unreliable)

- [x] 5.8 Review follow-ups: spec scope made concrete (route panel search explicitly unconstrained), `NO_ADMIN_REGION` sentinel constant, `SearchPanelComposeTest` (region label shown/hidden), robust auto-focus via onGloballyPositioned
