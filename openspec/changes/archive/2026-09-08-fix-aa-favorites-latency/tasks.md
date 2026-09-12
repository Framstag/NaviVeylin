## 1. FavoritesScreen reactive collection

Specs: auto-favorites ("Favorites appear without re-entering the screen"). Design: D1.

- [x] 1.1 Replace the one-shot read in `FavoritesScreen` (`auto/src/main/java/com/naviveylin/auto/FavoritesScreen.kt`): `favoritesProvider.favoriteLocations().first()` → `.collect { ... }` updating `favoritesData` + `invalidate()`, keeping the `loaded` flag (show "Loading" until first emission); verify the screen re-renders when the flow emits after the screen is open (mirror `MapScreen.kt:577` / `DetailsScreen.kt:134`)
- [x] 1.2 Add a `DefaultLifecycleObserver` to `FavoritesScreen` cancelling `scope` in `onDestroy` (pattern from `MapScreen`), so the collect does not leak per screen instance; verify the scope is cancelled on destroy
- [x] 1.3 Add `FavoritesScreenTest` (Robolectric, default sandbox per AGENTS.md classloader rule; `testCarContext()` + fake `AutoFavoritesProvider`): late emission after screen construction updates the rendered list without re-entering; empty store shows "No favorites saved"; scope cancelled on destroy

## 2. Warmup reorder

Specs: auto-favorites (favorites available without re-entry). Design: D2.

- [x] 2.1 Move the favorites init step in `NavigationSession.startWarmup` (`auto/src/main/java/com/naviveylin/auto/NavigationSession.kt`) ahead of `openMapDatabases` (order: entry point → client build → favorites init → nav controller → openMapDatabases), keeping the step best-effort (try/catch + log); verify the step order in the warmup log
- [x] 2.2 Add/extend a warmup-ordering test (or log-based assertion) verifying the favorites step completes before `openMapDatabases`; verify existing session tests still pass

## 3. Build verification

- [x] 3.1 Run `./gradlew :app:assembleMobileDebug` (covers `:auto`) and verify no compile errors, no new warnings
- [x] 3.2 Run `./gradlew test` and verify the full suite (existing + new tests) passes

## 4. On-device verification (AA emulator/head unit)

Specs: auto-favorites scenarios. Design: D1, D2.

- [x] 4.1 Cold start in AA mode, open the favorites screen immediately: favorites appear without leaving/re-entering the screen; verify `adb logcat -s NaviVeylin` shows the favorites warmup step before the map-database step
- [x] 4.2 Open the favorites screen before warmup completes (multiple installed maps): the list updates in place once favorites load; verify no stale "no favorites saved" state persists
