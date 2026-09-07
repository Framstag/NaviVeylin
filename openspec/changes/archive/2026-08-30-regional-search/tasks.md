# Tasks — Regional Search: Parent-Region Scope Expansion

## 1. Native: region level + scope resolution

- [x] 1.1 Add a region-level helper in `OSMScoutClient.cpp` (`GetRegionLevel`) that returns the level of an `AdminRegionRef`: `admin_level` feature value (`AdminLevelFeatureValue::GetAdminLevel()`) when the region object carries it, else hierarchy depth normalized to the admin_level scale via `naviveylin::NormalizeDepthToAdminLevel`. Verify: helper compiles; host test covers normalization.
- [x] 1.2 Add `kMaxSearchRegionLevel` constant (default 5 = Regierungsbezirk/district) in `search_scope.h` (dependency-free, host-testable). Verify: constant referenced by the expansion logic; host test covers the cap boundary.
- [x] 1.3 Implement scope resolution (`ResolveSearchScope`): given the resolved region R, resolve the parent chain and walk up while each parent is at or finer than the cap; scope = the highest fine ancestor (libosmscout's recursive region search covers it and all its subregions in one pass), else {R} when R has no parent or every ancestor is coarser than the cap. Verify: scope logic exercised by the search path; edge cases (no parent, coarser ancestors) return {R}.

## 2. Native: expanded search + scope name

- [x] 2.1 Rework the structured-search section of `DoSearchLocations` to run `SearchForLocationByString` once with `SetDefaultAdminRegion(scopeRegion)`, `SetLimit(limit)`, and the shared breaker; merge entries into the existing `ResultWithDb` vector. Verify: build passes; existing search behavior unchanged when scope = {R}.
- [x] 2.2 No dedupe needed — a single recursive search per database cannot produce overlapping structured results (removed the sibling-loop dedupe). Verify: build passes; results contain each object once.
- [x] 2.3 Restrict expansion to the database that resolved the admin region handle; other databases in the multi-map loop search unconstrained (applying the foreign region would read garbage offsets from their indexes). Verify: code path branches on the handle's owning database; multi-map search still returns results without index errors.
- [x] 2.4 Add JNI method `getAdminRegionScopeName(long handle)` returning the scope region's name (parent when expansion applies, else the resolved region); declare in `OSMScoutClient.java`. Verify: build passes; method returns the scope name for a resolved handle.

## 3. Tests

- [x] 3.1 ViewModel uses `getAdminRegionScopeName` for the search-panel label (fallback to `getAdminRegionName`); region resolution has no fix-age cap — the last known position scopes the search however old the fix, re-resolving only on movement beyond the threshold. Existing `MapCanvasViewModelAdminRegionTest.kt` covers handle passing, `NO_ADMIN_REGION` fallback, handle reuse/re-resolution, region-name display, and old-fix resolution. Native scope behavior is verified by the host test (3.3), the build (4.1), and the manual smoke test (4.2). Verify: `./gradlew :app:testMobileDebugUnitTest` passes.
- [x] 3.2 Update `FakeOSMScoutClient.kt` with `getAdminRegionScopeName` (no existing signature changes). Verify: full unit test suite passes.
- [x] 3.3 Host unit test `app/src/test/cpp/search_scope_test.cpp` for the pure scope decision in `search_scope.h`: depth normalization, cap boundary (parent at cap 5 expands, coarser does not), unknown level never expands. Verify: `g++ -std=c++17 -I app/src/main/cpp/libosmscout/libosmscout-client-java/src app/src/test/cpp/search_scope_test.cpp -o /tmp/search_scope_test && /tmp/search_scope_test` prints ALL TESTS PASSED.

## 4. Verification

- [x] 4.1 Build the app: `./gradlew :app:assembleMobileDebug` succeeds.
- [x] 4.2 Manual smoke test on device/emulator: with GPS fix in a kreisfreie Stadt (e.g. Dortmund), search an incomplete address that exists in a neighboring town of the same Regierungsbezirk (e.g. Bergkamen) → result appears; search a query whose match is only in a state-level scope → not included; search panel shows the scope region name (e.g. "Regierungsbezirk Arnsberg"). Verify via `adb logcat -s NaviVeylin` search logs and visible results.
