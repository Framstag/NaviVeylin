## 1. ViewModel — Search State & Logic

- [x] 1.1 Add search fields to `MapCanvasUiState`: `searchQuery: String`, `searchResults: List<LocationEntry>`, `isSearching: Boolean`, `selectedLocation: LocationEntry?`
- [x] 1.2 Add `MutableStateFlow<String>` for search query with 300ms debounce in `MapCanvasViewModel`
- [x] 1.3 Add coroutine in ViewModel that collects debounced query, calls `client.searchLocations(query, 20)` on `Dispatchers.Default`, and updates `searchResults`
- [x] 1.4 Add `onSearchQueryChanged(query: String)`, `onSearchResultSelected(entry: LocationEntry)`, `clearSearch()` methods to ViewModel
- [x] 1.5 `onSearchResultSelected` updates `selectedLocation`, re-centers viewport via `updateCenter()`, and triggers re-render with marker via `renderWithRouteAndPois()`

## 2. Search Panel UI

- [x] 2.1 Create `SearchPanel.kt` composable with `ModalBottomSheet` and auto-focused `OutlinedTextField`
- [x] 2.2 Add clear (X) button inside the text field using `trailingIcon`
- [x] 2.3 Add result list below input using `LazyColumn`, each item showing `LocationEntry.label` and `adminRegionHierarchy`
- [x] 2.4 Show `CircularProgressIndicator` in results area while `isSearching` is true
- [x] 2.5 Show "No results found" text when search completes with empty results
- [x] 2.6 Wire `onDismiss` callback to clear search state

## 3. Map Screen Integration

- [x] 3.1 Add search icon button (`Icons.Default.Search`) in top-left of `MapCanvasScreen` overlay
- [x] 3.2 Add `showSearchPanel: Boolean` state variable; toggle on search button tap
- [x] 3.3 Render `SearchPanel` when `showSearchPanel` is true, passing ViewModel state and callbacks
- [x] 3.4 On search result selection: dismiss panel, center map, trigger re-render with marker

## 4. Map Rendering with Marker

- [x] 4.1 Update `MapCanvasViewModel.renderOnDefault()` to call `client.renderWithRouteAndPois()` when `selectedLocation` is non-null, passing `searchSelLat`/`searchSelLon`
- [x] 4.2 Keep `renderOnDefault()` calling `client.render()` when no selected location (backward compatible)
- [x] 4.3 Ensure marker persists across pan/zoom re-renders (selectedLocation stays in state until new search or app restart)

## 5. Build & Verify

- [x] 5.1 Run `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` and fix any compilation errors
- [x] 5.2 Run `./gradlew test` and verify existing tests still pass
- [ ] 5.3 Manual smoke test: open map, tap search, type query, verify suggestions appear, select result, verify map centers and marker shows
