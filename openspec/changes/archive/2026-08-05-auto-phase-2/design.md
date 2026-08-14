## Context

Phase 1 established the Android Auto architecture: `NavigationSession` creates a `NavigationScreen` that renders a `NavigationTemplate` from `NavigationState` observed via `NavigationStateProvider`. The `:auto` module depends on `:core` for the `NavigationViewModel` interface and `NavigationState` data class. Currently, `NavigationSession.onCreateScreen()` always returns a `NavigationScreen` (nav template or empty template).

Phase 2 adds search and favorites UIs on the car screen. The `:auto` module needs access to `OSMScoutClient.searchLocations()` and `FavoriteRepository` — both live in `:app`. The `AutoEntryPoint` interface in `:core` currently exposes only `navigationViewModel()`. See `proposal.md` for motivation.

## Goals / Non-Goals

**Goals:**
- `SearchScreen` using `SearchTemplate` — car-optimized search with debounce
- `FavoritesScreen` using `PlaceListTemplate` — browse favorites by group
- "Navigate here" action on both → route calc + start nav
- `PaneTemplate` root screen with search/favorites shortcuts when not navigating
- All state flows through `NavigationStateProvider` bridge

**Non-Goals:**
- No changes to phone-side search/favorites UI
- No map rendering on car (Phase 3)
- No voice guidance (deferred)
- No favorites editing on car (read-only browse + navigate)

## Decisions

### Decision: Extend `AutoEntryPoint` for search + favorites
**Why:** `NavigationSession` already uses `AutoEntryPoint` to get `NavigationViewModel`. Adding `searchLocations()` and `favorites()` keeps the same pattern — single Hilt entry point for all Auto dependencies. Alternative: create separate entry points per concern, but that adds boilerplate with no benefit for 3 dependencies.

### Decision: `NavigationSession` manages screen stack, not a separate router
**Why:** Android Auto's `Session.onCreateScreen()` returns the initial screen. Screen transitions happen via `ScreenManager.pushScreen()` / `ScreenManager.popToRoot()`. The `NavigationSession` already holds the screen reference. Adding a simple state-based screen switch in `onCreateScreen()` and using `ScreenManager` for navigation between `PaneTemplate` → `SearchScreen`/`FavoritesScreen` → `NavigationScreen` keeps it simple. A full navigation graph would be overkill for 3 screens.

### Decision: Route calculation delegates to `RoutePanelViewModel` via `NavigationStateProvider`
**Why:** The existing `NavigationViewModel` in `:app` already has `setRoutePanelViewModel()` which wires rerouting. For "Navigate here" from car, we need to call `RoutePanelViewModel.calculateRoute()` with the selected destination and current GPS position. The `NavigationStateProvider` bridge already exists — extend it with a `navigateTo(destLat, destLon)` method that creates a `LocationEntry`, calls `calculateRoute()`, and starts navigation on success.

### Decision: `SearchTemplate` uses `onSearchTextChanged` callback, not custom input
**Why:** `SearchTemplate` provides built-in search input with `setOnSearchTextChangedListener`. This avoids custom text input handling and matches Android Auto UX patterns. Debounce is handled client-side before calling the JNI search.

### Decision: `PlaceListTemplate` for favorites, not `ListTemplate`
**Why:** `PlaceListTemplate` is designed for location-based lists with "Navigate here" actions. `ListTemplate` is generic. `PlaceListTemplate` provides `setOnItemSelectedDelegate()` for selection handling and supports `Place.Builder` with location metadata.

## Risks / Trade-offs

- **[Risk] `AutoEntryPoint` grows** → Mitigation: keep it focused. Only add what Auto screens need. If it grows beyond 5 methods, split into separate entry points.
- **[Risk] `NavigationStateProvider` becomes a god bridge** → Mitigation: `navigateTo()` is the only addition. Keep `NavigationStateProvider` focused on state observation + navigation control.
- **[Risk] Search debounce on car may feel laggy** → Mitigation: 300ms debounce (same as phone). JNI search is fast for local data.
- **[Risk] No GPS fix when starting nav from car** → Mitigation: show error message. `LocationService` already provides GPS state via `NavigationStateProvider`.

## Migration Plan

1. Extend `AutoEntryPoint` with `searchLocations()` and `favorites()` methods
2. Add `navigateTo()` to `NavigationStateProvider`
3. Create `SearchScreen` composable with `SearchTemplate`
4. Create `FavoritesScreen` composable with `PlaceListTemplate`
5. Create root `PaneTemplate` screen
6. Update `NavigationSession` to return `PaneTemplate` when not navigating
7. Wire screen transitions: PaneTemplate → SearchScreen/FavoritesScreen → NavigationScreen
8. Unit tests for new screens and mappers

## Open Questions

- None. Specs, approach, and task breakdown are clear.
