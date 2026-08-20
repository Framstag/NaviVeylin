# POI Search — Design

## Context

NaviVeylin has no POI search. The JavaScout demo implements it (menu item → `PoiSearchOverlay` → `client.searchPOIs(category, lat, lon, radius, limit)` → result list → description dialog); see `proposal.md` for motivation. NaviVeylin already ships the native side of the bridge:

- C++ JNI `Java_com_framstag_libosmscout_client_OSMScoutClient_searchPOIsByTypes` exists in the submodule (`libosmscout-client-java/src/OSMScoutClient.cpp`).
- `PoiEntry` and `PoiCategories` are compiled into the `:osmscout-client-java` jar from submodule sources (they are not in the local-exclude list).
- What is missing: the Java declarations on the **local** `OSMScoutClient.java` (repo-root `osmscout-client-java`, which overrides the submodule client for Android-compatible HTTP/library loading). It currently exposes no `searchPOIs`/`PoiEntry`.

App patterns to reuse: search state + details-sheet flow live in `MapCanvasViewModel`/`MapCanvasUiState`; `SearchPanel` shows the M3 result-list style; `LocationDetailsSheet` renders `ObjectDescription` sections and the Route action.

## Goals / Non-Goals

**Goals:**
- Add POI search accessible from the map menu, matching JavaScout behavior/APIs with the deviations specified in the proposal (no preselected category, no preload, double-click details, action closes both sheets).
- Keep the native bridge untouched (C++ already compiled); only add the missing Java façade.
- Reuse existing result-list styling, details sheet, and distance formatting.

**Non-Goals:**
- New POI categories beyond the existing `PoiCategories` map (hotels, restaurants, grocery) — the map is hardcoded in `PoiCategories`, shared with JavaScout.
- POI markers on the map, POI search history, or POI favorites integration beyond what `LocationDetailsSheet` already offers.
- Changes to JavaScout itself.

## Decisions

### D1: Bridge — add Java façade only
Add to local `OSMScoutClient.java`:
- `public native PoiEntry[] searchPOIsByTypes(String[] typeNames, double lat, double lon, double radiusMeters, int limit)`
- `public PoiEntry[] searchPOIs(String category, double lat, double lon, double radiusMeters, int limit)` — resolves `PoiCategories.getTypeNames(category)` and delegates (same shape as the submodule client, so JNI symbol names match the existing C++ implementation).

Rationale: no native rebuild or submodule bump needed; `PoiEntry`/`PoiCategories` already on the classpath.
Alternatives: sync the full submodule `OSMScoutClient.java` — rejected, it would override the local Android-specific HTTP/library-loading variants. Copy `PoiEntry`/`PoiCategories` into the local module — rejected, duplication; submodule classes already ship in the jar.

### D2: State in MapCanvasViewModel / MapCanvasUiState
Add: `poiSearchOpen: Boolean`, `poiCategory: String?` (null = none selected), `poiRadiusMeters: Double`, `poiResults: List<PoiEntry>`, `isPoiSearching: Boolean`, `poiSearchError: String?`.
ViewModel functions: `openPoiSearch()`, `closePoiSearch()`, `onPoiCategorySelected()`, `onPoiRadiusChanged()`, `performPoiSearch()`, `onPoiEntryDoubleClick()`, `onPoiEntryClick()`.

Rationale: results + selection must survive the details-sheet round trip (spec: plain dismiss keeps POI search open with results), and the details flow already lives in this ViewModel (`onSearchResultSelected`). VM state is unit-testable with the existing `FakeOSMScoutClient` pattern.
Alternatives: separate `PoiSearchViewModel` — rejected, needs cross-VM wiring like `RoutePanelViewModel` for a single feature; local composable state like `SearchPanel`'s flag — rejected, does not survive the details-sheet round trip and is not testable.

### D3: One bottom sheet at a time
Double-clicking a POI closes the POI sheet and opens `LocationDetailsSheet`; results stay in the VM. Details dismiss → reopen the POI sheet (instant restore). Route/Show action → details closes, POI sheet stays closed, route flow / map centering proceeds.

