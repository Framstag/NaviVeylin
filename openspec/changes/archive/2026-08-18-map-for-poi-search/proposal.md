# POI Search Result Map

## What Changes

The POI search result sheet currently shows only a filterable result list. This change embeds a live interactive map directly into the POI search sheet so the user can see where results are located relative to each other and to their own position:

- Portrait: map shown above the result list.
- Landscape: map shown to the left of the result list.
- The map shows the current position (when a GPS fix is available) and markers for every search result.
- Selecting a result updates the map: the panel map highlights the selection, and the main map centers on the result (existing behavior retained).
- The location details dialog (opened from a POI result) also shows the current position on its mini map when available.

The embedded map reuses the existing self-contained mini map widget (`MiniMap`), extended to support multiple markers and an optional current-position marker.

## Capabilities

### New Capabilities

None — behavior extends existing capabilities.

### Modified Capabilities

- `poi-search`: The results sheet SHALL embed an interactive map showing the location of all results and the current position (when available); the map is placed above the result list in portrait and left of it in landscape; selecting a result updates both the embedded map's selection and the main map.
- `mini-map`: The widget SHALL support rendering multiple object markers and an optional current-position marker, so the same widget serves the POI results sheet and the details dialog.
- `enhanced-details-sheet`: The details dialog's mini map SHALL also show the current position when a GPS fix is available.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/PoiSearchPanel.kt` — restructure layout (portrait/landscape split via `BoxWithConstraints`), embed map, pass result markers + current position + selection callback.
- `app/src/main/java/com/naviveylin/ui/map/MiniMap.kt` — extend to accept a list of markers and an optional current-position marker (distinct style: e.g. primary-colored pins for results, blue dot + accuracy for GPS); keep existing single-marker behavior working for other callers.
- `app/src/main/java/com/naviveylin/ui/map/LocationDetailsSheet.kt` — accept and render the current position on the mini map.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` / `MapCanvasViewModel.kt` — feed `gpsLocation`, `poiResults`, and selection state into the sheet; selection already centers the main map (kept).
- No JNI/native changes: `PoiEntry` already carries `lat`/`lon`; the mini map's renderer already handles arbitrary viewports.
