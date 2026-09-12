# Tasks: Show Operator and Brand in POI Search Results

## 1. Stylesheet (spec: poi-search — POI results list)

- [x] 1.1 Add `Operator, Brand` to the feature list of every POI-category type in `app/src/main/cpp/libosmscout/stylesheets/map.ost` (amenity_atm, amenity_fuel(+_building), amenity_bank, amenity_restaurant(+_building), amenity_fast_food(+_building), grocery shop types, tourism types (+_building variants), amenity_parking, amenity_bicycle_parking, amenity_police, amenity_hospital(+_building), amenity_doctors, railway_station/halt/tram_stop/subway_entrance, amenity_bus_station, public_transport_platform); verify with a grep that every type listed in `PoiCategories.CATEGORY_TYPES` that exists in map.ost now carries both features and that `amenity_charging_station` is unchanged
- [x] 1.2 Verify the stylesheet still parses: run the import tooling or a stylesheet validation step (e.g. `Import` against a small extract or `osmscout` type dump) and confirm no "unknown feature" errors

## 2. JNI bridge — data fields (spec: poi-search — POI results list)

- [x] 2.1 Add public `operator` and `brand` String fields to `PoiEntry.java` (libosmscout-client-java submodule); verify `PoiEntryTest.java` gains cases for null defaults and populated values
- [x] 2.2 Extend the C++ `PoiEntry` struct and `BuildPoiEntry` in `OSMScoutClient.cpp` to read `OperatorFeatureValue` and `BrandFeatureValue` into the new fields (keep the existing `label` fallback unchanged); verify the JNI serialization block sets the two new fields
- [x] 2.3 Verify the submodule patch is minimal and additive: `git diff` in the submodule shows only PoiEntry.java + OSMScoutClient.cpp changes, no behavior change to existing fields

## 3. App UI — label composition (spec: poi-search — POI results list)

- [x] 3.1 In `PoiSearchPanel.kt` `PoiResultItem`, compose the primary text from `label` plus a parenthetical `(brand)` when brand is present and differs, else `(operator)` when operator is present and differs; keep "(unnamed)" only when label, operator, and brand are all empty; verify with new Compose unit tests in `PoiSearchPanelComposeTest.kt` covering: name+brand, name+operator, brand preferred over operator, no-name → brand/operator alone, name==brand/operator dedup, fully unnamed
- [x] 3.2 Verify the `:app` module compiles: `./gradlew :app:compileMobileDebugKotlin` (or the build-app skill) succeeds without errors

## 4. Verification

- [x] 4.1 Run the app unit test suite (`./gradlew test` or the run-tests skill) and verify all tests pass, including the new PoiEntry and Compose label tests and the existing POI search tests
- [x] 4.2 Re-import a test database with the updated `map.ost`; on-device check (emulator): search ATMs, fuel, restaurants, and supermarkets and verify entries show name + brand/operator per the spec scenarios; verify "(unnamed)" only for objects with no name/operator/brand
- [x] 4.3 On-device check with an OLD database (imported before this change): verify the app runs without crash and entries behave as before (no operator/brand shown); verify map rendering is unchanged and database size delta after re-import is negligible
