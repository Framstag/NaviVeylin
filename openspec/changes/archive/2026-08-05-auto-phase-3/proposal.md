## Why

Phases 1 and 2 added turn-by-turn navigation, search, and favorites on Android Auto. But the car screen shows only text-based templates — no map. Drivers cannot see their position, browse the map, or pick destinations visually. Phase 3 renders libosmscout maps on the car display, bringing visual map context to Android Auto.

## What Changes

- New `AutoMapRenderer` in `:auto` module: renders libosmscout map to Android `Surface` via JNI, reusing `OSMScoutClient.renderWithRouteAndPois()` from `:app`
- `MapTemplate` with custom `Surface` renderer — Google Play Services available for `MapController`
- GPS position marker on car map (reuse `LocationService.location` via `NavigationStateProvider`)
- Favorites markers on car map (reuse `FavoriteRepository.favorites` via `AutoFavoritesProvider`)
- Pan/zoom gesture handling adapted for car input model (rotary controller, touch)
- Map-based destination picker: select location on car map → navigate
- New `MapScreen` composable using `MapTemplate` or `MapWithContentTemplate`
- Screen transitions: `RootScreen` → `MapScreen` (browse mode) → `NavigationScreen` (nav mode)
- No changes to phone-side map rendering

## Capabilities

### New Capabilities
- `auto-map-renderer`: Render libosmscout map to Android `Surface`/bitmap for Android Auto display, reusing existing JNI render pipeline
- `auto-map-interaction`: Pan/zoom gestures on car map adapted for rotary controller and touch input
- `auto-map-destination-picker`: Select location on car map → navigate, wired to existing routing pipeline

### Modified Capabilities
- `auto`: Add map browsing screen to Android Auto module alongside existing search, favorites, and navigation screens

## Impact

- `auto/` module: new `AutoMapRenderer`, `MapScreen`, `MapInteractionHandler`, mapper classes, updated `NavigationSession` for screen routing
- `app/` module: `MapRenderer` may need minor refactoring to expose reusable render logic (e.g., extract render-to-bitmap utility)
- `core/` module: no changes expected
- Dependencies: no new external deps — reuses existing `OSMScoutClient.renderWithRouteAndPois()` and `LocationService`
- Google Play Services available for Android Auto `MapController` — no fallback needed
