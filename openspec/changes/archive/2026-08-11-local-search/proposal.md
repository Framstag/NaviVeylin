# Local Search — Admin-Region-Scoped Search via GPS Position

## What Changes

Currently `OSMScoutClient.searchLocations(query, limit)` performs an unconstrained free-text search: addresses and POIs only match when the user types the full qualifier chain (e.g. "Hauptstraße 12 Dortmund"), because libosmscout must locate the admin region from the search string alone.

libosmscout's `LocationStringSearchParameter` supports a *default admin region* (`SetDefaultAdminRegion`): when set, the search first resolves the admin region implied by the search string, and falls back to the default region if none is found. Addresses and POIs then match without the region qualifier (e.g. "Hauptstraße 12" suffices when the current region is known).

This change wires that feature into NaviVeylin's search:

- When a usable GPS fix is available (quality GOOD, fresh timestamp), the app resolves the admin region containing the current position and passes it to the search as the default admin region.
- Search queries are then scoped to that region as a fallback, so incomplete addresses/POI names still match.
- When no GPS fix is available (or it is too stale/inaccurate), search falls back to the current unconstrained behavior.
- The admin region is re-resolved on movement so the scope follows the user (with debouncing to avoid churn during a query session).
- The JNI bridge is extended with a coordinate→admin-region resolution and `searchLocations` accepts the resolved region. libosmscout core (`LocationService`, `LocationIndex`) already provides everything needed; no changes to the vendored core library are required.

## Capabilities

### New Capabilities

*(none)*

### Modified Capabilities

- `location-search`: Search queries SHALL be scoped by the current admin region when a usable GPS fix is available, so addresses and POIs match without a full region qualifier. New requirements cover: default-admin-region resolution from the GPS fix, fallback to unconstrained search without a fix, and re-resolution on significant movement.

## Impact

- **JNI bridge** (`app/src/main/cpp/libosmscout/libosmscout-client-java/`):
  - `OSMScoutClient.java`: extend `searchLocations()` with an admin region parameter (or add an overload)
  - `OSMScoutClient.cpp`: set `LocationStringSearchParameter::SetDefaultAdminRegion()`; add a native coordinate→admin-region resolver (`LocationService::VisitAdminRegions` + region geometry containment test via `MapService`)
- **ViewModel** (`MapCanvasViewModel.kt`): resolve current admin region from `locationService.location` GPS fix, cache with validity, pass into `searchLocations()` on each debounced query; re-resolve on movement (debounced)
- **Tests**: JNI test stubs/fakes (`FakeOSMScoutClient.kt`, host stub) updated for the new signatures; ViewModel search-scoping unit tests
- **No new dependencies**; no changes to vendored libosmscout core
- **Performance note**: admin-region resolution is a native index walk + geometry test, run off the main thread like existing searches; resolution is cached and re-run only when the position moved significantly
