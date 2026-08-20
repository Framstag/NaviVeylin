# POI Search — Tasks

Spec: `specs/poi-search/spec.md` · Design: `design.md`

## 1. JNI Bridge Façade

- [x] 1.1 Add native method declaration `searchPOIsByTypes(String[] typeNames, double lat, double lon, double radiusMeters, int limit)` to local `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` (spec: POI results list; design D1 — C++ impl already exists, symbol name must match)
- [x] 1.2 Add `searchPOIs(String category, double lat, double lon, double radiusMeters, int limit)` wrapper resolving `PoiCategories.getTypeNames(category)` and delegating to `searchPOIsByTypes`, returning empty array on unknown category/invalid radius (spec: POI results list; design D1)
- [x] 1.3 Verify `PoiEntry` and `PoiCategories` resolve from the `:osmscout-client-java` jar (submodule sources, not excluded) — no new files needed (design D1)

## 2. ViewModel State and Search Logic

- [x] 2.1 Extend `MapCanvasUiState` with `poiSearchOpen`, `poiCategory: String?`, `poiRadiusMeters`, `poiResults: List<PoiEntry>`, `isPoiSearching`, `poiSearchError` (spec: category/radius selection; design D2)
- [x] 2.2 Implement `openPoiSearch()`/`closePoiSearch()` with `poiCategory` reset to null and no search triggered on open (spec: no category preselected and no preloaded results)
- [x] 2.3 Implement `onPoiCategorySelected(category)`/`onPoiRadiusChanged(radius)` updating state without triggering a search (spec: no preloaded results)
- [x] 2.4 Implement `performPoiSearch()` running blocking `client.searchPOIs` on `Dispatchers.Default`, cancelling the previous in-flight job, capping at 100 results, setting `poiResults`/`isPoiSearching`/`poiSearchError` (spec: category/radius selection, POI results list; design D6)
- [x] 2.5 Implement `onPoiEntryClick(entry)` — open details without changing the map center (spec: details via single click without recentering; design D5)
- [x] 2.6 Implement `onPoiEntryClick` details flow — copy to `LocationEntry`, set `selectedLocation`, fetch `client.getDescription` off-main, set `objectDescription` + `showDetailsSheet`, close POI sheet (spec: details via single click; design D7)
- [x] 2.7 Implement details-dismiss handling — plain dismiss reopens POI sheet with results intact; Route/Show action keeps it closed (spec: selective action closes both dialogs; design D3)

## 3. UI

- [x] 3.1 Create `PoiSearchPanel` composable (`ui/map/`): M3 `ModalBottomSheet` with title, `FilterChip` category row (none selected initially), radius `Slider` with steps 500/1000/2000/5000/10000/20000 m defaulting to 5000, Search button disabled until category selected, progress/empty/error states (spec: category/radius selection, POI results list; design D4)
- [x] 3.2 Render result rows matching the existing result-list style — label, object type, distance right-aligned (`PoiEntry.distance` via `formatDistanceKm`), dividers (spec: POI results list)
- [x] 3.3 Result rows open details on single click (`clickable`); selection does not recenter the map; double-click removed (spec: details via single click; design D5)
- [x] 3.4 Add "Search POIs" item to the map menu in `MapCanvasScreen.kt` opening the POI sheet (spec: POI search accessible from the map menu)
- [x] 3.5 Wire POI sheet + details sheet interplay in `MapCanvasScreen.kt`: click closes POI sheet and opens `LocationDetailsSheet`; dismiss reopens; Route/Show callbacks keep POI sheet closed and start route via `openRoutePanelWithStart` (spec: selective action closes both dialogs; design D3/D7)
- [x] 3.6 String resources for new UI strings (menu item, category labels, search button, radius label, empty/error states) in `res/values/strings.xml`

## 4. Tests

- [x] 4.1 Extend `FakeOSMScoutClient` with `poiResults` stub overriding `searchPOIs` (and `searchPOIsByTypes`) (design D1; classloader rule: any test instantiating the fake MUST run under `@RunWith(RobolectricTestRunner::class)` with default sandbox config)
- [x] 4.2 ViewModel unit tests: open state has no category/no results, search disabled without category, `performPoiSearch` populates results, failure sets error, click sets selected location + fetches description without recentering, dismiss keeps results (spec: no preselection, category/radius, results list, single-click details)
- [x] 4.3 Compose UI test for `PoiSearchPanel`: no preselected chip, Search disabled until category chosen, single click opens details (spec: no preselection, single-click details)
- [x] 4.4 Compose UI test: Route action in details closes details AND POI sheet (spec: selective action closes both dialogs)

## 5. Verification

- [x] 5.1 Run `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` — builds without errors
- [x] 5.2 Run `./gradlew test` — existing tests still pass
- [x] 5.3 Run `./gradlew :app:testDebugUnitTest` (or full-suite run) to confirm the JNI stub classloader rule still holds with new POI tests

## 6. Follow-up: 100 km radius, fit zoom, markers, viewport restore

- [x] 6.1 Extend `POI_RADIUS_STEPS_M` to `{500, 1000, 2000, 5000, 10000, 20000, 50000, 100000}` (spec: search radius up to 100 km; design D4)
- [x] 6.2 `onPoiEntryClick` centers the map on the POI and sets zoom to fit current location + POI via `computeAreaZoom` (bbox + ~30% margin), current-zoom fallback without GPS (spec: details via single click; design D5)
- [x] 6.3 Set POI marker via `mapRenderer.setSearchSelected(lat, lon)` on selection; clear via `clearSearchSelected` on POI-sheet close; current-location marker stays the existing GPS overlay (spec: markers; design D5)
- [x] 6.4 `openPoiSearch` snapshots the viewport; `closePoiSearch` restores center/zoom, clears the POI marker and re-renders; plain details dismiss does not restore (spec: viewport restored when POI search closes; design D7)
- [x] 6.5 Tests: 100 km radius selectable, selection centers + fits zoom (with and without GPS), marker set/cleared, viewport restore on close (spec: radius, details, restore)
- [x] 6.6 Re-run `./gradlew test` and arm64 `assembleDebug`
- [x] 6.7 Marker tests: `FakeOSMScoutClient` records search-sel from `renderWithRouteAndPois`; `MapRendererSmokeTest` asserts `setSearchSelected` forwards and `clearSearchSelected` resets (spec: markers)
- [x] 6.8 Extract shared `MapMenu` composable (dedupe landscape/portrait menu) + `MapMenuComposeTest` verifying "Search POIs" opens POI search (spec: menu entry)
- [x] 6.9 VM test: changing category/radius keeps prior results and does not auto re-search (spec: category/radius selection)
