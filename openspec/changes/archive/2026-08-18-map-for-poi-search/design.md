# Design: POI Search Result Map

## Context

The POI search sheet (`PoiSearchPanel`, a `ModalBottomSheet`) currently shows a category filter, radius slider, and a result list. The main map already centers on a POI and opens the details dialog on result click (`MapCanvasViewModel.onPoiEntryClick`), and restores its viewport snapshot when the sheet closes.

An embeddable self-contained mini map widget (`MiniMap`, `ui/map/MiniMap.kt`) already exists: it owns its own `MapRenderer` + coroutine scope, renders north-up, supports drag pan + zoom buttons, and draws a single primary marker at `lat`/`lon`. It is already embedded in the details dialog (`LocationDetailsDialog`).

GPS state is available in `MapCanvasUiState` as `gpsMarkerLat`/`gpsMarkerLon` (`Double.NaN` when unavailable) and `gpsLocation: Location?`. Search results arrive as `List<PoiEntry>` with `label`, `lat`, `lon`, `distance`.

## Goals / Non-Goals

Goals:
- Embed a live interactive map in the POI search sheet showing all result markers + current position.
- Portrait: map above list; landscape: map left of list (reuse `BoxWithConstraints`).
- Selected result visually distinguished on the embedded map; main-map centering behavior unchanged.
- Details dialog mini map additionally shows current position when fix available.
- No JNI/native changes; no new renderer infrastructure.

**Non-Goals:**
- No tappable markers on the embedded map (selection stays list-driven).
- No change to search logic, radius/category controls, or the main map's renderer.
- No change to sheet-close viewport restore semantics.
- No change to the existing single-click → details flow.

## Decisions

### D1: Extend `MiniMap` instead of writing a new map widget
`MiniMap` already encapsulates renderer lifecycle, pan/zoom, north-up rendering, and marker drawing. Extend it with two additive optional parameters:
- `additionalMarkers: List<Pair<Double, Double>>` (or a small data class with lat/lon + selected flag) — drawn with the existing pin style but slightly smaller/secondary color; the primary marker keeps the existing distinct style.
- `currentPosition: Pair<Double, Double>?` — drawn as a blue dot with accuracy-style circle (mirror the main map's GPS marker visuals), hidden when null.

Existing callers (details dialog) pass only lat/lon/initialMag — signature stays source-compatible. This preserves `mini-map` spec ("Reusable embeddable widget").

### D2. Panel map state is stateless and viewport-independent
`PoiSearchPanel` gets two new params: `currentPosition: Pair<Double, Double>?` (from `state.gpsMarkerLat/Lon`, NaN → null) and `selectedPoi: PoiEntry?` (local `remember` in the panel, reset on new search / sheet reopen). The embedded map's renderer is created/destroyed by `MiniMap` itself — no `MapCanvasViewModel` viewport involvement, so the main map's viewport snapshot/restore logic is untouched.

Selection highlight: `onEntryClick` still routes through the existing ViewModel handler (closes sheet → opens details → centers main map). The panel keeps a `selectedIndex` remember-state to paint the highlight for the duration the sheet stays visible (e.g. re-opened sheet).

### D3. Fit the embedded map to the results
On first render after a search, center the embedded map on the search center (the map center at search time, already captured as `lat`/`lon` in `performPoiSearch`) at a magnification that fits the result bounding box + current position with margin. Reuse `computeAreaZoom` (same fit logic as `poiFitMagnification`) over the bbox of non-empty results; fall back to a radius-derived magnification when results are empty. The panel is stateless: pass `initialLat/initialLon/initialMag` computed by the caller (`MapCanvasScreen` or a small helper in the panel) — `MiniMap` already re-renders when these change.

### D4. Portrait/landscape layout with `BoxWithConstraints`
Inside the sheet content, use `BoxWithConstraints`: when `maxWidth > maxHeight` (landscape) lay out `Row { map(weight 1f); list(weight 1f) }`; otherwise `Column { map(fixed fraction ~40% or weight); list(weight 1f) }`. Keep the sheet at `fillMaxWidth` in landscape so the map gets real width (default `ModalBottomSheet` max width 640 dp would starve the map on tablets). Map height in portrait: `heightIn(max = 40%)`-style constraint via weight.

### D5. Details dialog: pass current position to the embedded mini map
`LocationDetailsDialog` receives an optional `currentPosition: Pair<Double, Double>?`; forwarded to its `MiniMap`. `MapCanvasScreen` passes `state.gpsMarkerLat/Lon` (null when NaN). No other detail-dialog changes.

### D6. No native changes
`PoiEntry` already carries `lat`/`lon`; the mini-map renderer already projects arbitrary viewports. Keep JNI untouched.

## Risks / Trade-offs

- **Render cost**: each embedded mini map is a second `MapRenderer` sharing the client's `dbThread` with the main map. POI panel + main map = 2 renderers; acceptable (details dialog already does this), but heavy panning on the panel map can delay main-map renders. Mitigation: keep the panel map's zoom buttons + drag only; no continuous follow rendering.
- **Bottom-sheet drag vs. map drag**: the embedded map consumes drag gestures inside its bounds, which can reduce the sheet's drag-to-resize area in portrait (map occupies top region). Accepted; sheet still resizes via handle/bottom area.
- **Marker legibility in dark mode**: result pins and selection highlight must use `MaterialTheme.colorScheme` colors; selection highlight needs a distinct color (e.g. `tertiary`) that reads in both themes.
- **Fit zoom edge cases**: results far from the current position produce a wide bbox → low magnification; acceptable, matches `poiFitMagnification` semantics.
