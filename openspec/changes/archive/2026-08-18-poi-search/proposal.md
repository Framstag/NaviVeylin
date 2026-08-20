# POI Search

## What Changes

NaviVeylin currently has no POI search. The JavaScout demo (libosmscout) implements an initial POI search built on the `OSMScoutClient.searchPOIs` JNI API; NaviVeylin will port that feature using the same principles and APIs.

POI search is triggered from the existing map menu (new "Search POIs" menu item). The user picks a POI category (hotels, restaurants, grocery) and a search radius (up to 100 km); the app searches around the current map center and shows results in a Material 3 result list. Selecting a result opens the location details sheet, centers the map on the POI, and zooms so that the current location and the POI are both visible with markers; closing the POI search restores the previous map center and zoom. Choosing a selective action (route, show) there also closes the POI search sheet.

Deviation from JavaScout reference:
- JavaScout preselects the first category and auto-runs a search when the overlay opens. NaviVeylin preselects **no** category and does **not** preload results — the user must choose a category before the first search.
- JavaScout uses long-press to open the description; NaviVeylin shows details on single click, centers the map on the POI with a zoom that fits current location and POI, and restores the pre-search viewport when the POI search closes.
- In JavaScout the POI overlay stays open behind the description dialog. In NaviVeylin, closing the details sheet via a selective action (route, show) closes the POI search sheet as well.

## Capabilities

### New Capabilities
- `poi-search`: Search for points of interest around the current map center by category and radius, browse results in a list, open details, and hand off to routing or map display.

### Modified Capabilities
<!-- None: existing capabilities (location-search, search-free-text, enhanced-details-sheet) are not changing requirements. -->

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — add "Search POIs" menu item, POI sheet state flag, double-click result handling, details-sheet wiring with POI-sheet close-on-action.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` + `MapCanvasUiState` — POI search state (category, radius, results, searching flag), async `searchPOIs` call off the main thread, description fetch for double-clicked entry.
- New composable `PoiSearchPanel.kt` in `ui/map/` — Material 3 bottom sheet: category picker (no preselection), radius control, search trigger, result list styled like the existing `SearchPanel` result rows.
- Reused as-is: `OSMScoutClient.searchPOIs` / `searchPOIsByTypes`, `PoiEntry`, `PoiCategories` (JNI bridge, submodule), `LocationDetailsSheet` (details rendering + Route action), `DistanceFormat`/`formatDistanceKm`, `haversineDistanceMeters`.
- Tests: extend `FakeOSMScoutClient` with POI search fakes; Robolectric tests follow the existing classloader rule.
