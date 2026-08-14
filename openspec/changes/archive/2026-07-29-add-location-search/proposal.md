## Why

Users navigating with NaviVeylin need to find specific places, addresses, and points of interest. Currently the app only shows a static map with zoom/pan. Adding location search lets users quickly jump to a destination — a core requirement for any navigation app.

## What Changes

- Add a search button overlay on the map screen
- Add a draggable bottom-sheet search panel with:
  - Auto-focused text input field
  - Suggestions-while-type via native `LocationService::SearchForLocationByString()` (exposed through the existing `OSMScoutClient.searchLocations()` JNI bridge)
  - Results list showing matching locations below the input
  - Clear text button in the search field
- On selecting a candidate: dismiss the search panel, mark the location on the map, and re-center the viewport to that coordinate
- Add `searchMarkerLat`/`searchMarkerLon` state to `MapCanvasUiState` and render a marker via `renderWithRouteAndPois()`
- All search UI is Compose + Material 3; no new native code needed (search already exposed via JNI)

## Capabilities

### New Capabilities

- `location-search`: Free-text search for locations, addresses, and POIs via libosmscout's `LocationService::SearchForLocationByString()`. Search-as-you-type with debounced native queries, results displayed in a draggable bottom sheet overlay, selection centers the map on the chosen location.

### Modified Capabilities

*(No existing capability REQUIREMENTS change — only implementation details)*

## Impact

- **New UI composable**: `SearchPanel.kt` — draggable bottom-sheet with search input, suggestion list, clear button
- **Modified screen**: `MapCanvasScreen.kt` — add search button overlay, wire search panel visibility and selection callback
- **Modified ViewModel**: `MapCanvasViewModel.kt` — add search state (query, results, loading, selected location), debounced search call to `OSMScoutClient.searchLocations()`
- **Modified UI state**: `MapCanvasUiState` — add `searchQuery`, `searchResults: List<LocationEntry>`, `isSearching`, `selectedLocation: LocationEntry?`
- **Map rendering**: Update to pass `selectedLocation` coords to `renderWithRouteAndPois()` for marker display
- **No new native/JNI code** — reuses existing `searchLocations()` method
- **No new dependencies**
