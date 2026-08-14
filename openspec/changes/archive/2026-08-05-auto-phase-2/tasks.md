## 1. Core Bridge — Extend AutoEntryPoint + NavigationStateProvider

- [x] 1.1 Create `AutoSearchProvider` + `AutoFavoritesProvider` interfaces in `:core`; add to `AutoEntryPoint`
- [x] 1.2 Add `navigateTo()` to `core.NavigationViewModel` interface
- [x] 1.3 Create `AutoServiceModule` in `:app` providing `AutoSearchProvider` + `AutoFavoritesProvider`
- [x] 1.4 Implement `navigateTo()` in app's `NavigationViewModel` — creates LocationEntry, calls calculateRoute(), starts nav on success
- [x] 1.5 Add `navigateTo()` delegation to `NavigationStateProvider`
- [x] 1.6 Verify `:auto` module compiles with new `AutoEntryPoint` methods

## 2. SearchScreen — Car-Optimized Search

- [x] 2.1 Create `SearchScreen` composable extending `Screen`, using `SearchTemplate`
- [x] 2.2 Wire `onSearchTextChanged` callback with 300ms debounce → `AutoEntryPoint.searchLocations()`
- [x] 2.3 Display search results as `SearchTemplate` result list with location name + description
- [x] 2.4 Handle empty query (no results), no results state, and error state
- [x] 2.5 Add "Navigate here" action on each search result → calls `NavigationStateProvider.navigateTo()`
- [x] 2.6 Write unit tests for `SearchScreen` mapper logic

## 3. FavoritesScreen — Car-Optimized Favorites Browser

- [x] 3.1 Create `FavoritesScreen` composable extending `Screen`, using `PlaceListTemplate`
- [x] 3.2 Wire favorites data from `AutoEntryPoint.favorites()` → group items with headers
- [x] 3.3 Display each favorite with name + address/description
- [x] 3.4 Handle empty favorites state
- [x] 3.5 Add "Navigate here" action on each favorite → calls `NavigationStateProvider.navigateTo()`
- [x] 3.6 Write unit tests for `FavoritesScreen` mapper logic

## 4. Root Screen — PaneTemplate Entry Point

- [x] 4.1 Create `RootScreen` composable extending `Screen`, using `PaneTemplate`
- [x] 4.2 Add search shortcut in left pane → pushes `SearchScreen`
- [x] 4.3 Add favorites shortcut in right pane → pushes `FavoritesScreen`
- [x] 4.4 Wire screen transitions via `ScreenManager.pushScreen()` / `popToRoot()`

## 5. NavigationSession — Screen Routing

- [x] 5.1 Update `NavigationSession.onCreateScreen()` to return `RootScreen` when not navigating
- [x] 5.2 Keep returning `NavigationScreen` when navigation is active (existing behavior)
- [x] 5.3 Wire lifecycle-aware state observation for screen switching (navigate from car → switch to `NavigationTemplate`)
- [x] 5.4 Handle navigation stop from car → return to `RootScreen`

## 6. Build & Verify

- [x] 6.1 Run `./gradlew :auto:assembleDebug` — verify compilation
- [x] 6.2 Run `./gradlew :app:assembleDebug` — verify no regressions
- [x] 6.3 Run existing unit tests — verify all pass (12 pre-existing failures unrelated to changes)
- [x] 6.4 Write unit tests for SearchScreen, FavoritesScreen, and RootScreen mappers
