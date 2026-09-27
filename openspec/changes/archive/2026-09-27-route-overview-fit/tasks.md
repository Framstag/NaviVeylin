# Route Overview Fit — Tasks

Parent spec: `route-map-overview` (all requirements R1–R3 below).

## 1. Core Implementation

- [x] 1.1 Add optional `minZoom: Double = MIN_AREA_ZOOM` parameter to `computeAreaZoom` in `MapCanvasViewModel.kt` (default keeps favorites behavior unchanged) and verify existing favorites/zoom tests still pass (`./gradlew :app:testDebugUnitTest`)
- [x] 1.2 Add `fitViewportToRoute` helper + `lastFittedResult` field in `MapCanvasViewModel.kt`: bbox over polyline + start/dest (NaN-skipping), `computeAreaZoom(bbox, screenWidth, screenHeight, minZoom = MIN_MAG, dpi = projectionDpi)` (or `NODE_ZOOM` for degenerate), center + magnification applied via existing viewport state, `renderMap()`; no-usable-coords and unknown-canvas-size paths leave the viewport untouched (spec R1 calc scenario, R3). `computeAreaZoom` also gained the ground-resolution correction (display DPI + Mercator `cos(lat)`, design Decision 3) and a projected-bbox verification (design Decision 8) so the overview can never clip the route
- [x] 1.3 Wire the collector in `setRoutePanelViewModel`: after `mapRenderer.setRoute(...)`, fit only when `result != lastFittedResult` AND not navigating AND follow mode off; update `lastFittedResult` on fit and reset to `null` in the `clearRouteSignal` collector (spec R1 stale scenario, R2 driving suppression)
- [x] 1.4 Disable follow mode on destination selection (spec R1 — "Route calculated from a favorite while free-driving"): add `followMode = false` to `onFavoriteSelected` and `openRoutePanelWithStart` in `MapCanvasViewModel.kt`, mirroring `onSearchResultSelected`/`onPoiEntryClick`/`onAddressBookResultSelected` (Decision 7)
- [x] 1.5 Sheet-aware fit + settle delay (spec R1 — visible map area, Decision 8):
  - [x] `RoutePanel` reports covered height in px from the Material3 sheet offset (`requireOffset()`; `SheetState.offset` is internal) through `RoutePanelViewModel.sheetCoveredHeightPx` (a `StateFlow<Int>`, distinct-until-changed, valid during partial drag); `MapCanvasViewModel` consumes it; the canvas height comes from `MapCanvasScreen` (`canvasHeightPx`)
  - [x] `fitViewportToRoute` uses `effectiveHeight = canvasHeight - coveredPx` for the magnification and moves the camera so the bbox midpoint lands on the visible-area center (`coveredPx / 2` px down in screen terms, projected with `ProjectionUtils` at the display DPI + viewport angle); `coveredPx == 0` keeps today's behavior, `coveredPx >= canvasHeight` skips the fit
  - [x] Collector marks `lastFittedResult` at arrival and applies the fit after ~150 ms settle via a cancellable job; before applying re-check guards + viewport snapshot unchanged (no gesture in the window); a cleared route cancels the pending fit

## 2. Unit Tests

- [x] 2.1 Create `MapCanvasViewModelRouteFitTest.kt` mirroring the `MapCanvasViewModelRouteVisibilityTest.kt` harness (Robolectric, `FakeOSMScoutClient`, real `MapRenderer`, `setScreenSize`) with test: route calculation fits viewport center/magnification to the route bbox (spec R1 — "Route calculation shows the whole trip")
- [x] 2.2 Test: long trip (route spanning > area-favorites floor) zooms below 14 and both endpoints stay in the bbox-derived magnification (spec R1 — "Long trip zooms out below the area-favorites floor")
- [x] 2.3 Test: re-subscribing the collector with an unchanged result (user-moved viewport) does not re-fit (spec R1 — "Stale route result does not re-fit")
- [x] 2.4 Test: navigating guard — with `NavigationViewModel` wired navigating, a new route result redraws without changing the viewport; same for follow mode on (spec R2 — reroute + restart scenarios)
- [x] 2.5 Test: empty polyline falls back to endpoint midpoint at `NODE_ZOOM`, and all-invalid coordinates leave the viewport untouched (spec R3 — both scenarios)
- [x] 2.6 Verify the full route visibility suite still passes: `./gradlew :app:testDebugUnitTest --tests "com.naviveylin.ui.map.*" --tests "com.naviveylin.ui.route.*"`
- [x] 2.7 Harness fix: wire a real `NavigationViewModel` into `MapCanvasViewModelRouteFitTest.kt` (current harness leaves `?.state == null`, so guard branches were never exercised). Tests: no fit while navigating (reroute scenario, plus a panel calculation while navigating), no fit while follow mode on for a non-selection calculation (spec R2), and navigation starting inside the settle window cancels the pending fit
- [x] 2.8 Test: fav-select flow — with follow mode on, `onFavoriteSelected` disables follow and the resulting route fits (spec R1 — favorite-while-free-driving scenario); same follow disengagement asserted for `openRoutePanelWithStart`
- [x] 2.9 Tests: sheet-inset — covered-height flow makes the fit use the reduced effective height and shifted center (bbox midpoint on the visible-area center, both endpoints above the panel's top edge, asserted through `ProjectionUtils`); settle window — a viewport change during the window cancels the pending fit; `coveredPx == canvasHeight` skips

## 3. Build & On-Device Verification

- [x] 3.1 Build both flavors compile cleanly: `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug -Pandroid.injected.build.abi=arm64-v8a` (no new warnings)
- [x] 3.2 On-device: calculate a 30–50 km route in the route panel — start marker, target marker, and polyline all visible **above the open panel** (visible-area fit, Decision 8); pan/zoom then rotate the device (screen recreation) — user-moved viewport kept, no re-fit (spec R1)
- [x] 3.3 On-device: start navigation — camera follows GPS as before; trigger a reroute mid-drive (simulated off-route) — camera stays on the driver, new route drawn over current viewport (spec R2)
