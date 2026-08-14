## Why

Phase 1 added turn-by-turn navigation on Android Auto (`NavigationTemplate`). But drivers can only navigate to a destination set on the phone — no way to search or pick favorites from the car screen. Phase 2 adds car-optimized search and favorites UIs so drivers can find destinations and start navigation entirely from the car display.

## What Changes

- New `SearchScreen` composable using `SearchTemplate` for car-optimized search
- New `FavoritesScreen` composable using `PlaceListTemplate` for browsing favorites
- "Navigate here" action on search result and favorite selection
- Wire "Navigate here" → existing routing pipeline (`RoutePanelViewModel.calculateRoute()` + `NavigationViewModel.startNavigation()`)
- New root `Screen` with `PaneTemplate` (search + favorites shortcuts) — entry point when not navigating
- No changes to phone-side UI or existing navigation flow

## Capabilities

### New Capabilities
- `auto-search`: Car-optimized location search using `SearchTemplate`, backed by `OSMScoutClient.searchLocations()`
- `auto-favorites`: Car-optimized favorites browser using `PlaceListTemplate`, backed by `FavoriteRepository`
- `auto-destination-picker`: "Navigate here" action on search results and favorites, wired to existing routing pipeline

### Modified Capabilities
- *(none — no existing spec-level behavior changes)*

## Impact

- `auto/` module: new `SearchScreen`, `FavoritesScreen`, root `Screen` with `PaneTemplate`, mapper classes
- `app/` module: `NavigationStateProvider` may need bridge for search/favorites state
- `core/` module: no changes expected
- Dependencies: no new external deps — reuses existing `OSMScoutClient.searchLocations()` and `FavoriteRepository`
