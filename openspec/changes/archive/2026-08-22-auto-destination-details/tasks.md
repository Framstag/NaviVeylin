## 1. Core: destination in NavigationState

- [x] 1.1 Add `destLat`/`destLon` (default `Double.NaN`) and `destinationName` (default `null`) to `NavigationState` in `:core` and verify `./gradlew :core:testDebugUnitTest` passes with existing tests unchanged
- [x] 1.2 Add `navigateTo(destLat, destLon, destinationName)` overload to `NavigationViewModel` that records the name alongside coordinates, and verify existing `navigateTo` callers compile and unit tests pass

## 2. Shared DetailsScreen

- [x] 2.1 Extract the anonymous details screen from `MapScreen.kt` into `DetailsScreen.kt` (constructor: carContext, navigationViewModel, lat, lon, preloadedDescription, showClear) and verify `MapScreen.kt` uses the new class and `./gradlew :auto:testDebugUnitTest` passes
- [x] 2.2 Gate the "Clear" action behind `showClear` (map-tap flow only) as a pane-level button (PaneTemplate rows are non-actionable on car hosts — see design.md decision 1) and verify a unit test covers both flag values

## 3. Entry-point rewiring

- [x] 3.1 Change `SearchScreen` row tap to push `DetailsScreen` (passing the result label as name hint) instead of calling `navigateTo` directly, and verify `SearchScreenMapperTest`/`StartupScreensTest` are updated and pass
- [x] 3.2 Change `PoiResultsScreen` row tap to push `DetailsScreen` instead of calling `navigateTo` directly, and verify the POI flow test passes

## 4. Destination in navigation template

- [x] 4.1 Render a destination marker (pin + name label) on the navigation map surface via `AutoMapRenderer.setDestinationMarker` (car-app 1.7 `NavigationTemplate` has no marker API — see design.md decision 3) and verify `AutoMapRendererTest` covers named and unnamed destinations
- [x] 4.2 Make `NavigationScreen` read `destinationName`/`destLat`/`destLon` from `NavigationState` and push them to the renderer, and verify `NavigationScreenActionsTest` passes

## 5. Integration verification

- [x] 5.1 Add `DetailsScreen` unit tests (address shown, description shown, no-description fallback, "Navigate here" starts navigation, back does not navigate — structural: rows carry no click listeners, pane actions are exactly navigate/clear) and verify they pass
- [x] 5.2 Run `./gradlew :auto:testDebugUnitTest :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` and verify the full suite is green
