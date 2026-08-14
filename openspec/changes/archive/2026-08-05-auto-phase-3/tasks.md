## 1. Extract shared render utility

- [x] 1.1 Create `MapRenderUtil` object in `:app` with `renderToBitmap()` function: calls `client.renderWithRouteAndPois()` with viewport params + overlays, returns `Bitmap`
- [x] 1.2 Verify phone `MapRenderer` still works after extraction (build + existing tests pass)
- [x] 1.3 Add unit tests for `MapRenderUtil.renderToBitmap()` covering null/empty overlay cases

## 2. Create AutoMapRenderer

- [x] 2.1 Create `AutoMapRenderer` class in `:auto` package with render loop on `Dispatchers.Default`
- [x] 2.2 Implement `Surface` management: `onSurfaceCreated(Surface, int width, int height)`, `onSurfaceDestroyed()`
- [x] 2.3 Implement render loop: debounce viewport changes (100ms), call `MapRenderUtil.renderToBitmap()`, write to `Surface` via `lockCanvas()`/`unlockCanvasAndPost()`
- [x] 2.4 Expose `StateFlow<RenderRequest>` for viewport state (center lat/lon, zoom, rotation)
- [x] 2.5 Add `setGpsMarker(lat, lon, bearing, accuracy)` method
- [x] 2.6 Add `setFavoriteLocations(favorites: List<FavoriteLocation>)` method
- [x] 2.7 Add `setViewport(lat, lon, zoom, angle)` method
- [x] 2.8 Add `shutdown()` method to clean up render loop coroutine
- [x] 2.9 Write unit tests for `AutoMapRenderer` (mock `Surface`, verify render calls)

## 3. Create MapScreen

- [x] 3.1 Create `MapScreen` class extending `Screen` in `:auto` package
- [x] 3.2 Implement `onGetTemplate()` returning `MapWithContentTemplate` with `MapController`
- [x] 3.3 Wire `SurfaceCallback` to `AutoMapRenderer.onSurfaceCreated()`/`onSurfaceDestroyed()`
- [x] 3.4 Add lifecycle observer: start/stop render loop on screen visible/hidden
- [x] 3.5 Wire GPS position updates from `NavigationViewModel.state` to `AutoMapRenderer.setGpsMarker()`
- [x] 3.6 Wire favorites updates from `AutoFavoritesProvider` to `AutoMapRenderer.setFavoriteLocations()`
- [x] 3.7 Add zoom in/out `ActionStrip` actions for non-touch input
- [x] 3.8 Add re-center action (returns to GPS position, re-engages follow mode)
- [x] 3.9 Write unit tests for `MapScreen` (verify template structure, lifecycle)

## 4. Add map interaction gestures

- [x] 4.1 Wire `SurfaceCallback.onScroll()` to update viewport center → trigger re-render
- [x] 4.2 Wire `SurfaceCallback.onScale()` to update zoom level → trigger re-render
- [x] 4.3 Wire `SurfaceCallback.onClick()` for destination selection
- [x] 4.4 Implement follow mode: auto-center on GPS position, disengage on manual pan/zoom
- [x] 4.5 Write unit tests for gesture → viewport state mapping

## 5. Implement map-based destination picker

- [x] 5.1 On map click: show details overlay (`PaneTemplate`) with coordinates and nearby street name
- [x] 5.2 Add "Navigate here" action on details overlay → calls `NavigationViewModel.navigateTo()`
- [x] 5.3 Add "Clear selection" action to remove selection marker
- [x] 5.4 Wire favorite marker tap → select favorite as destination → "Navigate here" flow (deferred; see spec.md)
- [x] 5.5 On navigation start from map: transition to `NavigationScreen` (pop map, push nav)
- [x] 5.6 Write unit tests for destination picker flow

## 6. Integrate into screen stack

- [x] 6.1 Add "Map" row to `RootScreen` alongside Search and Favorites
- [x] 6.2 Update `NavigationSession.onCreateScreen()` to handle map screen transitions
- [x] 6.3 Ensure navigation start from map correctly transitions to `NavigationScreen`
- [x] 6.4 Ensure back from map returns to `RootScreen`
- [x] 6.5 Verify existing search/favorites/navigation screens still work

## 7. Android Auto manifest and recognition

- [x] 7.1 Add Car App Service intent-filter with action `androidx.car.app.CarAppService` and category `androidx.car.app.category.NAVIGATION`
- [x] 7.2 Add `com.google.android.gms.car.application` metadata referencing `automotive_app_desc.xml`
- [x] 7.3 Set `automotive_app_desc.xml` to use current `<uses name="template" />` declaration
- [x] 7.4 Add `<queries>` element so host packages and `CarAppService` are discoverable
- [x] 7.5 Move `NaviVeylinCarAppService` to `:app` base package for reliable binding
- [x] 7.6 Verify merged manifest resolves service, metadata, permissions, and queries correctly
- [x] 7.7 Verify APK contains `CarAppService` and `NavigationSession` classes
- [x] 7.8 Install on AAOS emulator and confirm app appears in car launcher

## 8. Build verification

- [x] 8.1 Run `./gradlew :auto:assembleDebug` — verify compiles without errors
- [x] 8.2 Run `./gradlew :app:assembleDebug` — verify no regressions in phone module
- [x] 8.3 Run existing unit tests — verify all pass (12 pre-existing failures unrelated)
- [x] 8.4 Run new unit tests — verify all pass
