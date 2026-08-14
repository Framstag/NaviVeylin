## 1. MapRenderer — Add route overlay support

- [x] 1.1 Add route data fields to `MapRenderer`: `routeLats`, `routeLons`, `routeStartLat`, `routeStartLon`, `routeDestLat`, `routeDestLon` (all `@Volatile` or atomic)
- [x] 1.2 Add `setRoute()` method — accepts route coords arrays + start/dest coords, sets fields, increments epoch, triggers re-render
- [x] 1.3 Add `clearRoute()` method — nulls route fields, increments epoch, triggers re-render
- [x] 1.4 Update `RenderJob` data class to include route coords + start/dest coords
- [x] 1.5 Update `executeRender()` to pass route coords to `renderWithRouteAndPois()` when route is set (instead of `null, null`)
- [x] 1.6 Verify build compiles with `./gradlew :app:compileDebugKotlin`

## 2. RoutePanelViewModel — Route calculation state

- [x] 2.1 Create `RoutePanelViewModel` as `@HiltViewModel` injected with `OSMScoutClient`, `FavoriteRepository`, `LocationService`
- [x] 2.2 Define `RoutePanelUiState` data class: `startLocation`, `destLocation`, `vehicle`, `routeState` (Idle/Calculating/Done/Error), `routeEntry`, `routeInstructions`, `error`
- [x] 2.3 Expose `uiState: StateFlow<RoutePanelUiState>`
- [x] 2.4 Implement `setStartLocation(LocationEntry)`, `setDestLocation(LocationEntry)`, `setVehicle(Vehicle)`, `swapStartDest()`
- [x] 2.5 Implement `calculateRoute()` — creates `RoutingProfile`, calls `client.calculateRouteAsync()` with `RouteCallback`, marshals callbacks to coroutine context
- [x] 2.6 Implement `cancelRoute()` — calls `client.cancelRoute()`, resets state to Idle
- [x] 2.7 Implement `clearRoute()` — resets all state to defaults
- [x] 2.8 Expose `routeResultFlow: StateFlow<RouteResult?>` for `MapCanvasViewModel` to collect (contains route coords + start/dest for map rendering)
- [x] 2.9 Verify build compiles

## 3. MapCanvasViewModel — Wire route data to renderer

- [x] 3.1 Add `showRoutePanel` boolean to `MapCanvasUiState`
- [x] 3.2 Add `openRoutePanelWithStart(LocationEntry?)` method — sets start location, shows route panel
- [x] 3.3 Add `dismissRoutePanel()` method
- [x] 3.4 Collect `RoutePanelViewModel.routeResultFlow` in `init` block — when route result arrives, call `mapRenderer.setRoute()` then `renderMap()`
- [x] 3.5 Collect `RoutePanelViewModel.clearRouteSignal` — call `mapRenderer.clearRoute()` then `renderMap()`
- [x] 3.6 Add `setRouteStart(LocationEntry)` and `setRouteDest(LocationEntry)` methods for search/fav/current-location picking
- [x] 3.7 Verify build compiles

## 4. RoutePanel composable — Route panel UI

- [x] 4.1 Create `RoutePanel.kt` in `ui/route/` package with `@Composable fun RoutePanel(...)` as `ModalBottomSheet`
- [x] 4.2 Implement header with title and close (X) button
- [x] 4.3 Implement start location field row: label + search icon + fav icon + current-location option
- [x] 4.4 Implement destination location field row: label + search icon + fav icon + current-location option
- [x] 4.5 Implement swap button between start and dest rows
- [x] 4.6 Implement vehicle selector row: Car / Bicycle / Pedestrian toggle buttons with highlight
- [x] 4.7 Implement Calculate button (enabled when both fields set) and Cancel button (during calculation)
- [x] 4.8 Implement progress indicator during calculation
- [x] 4.9 Implement Clear button (visible when route is calculated)
- [x] 4.10 Implement turn-by-turn instruction list (scrollable, shows description + distance)
- [x] 4.11 Implement error state display
- [x] 4.12 Verify build compiles

## 5. LocationDetailsSheet — Add Route button

- [x] 5.1 Add `onRouteToLocation: (() -> Unit)?` parameter to `LocationDetailsSheet`
- [x] 5.2 Add "Route" button alongside favorite controls — visible when `onRouteToLocation != null`
- [x] 5.3 Tapping "Route" button calls `onRouteToLocation` and dismisses the sheet
- [x] 5.4 Verify build compiles

## 6. SearchPanel — Reusable for route location picking

- [x] 6.1 Add optional `onResultSelectedOverride: ((LocationEntry) -> Unit)?` parameter to `SearchPanel`
- [x] 6.2 When override is set, use it instead of the default `onResultSelected` behavior
- [x] 6.3 Verify build compiles

## 7. FavoritePickerDialog — Lightweight favorite selection

- [x] 7.1 Create `FavoritePickerDialog.kt` in `ui/route/` package — `ModalBottomSheet` showing flat list of all favorites (group name + fav name)
- [x] 7.2 Accept `onFavoriteSelected: (LocationEntry) -> Unit` callback
- [x] 7.3 Load favorites from `FavoriteRepository` via ViewModel or direct injection
- [x] 7.4 Verify build compiles

## 8. MapCanvasScreen — Wire route panel into screen

- [x] 8.1 Add `showRoutePanel` state variable and conditional `RoutePanel` composition in `MapCanvasScreen`
- [x] 8.2 Wire `routeFieldPicker` state (`RouteField.START` / `RouteField.DEST` / null) for search panel reuse
- [x] 8.3 Pass `onResultSelectedOverride` to `SearchPanel` when picking for route
- [x] 8.4 Wire "Current Location" option — get GPS location from `LocationService`, create `LocationEntry` with label "Current Location"
- [x] 8.5 Wire `FavoritePickerDialog` — show when user taps fav icon on route field
- [x] 8.6 Wire `LocationDetailsSheet.onRouteToLocation` — calls `viewModel.openRoutePanelWithStart(entry)`
- [x] 8.7 Verify build compiles

## 9. Build and verify

- [x] 9.1 Run `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` — verify build succeeds
- [x] 9.2 Run `./gradlew test` — verify existing tests pass
- [ ] 9.3 Manual smoke test: search → details → Route button → route panel opens with start prefilled
- [ ] 9.4 Manual smoke test: set destination via search, calculate route, verify polyline + markers on map
- [ ] 9.5 Manual smoke test: swap start/dest, recalculate
- [ ] 9.6 Manual smoke test: switch vehicle, recalculate
- [ ] 9.7 Manual smoke test: cancel during calculation
- [ ] 9.8 Manual smoke test: clear route, verify polyline removed
- [ ] 9.9 Manual smoke test: dismiss route panel, verify route persists on map
