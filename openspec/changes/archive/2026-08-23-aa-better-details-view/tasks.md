# aa-better-details-view — Tasks

## 1. MapScreen: optional initial center

- [x] Add `initialCenter: Pair<Double, Double>? = null` constructor param to `MapScreen` (spec: auto-destination-details — "Show action displays destination on map")
- [x] `computeInitialViewport()` returns `initialCenter` at `DEFAULT_AA_ZOOM` when set, before the saved-viewport / map-bbox fallbacks
- [x] Update `MapScreenTest` for the new param (existing tests compile unchanged; add a test that `initialCenter` wins over saved viewport)

## 2. DetailsScreen: labeled rows + title

- [x] Rework `buildDetailsRows` to emit labeled rows in priority order: "Coordinates" (always), "Address" (street + house number), "Area" (admin region, else postal area), then description entries (label/value) up to the 4-row cap (spec: auto-destination-details — "Details screen shows labeled attribute rows")
- [x] Add title resolution: description `General/Name` entry → resolved address (street + house number, else admin region) → `nameHint` → "Location"; use it for the pane header title (spec: auto-destination-details — title scenarios)
- [x] Update `DetailsScreenTest`: labeled-row assertions, title fallback chain (name → address → label → generic), coordinates row always present

## 3. DetailsScreen: map preview

- [x] Switch `onGetTemplate()` from `PaneTemplate` to `MapWithContentTemplate` via `MapTemplateFactory.buildTemplate` with the pane as content (spec: auto-destination-details — "Details screen shows map preview")
- [x] Add `SurfaceCallback` registration mirroring `MapScreen`'s lifecycle pattern (register on start, release on stop, unregister on destroy, capped `invalidate` surface-failure recovery)
- [x] Own `AutoMapRenderer` centered on `(lat, lon)` at `mag`; on surface available set `client.setMapDpi(surfaceDpi)` + `renderer.updateProjectionDpi(surfaceDpi)`; call `renderer.setDestinationMarker(lat, lon, name)` (spec: auto-destination-details — map preview + destination marker)
- [x] No gesture callbacks on the preview surface (static preview per design decision 2)

## 4. DetailsScreen: actions

- [x] Add "Show" secondary action; keep "Navigate here" as `FLAG_PRIMARY` (spec: auto-destination-details — "Show action displays destination on map")
- [x] "Show" handler: `screenManager.popToRoot()` then `push(MapScreen(carContext, navigationViewModel, initialCenter = lat to lon))`
- [x] Remove "Clear" action, `showClear`/`onClear` params, and `MapScreen.clearSelection()` + write-only selection state (design decision 4)
- [x] Update call sites: `MapScreen.onLocationSelected` (drop `showClear`/`onClear`), `SearchScreen`, `PoiResultsScreen` (unchanged args otherwise)
- [x] Update `DetailsScreenTest`: pane has exactly 2 actions ("Navigate here" + "Show"), no "Clear"; "Show" invokes its callback; `MapScreenTest` updated for removed `clearSelection`

## 5. Verify

- [x] `./gradlew :auto:testDebugUnitTest` — all `:auto` unit tests pass (spec: all)
- [x] `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` — build compiles (spec: all)
- [x] Manual check on AAOS emulator: details screen shows map preview + destination marker, labeled rows, title from destination, "Show" centers browse map, "Navigate here" starts navigation, back returns without navigating (spec: auto-destination-details)
- [x] If pane actions do not render inside `MapWithContentTemplate` content on the emulator, apply the `setActionStrip` fallback (design decision 7) and re-verify

## 6. Review feedback (current position + Show fix)

- [x] Fix "Show" bug: `MapScreen.applySettings` must not re-center on GPS (follow mode) when `initialCenter` is set — the map stays on the requested destination (spec: auto-destination-details — "Show action displays destination on map")
- [x] DetailsScreen: observe `locationProvider.position()` and draw the GPS marker on the preview (spec: auto-destination-details — "Details screen shows current position")
- [x] DetailsScreen: when destination + GPS fix both known, re-center on the midpoint and zoom so both are visible; without a fix stay centered on the destination at `mag` (spec: auto-destination-details — "Details screen shows current position")
- [x] Spec delta updated with current-position + fit-zoom scenarios
- [x] `./gradlew :auto:testDebugUnitTest` — all `:auto` unit tests pass after review changes
- [x] `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` — build compiles after review changes

## 7. Review feedback round 2 (centering, Show follow mode, favorites)

- [x] Center the destination in the visible map area: pane panel covers ~40% of the surface on the left (user-confirmed), so the viewport center is offset so the destination projects to the visible-area center (~70% width); tunable `PANE_FRACTION` constant, RTL hosts mirror the panel to the right (spec: auto-destination-details — "Details screen shows map preview")
- [x] Fix "Show" still showing current location: `AutoMapRenderer` defaults `followMode = true`, so every GPS fix re-centered the viewport; new `initialFollowMode` param, `MapScreen` passes `false` when `initialCenter` is set (spec: auto-destination-details — "Show action displays destination on map")
- [x] "Show" zoom continuity: `MapScreen` gains `initialZoom`, the details screen passes its `mag` instead of the fixed `DEFAULT_AA_ZOOM` (spec: auto-destination-details — "Show action displays destination on map")
- [x] Favorites on the details preview: observe `favoritesProvider.favoriteLocations()` and set markers (spec: auto-destination-details — "Details screen shows favorites")
- [x] Spec delta updated with visible-area centering + favorites scenarios
- [x] `./gradlew :auto:testDebugUnitTest` — all `:auto` unit tests pass after round-2 changes
- [x] `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` — build compiles after round-2 changes

## 8. Review feedback round 3 (Show map: marker + visible-area centering)

- [x] "Show" browse map draws a destination marker (with name) so the destination is visible — the browse map previously drew none, so the destination was unverifiable (spec: auto-destination-details — "Show action displays destination on map")
- [x] "Show" browse map centers the destination in the visible map area: the host's menu panel covers the left ~40% of the surface, so the viewport is offset via the shared `paneOffsetCenter` helper (new `PaneOffset.kt`, used by both `DetailsScreen` and `MapScreen`) (spec: auto-destination-details — "Show action displays destination on map")
- [x] Diagnostic log in `computeInitialViewport` for the Show flow (`Show map: initialCenter=..., zoom=...`) to verify the destination/zoom on the emulator
- [x] `./gradlew :auto:testDebugUnitTest` — all `:auto` unit tests pass after round-3 changes
- [x] `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a` — build compiles after round-3 changes
