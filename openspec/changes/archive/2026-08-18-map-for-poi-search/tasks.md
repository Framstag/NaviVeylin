## 1. MiniMap extension
- [x] 1.1 Extend `MiniMap` with `additionalMarkers: List<Pair<Double, Double>>` param (default empty); draw each additional marker with the existing pin style in a secondary color, anchored to geo position, hidden when out of view; keep primary marker visually distinct (spec: mini-map Multiple object markers)
- [x] 1.2 Extend `MiniMap` with `currentPosition: Pair<Double, Double>?` param (default null); draw blue dot + accuracy-style circle when non-null, nothing when null (spec: mini-map Optional current-position marker)
- [x] 1.3 Verify existing callers compile unchanged (details dialog passes only lat/lon/initialMag); run `./gradlew :app:compileDebugKotlin`

## 2. POI panel map
- [x] 2.1 Add `currentPosition` and `selectedPoi` params to `PoiSearchPanel`; compute initial center/mag from search center + result bbox via `computeAreaZoom` (fallback radius-derived mag when no results) (design D2/D3)
- [x] 2.2 Embed the extended `MiniMap` in the sheet with `BoxWithConstraints`: portrait → map above list (`Column`), landscape → map left of list (`Row`), sheet `fillMaxWidth` in landscape (design D4, spec: poi-search POI results map)
- [x] 2.3 Pass all `PoiEntry` lat/lon as `additionalMarkers`; highlight `selectedPoi` marker distinctly (tertiary color); selection persisted in VM state (`poiSelectedLat/Lon`), reset on new search and sheet close (spec: poi-search Selection changes the maps)
- [x] 2.4 Wire `MapCanvasScreen`: pass `gpsMarkerLat/Lon` (NaN → null) and result markers from `state.poiResults`; main-map centering via existing `onPoiEntryClick` stays untouched (spec: poi-search Selection changes the maps)

## 3. Details dialog current position
- [x] 3.1 Add `currentPosition: Pair<Double, Double>?` param to `LocationDetailsDialog`; forward to its `MiniMap` (spec: enhanced-details-sheet Current position on details mini map)
- [x] 3.2 Pass `state.gpsMarkerLat/Lon` (null when NaN) from `MapCanvasScreen` details-dialog call site

## 4. Verification
- [x] 4.1 Add/extend Robolectric compose tests for `PoiSearchPanel`: portrait shows map above list, landscape shows map left of list, markers present, selection highlight set (follow classloader rule from AGENTS.md — `FakeOSMScoutClient` classes must run under default Robolectric sandbox)
- [x] 4.2 `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` and `./gradlew test` pass