Rationale: two stacked `ModalBottomSheet`s fight over scrims, gestures, and back handling (M3 anti-pattern). Closing/reopening is invisible to the user because the sheet content is stateless render of VM state.
Alternatives: stack both sheets — rejected (double scrim, gesture/back conflicts). Keep POI sheet open behind details — same stacking problem.

### D4: Category picker without preselection
M3 `FilterChip` row (Hotels / Restaurants / Grocery), all unselected on open; the Search button is disabled until exactly one chip is selected. Radius: `Slider` over the shared steps `{500, 1000, 2000, 5000, 10000, 20000, 50000, 100000}` m (up to 100 km), default 5 km, live label `formatDistanceKm`. No search runs on open and none runs on chip/slider change — the user presses Search.

Rationale: chips give one-tap selection, trivially express the no-selection state, and are M3-idiomatic. Radius default is not a "category", so a default is allowed by the spec (only category preselection is forbidden).
Alternatives: `ExposedDropdownMenu` like the favorites group picker — rejected, dropdowns are heavier for a 3-option picker and the empty-state placeholder is clumsier. Auto-search on chip select — rejected, contradicts "no preloading"/explicit search trigger and would spam the native call per tap.

### D5: Single click opens details and centers the map
Result rows use a plain `clickable`: a single click opens the details sheet and centers the map on the POI. Zoom is set to fit both the current location and the POI when a GPS fix exists (bbox over both points expanded by ~30%, `computeAreaZoom`, clamped to 4–20); without a fix the map centers on the POI at the current zoom. The POI is marked with the native search-selection marker (`MapRenderer.setSearchSelected`, rendered via `renderWithRouteAndPois`); the current location keeps the existing Compose GPS-marker overlay. The map is only recentered by selection and by the explicit "Show" action; there is no double-click handler.

### D6: Async search on Default dispatcher
`performPoiSearch()` runs `client.searchPOIs(...)` (blocking JNI) inside `viewModelScope.launch` + `withContext(Dispatchers.Default)`, same pattern as `onSearchResultSelected`'s `getDescription` call. Keep a reference to the in-flight `Job`; cancel it on re-search and on sheet close. `MAX_RESULTS = 100` (JavaScout constant). On failure set `poiSearchError` (shown in the sheet) — no crash.

### D7: Details flow, markers, and viewport restore
`onPoiEntryClick(entry)` (single click): copy `PoiEntry` → `LocationEntry` (label/lat/lon), set `selectedLocation`, set the search-selection marker via `mapRenderer.setSearchSelected`, center the map on the POI and fit both current location and POI (D5), fetch `client.getDescription(lat, lon, magnification)` off-main (existing `onSearchResultSelected` pattern), set `objectDescription` + `showDetailsSheet`, close POI sheet. `openPoiSearch()` snapshots the current viewport; `closePoiSearch()` (explicit sheet close) restores center/zoom, clears the search-selection marker (`clearSearchSelected`) and re-renders. Plain details dismiss reopens the POI sheet without restoring (snapshot kept). Route action reuses `openRoutePanelWithStart(selectedLocation)` and does not restore; the "Show on map" action (`showOnMap`) closes the details sheet, keeps the POI sheet closed, and leaves the map centered on the POI with its marker.

## Risks / Trade-offs

- [Native search latency on large radius/dense areas] → run on `Dispatchers.Default`, cancel in-flight job on new search/close, cap at 100 results; sheet shows a progress indicator.
- [POI type names missing from a map's TypeConfig (import-time stylesheet)] → `PoiCategories` types already match the project stylesheet; a map without them yields empty results, shown as the empty state, not an error.
- [Double-tap is slower on touch] → accepted UX from the requirement; single-tap pan gives immediate feedback so double-tap is discoverable.
- [`getDescription` returns null at node POIs] → details sheet renders without a description section (spec scenario covers this); the sheet still shows label/coords.
- [FakeOSMScoutClient must mirror new methods] → extend it with a configurable `poiResults` stub; Robolectric tests follow the existing classloader rule (stub .so loads once per JVM).

## Migration Plan

Additive feature; no schema/migration changes. Rollback = revert the change. The Java façade addition is binary-compatible with the existing native library (new native methods only).

## Open Questions

None — decisions deferred to design are resolved above; remaining unknowns (exact radius default, marker styling) are below spec level and can be tuned in implementation without touching specs or task breakdown.
