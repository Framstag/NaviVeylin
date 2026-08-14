## Context

NaviVeylin's map screen currently shows a static map with zoom/pan and an overflow menu. There is no way to search for locations. The native JNI bridge (`OSMScoutClient`) already exposes `searchLocations(String query, int limit)` which calls libosmscout's `LocationService::SearchForLocationByString()`. The search feature needs a UI overlay on the map screen and state management to display results and center the map on selection.

Constraints:
- No Google Play Services — all search is offline via libosmscout
- Search runs on native C++ (blocking JNI call) — must be off main thread
- Map renders via Cairo backend — marker rendering handled by existing `renderWithRouteAndPois()`

## Goals / Non-Goals

**Goals:**
- Add a search button overlay on the map screen
- Draggable bottom-sheet search panel with auto-focused text input
- Suggestions-while-type via debounced native search calls
- Result list below the search input
- Clear text button in the search field
- Selecting a result centers the map on that location and shows a marker
- Use existing `OSMScoutClient.searchLocations()` — no new native code

**Non-Goals:**
- Search history or recent searches
- Voice search
- Geocoding from network APIs (offline-only)
- Favorites integration (separate change)
- Android Auto search (deferred)

## Decisions

1. **Draggable bottom sheet over modal dialog**
   A draggable bottom sheet (`ModalBottomSheet` from Material 3) lets users see the map behind the search panel. A dialog would obscure the map completely. The sheet can be dragged down to dismiss.

2. **Debounced search on `Flow`**
   The search query text field emits values through a `MutableStateFlow<String>` with a 300ms debounce. This avoids flooding the native search with every keystroke. Debounced value triggers a coroutine on `Dispatchers.Default` that calls `client.searchLocations()`.

3. **Reuse `renderWithRouteAndPois()` for marker**
   The existing `renderWithRouteAndPois()` accepts `searchSelLat`/`searchSelLon` and draws a marker on the rendered map. The ViewModel passes the selected location coordinates to this method. No new Cairo rendering code needed.

4. **Search state in ViewModel, not a separate SearchViewModel**
   The search state (query, results, loading, selected location) lives in `MapCanvasViewModel` since it directly affects the map viewport and rendering. A separate ViewModel would add unnecessary coordination complexity.

5. **`LocationEntry` model used directly in UI**
   The JNI `LocationEntry` class (label, lat, lon, objectType, region) is used directly in the UI layer. It's a simple data class with no Android framework dependencies, so no mapping to a separate UI model needed.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| **Slow search on large datasets**: libosmscout text search can be slow on big OSM extracts | Keep debounce at 300ms; show loading indicator in results list; cap results via `limit` param |
| **JNI call blocks Default dispatcher**: Even on `Dispatchers.Default`, a long search blocks a pooled thread | Consider `Dispatchers.IO` if Default contention observed; add timeout via `withTimeout` |
| **Bottom sheet interferes with map gestures**: Dragging the sheet might conflict with map pan | No gesture conflict — sheet is a separate composable layer above the Canvas; map gestures are unaffected when sheet is shown |
| **No search results feedback**: User types and sees nothing if search returns empty | Show "No results found" message in the results area |
