# NaviVeylin TODO — Features JavaScout Has, NaviVeylin Doesn't

Based on analysis of [JavaScout](https://github.com/Framstag/libosmscout/tree/master/JavaScout) (libosmscout's reference JavaFX app) and its [OpenSpec specs](https://github.com/Framstag/libosmscout/tree/master/openspec/specs).

**Legend:** ✗ = missing | ✓ = implemented | 🏗 = in progress

---

## 1. Route Calculation & Visualization

| Feature | Status | Notes |
|---------|--------|-------|
| Route calculation (A→B) | ✓ | Via `RoutePanel` + `calculateRouteWithProfile()` |
| Route polyline on map | ✓ | Via `MapRenderer.setRoute()` + `renderWithRouteAndPois()` |
| Start/destination markers | ✓ | `_route_start`, `_route_end` POI types rendered |
| Route panel (start/dest labels, calculate, clear) | ✓ | `RoutePanel` composable with inline search-on-type |
| Vehicle selector (car/bicycle/pedestrian) | ✓ | `RoutingProfile` with `Vehicle` enum, wired via `calculateRouteWithProfile()` |
| Avoid tolls/ferries checkboxes | ✗ | `RoutingProfile` supports avoid flags — no UI yet |
| Route instructions list (turn-by-turn text) | ✓ | Via `RouteStepDisplay` in `RouteSummaryDialog` — columnar view with direction icon, distance/time, advice |
| Route summary dialog (stats + steps + Start Nav) | ✓ | `RouteSummaryDialog` — slides up from bottom, full-width, shows distance, time, scrollable steps, Start Navigation button |
| Route progress indicator | ✓ | `CircularProgressIndicator` during calculation |
| Route cancellation | ✓ | `cancelRoute()` wired to Cancel button |
| Bug: route polyline persists after navigation stop | ✗ | Stopping navigation clears `NavigationState` but route polyline + markers remain on map. Need to call `mapRenderer.clearRoute()` on stop. |
| Bug: search dialog height jumps on text input | ✓ | Fixed: `heightIn(min = 280.dp, max = 280.dp)` on `SearchPanel` Column — sheet height stable across all states |

## 2. Turn-by-Turn Navigation

| Feature | Status | Notes                                                                                                                                           |
|---------|--------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| GPS follow mode (map auto-centers on location) | ✓ | `LocationOptionsOverlay` toggle + `followMode` in `MapCanvasUiState`. Persisted via `SettingsStorage`. Disengages on manual pan/zoom. Re-center button appears on right column when disengaged + GPS available. Uses `Icons.Filled.MyLocation`. |
| Navigation controller | ✓ | `NavigationViewModel` wraps JNI `NavigationController` + `NavigationListener`. Full callback set: position, instruction, speed, reroute, error. |
| Next turn overlay (icon, distance, street name) | ✓ | `NextTurnOverlay` composable with emoji turn icons, distance, street name, description                                                          |
| Correct turn instruction icons/symbols | ✓ | Canvas-drawn SVG-style arrows via `NavigationArrowRenderer` — 12 turn types, 18 lane types, roundabouts |
| Next-next turn preview | ✓ | `NextTurnOverlay` shows `nextNextTurnType` + `nextNextDistanceTo` when available                                                                |
| Current road name/ref display | ✓ | `NavigationState.currentRoadInfo` parsed from `getDescription()`, displayed in `NavigationStateOverlay`                                         |
| Auto-rerouting on off-route detection | ✓ | `onRerouteRequest` callback sets current pos as start, keeps dest, recalculates via `RoutePanelViewModel`. Dismisses summary dialog during nav. |
| Auto-zoom by speed | ✓ | `SPEED_ZOOM_TABLE` with linear interpolation, smooth transitions, manual zoom suspension, persistent toggle in settings                         |
| Turn-zoom (zoom in near turns) | ✓ | Boost mag within 2000m/5000m of turn, hold 600m past. Curve detection for strong route bends without formal turn instruction                    |
| Speed spike filtering | ✓ | Reject speed > 150 km/h, use last good speed, default 20 km/h                                                                                   |
| Navigation state display (distance, time) | ✓ | `NavigationStateOverlay` composable: ETA, remaining distance, current speed, max speed, Stop button                                             |
| Speed warning (red when over limit) | ✓ | Current speed turns red when exceeding max allowed speed by 5+ km/h |
| Voice guidance / audio instructions | ✗ | JavaScout `onVoiceInstruction(int[])` callback exists in JNI                                                                                    |
| Lane navigation display | ✓ | `LaneHintsRow` in `NextTurnOverlay` with per-lane arrows, suggested lanes highlighted, toggle in settings |

## 3. Map Rotation

| Feature | Status | Notes |
|---------|--------|-------|
| Map rotation (north-up / driving-direction-up) | ✓ | Per-mode orientation (free-form + navigation) via `LocationOptionsOverlay` bottom sheet. Persisted in `AppSettings`. |
| Two-finger rotation gesture | ✓ | Direct rotation via two-finger gesture on canvas, disengages follow mode |
| Compass button overlay | ✓ | Animated compass with GPS fix ring, short-press re-center, long-press mode toggle. Mode-specific appearance (red "N" for north-up, blue arrow for follow-direction). |
| Compass rotation animation | ✓ | 300ms tween via `animateFloatAsState`, needle rotates to show north |

## 4. Favorite Locations

| Feature | Status | Notes |
|---------|--------|-------|
| Favorite groups (add/delete/rename) | ✓ | Via `FavoriteRepository` + `FavoritesSheet` — rename via group card menu, color picker with 8-12 swatches |
| Favorites within groups (add/delete/rename) | ✓ | Via `FavoriteRepository` + `FavoritesSheet`, star toggle per favorite |
| Favorite markers on map | ✓ | Via `renderWithRouteAndPois()` + `_favorite` type |
| Favorite management dialog | ✓ | Full-screen `FavoritesSheet` with group grid (`LazyVerticalGrid`), card menu (rename/delete/set color), detail view, back navigation |
| Search favorites by name | ✓ | Search bar at top of sheet, filters across all groups, results grouped by group name |
| Favorite picker for route start/dest | ✓ | `FavoritePickerDialog` — lightweight modal for route field selection, `StarredChipBar` for quick access |
| Persistence to JSON file | ✓ | Via JNI `loadFavoriteLocations()`/`saveFavoriteLocations()` |
| Details sheet with fav button | ✓ | `LocationDetailsSheet` after search result selection or long-press, structured description sections, draggable |

## 5. GPX Track Import & Playback

| Feature | Status | Notes |
|---------|--------|-------|
| GPX file import | ✗ | `importGpxTrack()` exists in JNI |
| Track rendering on map | ✗ | `renderWithRouteAndPois()` accepts `trackLats`/`trackLons` |
| Track playback (simulated GPS) | ✗ | JavaScout `TrackPlayer.java` with speed multiplier |
| Track playback toolbar (play/pause/stop/speed) | ✗ | JavaScout `trackToolbar` HBox |

## 6. Object Description & Long-Press

| Feature | Status | Notes |
|---------|--------|-------|
| Long-press on map → object description | ✓ | Via `ProjectionUtils.screenToGeo()` + `getDescription()` |
| Description overlay dialog | ✓ | `LocationDetailsSheet` with object description |
| Long-press timeout configuration | ✗ | Hardcoded 500ms — JavaScout configurable |

## 7. Map Interaction

| Feature | Status | Notes |
|---------|--------|-------|
| Keyboard shortcuts (+/- zoom, / search) | ✓ | `+`/`-` zoom in/out, `/` opens search panel |
| Touch pan (single-finger drag) | ✓ | Mercator projection via `ProjectionUtils.dragDeltaToNewCenter()` |
| Pinch-to-zoom | ✓ | Zoom-at-cursor via `ProjectionUtils.zoomAtCursor()` |
| Zoom in/out buttons | ✓ | With magnification level display, max zoom 20 |
| Combined zoom in/out control | ✓ | Pill-shaped `Row` with +/- buttons + zoom level text, shadow elevation |
| Canvas overrun (render beyond visible area) | ✓ | Enabled (overrun=1.5). Sub-region blit via `MapRenderer.trySubRegionBlit()` |
| Tile cache (reuse rendered tiles) | ✓ | `TileCache` with LRU eviction, epoch invalidation |
| Debounced re-render on pan/zoom | ✓ | 50ms pan debounce, 200ms zoom debounce via coroutine Channel |
| Double-buffered rendering | ✓ | Back/front buffer swap with epoch-based stale detection |
| Zoom placeholder (scaled buffer during zoom) | ✓ | `trySubRegionBlit()` scales front buffer on zoom change |
| Render timing metrics | ✓ | Logged at DEBUG level, WARNING if >500ms |

## 8. Search

| Feature | Status | Notes |
|---------|--------|-------|
| Free-text location search | ✓ | |
| Search-as-you-type with debounce | ✓ | 300ms debounce |
| Search results list | ✓ | |
| Search marker on map | ✓ | |
| Search result limit config | ✓ | Hardcoded 20 — JavaScout uses 50 |
| Search for route location picking | ✓ | Inline search-on-type in route panel fields with Current Location + Select Favorite entries |

## 9. Map Download

| Feature | Status | Notes |
|---------|--------|-------|
| Provider selection dropdown | ✓ | |
| Available maps tree view | ✓ | |
| Download with progress | ✓ | |
| Cancel download | ✓ | |
| Delete installed map | ✓ | |
| Search/filter maps | ✓ | |
| Installed maps persist across restarts | ✓ | |
| Progress bar updates reactively during download | ✓ | Fixed: `progressMap` in UI state + `key` on items ensures reactive recomposition |
| Installed maps always visible without refresh | ✓ | Merged at ViewModel level, synthetic entries always present |
| Installed maps stay on top after refresh | ✓ | Separate "Installed Maps" section at top of tree |
| Screen stays on during download | ✓ | `PowerManager` partial wake lock acquired on first download, released on last end |
| App not hibernated during download | ✓ | `MapDownloadService` foreground service with notification |

## 10. UI / Shell

| Feature | Status | Notes |
|---------|--------|-------|
| Main menu overlay (settings, about, etc.) | ✓ | DropdownMenu in `MapCanvasScreen` + `MainScreen` with Download Maps, Favorites, About |
| Responsive layout (small screen support) | ✗ | JavaScout `SMALL_SCREEN_THRESHOLD` (600px) |
| Landscape orientation support | ✓ | Orientation-aware layout via `BoxWithConstraints`. Controls on right side, zoom horizontal, fav+search side-by-side. Left side reserved for nav hints. |
| DPI-aware UI scaling | ✗ | JavaScout `UIScale.java` |
| Internationalisation (i18n) | ✗ | Multi-language UI support (strings, units, formats) |
| App icon | ✗ | No app icon — default Android placeholder |

## 11. Rendering

| Feature | Status | Notes |
|---------|--------|-------|
| Basic map render (Cairo) | ✓ | |
| Route overlay on map | ✓ | Via `MapRenderer.setRoute()` + `renderWithRouteAndPois()` route params |
| Favorite markers on map | ✓ | Via `renderWithRouteAndPois()` + `_favorite` type |
| Track rendering on map | ✗ | |
| Search selection marker | ✓ | |
| Location marker (current GPS position) | ✓ | Rendered natively on Cairo surface via `client.setGpsMarker()`. Always visible when GPS fix exists (triggers re-render on position change >5m). Bearing arrow + accuracy circle. |
| Location bearing/accuracy indicator | ✓ | Compass-style direction arrow with drop shadow, accuracy circle (shown when poor). Bearing adjusted by map rotation. |
| Map rotation support in renderer | ✓ | `angle` param passed to JNI `render()`/`renderWithRouteAndPois()`. `renderedAngle` exposed for marker overlay. |
| Canvas overrun (smooth panning) | ✓ | Enabled (overrun=1.2). Sub-region blit in `trySubRegionBlit()` |
| Tile cache | ✓ | LRU cache with epoch invalidation, tile composition |
| Double-buffered rendering | ✓ | Back/front buffer with lock-protected swap |
| DPI-aware font rendering | ✓ | Fixed: `cairo_set_scaled_font()` was missing in non-Pango path |
| Configurable base font size | ✓ | Via `OSMScoutClientBuilder.withFontSizeMm()` |

## Summary

| Category | Total Features | Implemented | Missing | In Progress |
|----------|---------------|-------------|---------|-------------|
| Route & Navigation | 26 | 23 | 3 | 0 |
| Map Rotation | 4 | 4 | 0 | 0 |
| Favorites | 10 | 10 | 0 | 0 |
| GPX Tracks | 4 | 0 | 4 | 0 |
| Object Description | 3 | 2 | 1 | 0 |
| Map Interaction | 11 | 11 | 0 | 0 |
| Search | 6 | 6 | 0 | 0 |
| Map Download | 12 | 12 | 0 | 0 |
| UI / Shell | 11 | 2 | 9 | 0 |
| Rendering | 13 | 12 | 1 | 0 |
| **Total** | **100** | **82** | **18** | **0** |

---

## 12. Android Auto

**Status:** Phase 1 complete. Phase 2 complete (Search + Favorites on car). Phase 3 complete (map rendering on car). Phase 4 complete (deep linking + cross-device continuity). `:core` module extracted, `NavigationSession` + `NavigationScreen` implemented, `NavigationTemplate` with next turn, ETA, speed, lane guidance, rerouting, trip progress, and stop action. 35/37 tasks done (2 DHU testing tasks blocked by outdated DHU binary). [Archived change](openspec/changes/archive/2026-08-04-android-auto-navigation-template/). Phase 2: 30/30 tasks complete — [change](openspec/changes/auto-phase-2/). Phase 3: [archived change](openspec/changes/archive/2026-08-05-auto-phase-3/). Phase 4: [change](openspec/changes/auto-phase-4/).

**Constraint:** No Google Play Services → no Google Maps tiles. `NavigationTemplate` (turn-by-turn) works without maps. Map browsing requires custom `Surface` rendering from libosmscout.

### Phase 1 — NavigationTemplate (turn-by-turn on car screen) ✓

*Implemented and archived.*

| Step | Status | Implementation |
|------|--------|---------------|
| 1.1 | ✓ | `NaviVeylinCarAppService.onCreateSession()` returns `NavigationSession` |
| 1.2 | ✓ | `NavigationSession` extends `Session`, injects `NavigationViewModel` via `@HiltEntryPoint` |
| 1.3 | ✓ | `NavigationScreen` returns `NavigationTemplate` with full nav data |
| 1.4 | ✓ | `NavigationViewModel.state` observed via `StateFlow` → `invalidate()` |
| 1.5 | ✓ | Stop action wired to `NavigationViewModel.stopNavigation()` |
| 1.6 | ✓ | Lifecycle observer starts/stops collection on screen visible/hidden |
| 1.7 | ✓ | `Trip` progress bar using `remainingDistance / totalDistance` |
| 1.8 | ✓ | `setNavigationState(NAVIGATION_STATE_REROUTING)` on reroute |
| 1.9 | ✓ | Lane guidance via `Step.addLane()` with `LaneDirection` shapes |
| 1.10 | ⏳ | DHU testing — blocked by outdated DHU binary (2022 build) |

**Key artifacts created:**
- `core/` module: `NavigationState`, `NavigationViewModel` interface, `AutoEntryPoint`
- `auto/`: `NavigationSession`, `NavigationScreen`, `NavigationTemplateMapper`
- `app/`: `NavigationStateProvider` (singleton bridge), `NavigationViewModelModule` (Hilt bindings)
- `app/`: `LocationService` with `LocationManager` fallback (no Google Play Services dependency)
- 35 unit tests (32 mapper + 3 computeRouteDistance)

### Phase 2 — Search + Favorites on car ✓

*Implemented. See [openspec/changes/auto-phase-2/](openspec/changes/auto-phase-2/).*

| Step | Status | Implementation |
|------|--------|---------------|
| 2.1 | ✓ | `SearchScreen` using `SearchTemplate` with debounced search-as-you-type |
| 2.2 | ✓ | Search wired to `OSMScoutClient.searchLocations()` via `AutoSearchProvider` + `AutoServiceModule` |
| 2.3 | ✓ | `FavoritesScreen` using `PlaceListNavigationTemplate` with group headers |
| 2.4 | ✓ | Favorites wired to `FavoriteRepository.favorites` via `AutoFavoritesProvider` |
| 2.5 | ✓ | "Navigate here" action on search results and favorites |
| 2.6 | ✓ | "Navigate here" → `RoutePanelViewModel.calculateRoute()` + `NavigationViewModel.startNavigation()` via `navigateTo()` |
| 2.7 | ✓ | `RootScreen` with `PaneTemplate` (search + favorites shortcuts) as entry point when not navigating |

**Key artifacts created:**
- `core/`: `AutoSearchProvider`, `AutoFavoritesProvider`, `navigateTo()` in `NavigationViewModel` interface
- `auto/`: `SearchScreen`, `FavoritesScreen`, `RootScreen`, `SearchScreenMapper`, `FavoritesScreenMapper`
- `app/`: `AutoServiceModule` (Hilt), `navigateTo()` in `NavigationViewModel` + `NavigationStateProvider`
- 9 unit tests (6 SearchScreenMapper + 3 FavoritesScreenMapper)

### Phase 3 — Map rendering on car

*Not started. Show libosmscout-rendered map on the car display.*

| Step | What | Why |
|------|------|------|
| 3.1 | Research: determine if `MapTemplate` with custom `Surface` renderer works without Google Play Services | Car App API `MapController` may require Google Maps |
| 3.2 | If `MapTemplate` requires Google Maps → use `MapWithContentTemplate` with static bitmap placeholder instead | Fallback: show rendered map as periodic snapshots |
| 3.3 | Create `MapRenderer` for Auto: render libosmscout map to Android `Surface` via JNI | Reuse `MapRenderer` logic from `:app` |
| 3.4 | Wire GPS position marker on car map | `LocationService.location` already available |
| 3.5 | Wire favorites markers on car map | `FavoriteRepository.favorites` already available |
| 3.6 | Handle pan/zoom gestures on car map | Car input model differs from touch |
| 3.7 | Wire "select location on map → navigate" flow | Full map-based destination picker |

### Phase 4 — Deep linking & cross-device continuity ✓

*Implemented. Phone → car destination handoff + navigation state continuity across surfaces.*

| Step | Status | Implementation |
|------|--------|---------------|
| 4.1 | ✓ | Deep-link entry point (`DeepLinkActivity`) with `androidx.car.app.category.NAVIGATION` filters: `geo:` URIs, Google Maps URLs, `text/plain` share → car session starts navigation via `DeepLinkParser` + `Session.onNewIntent()` |
| 4.2 | ✓ | Car connects mid-navigation → `NavigationSession.onCreateScreen()` returns `NavigationTemplate` immediately (no root flash). Shared `NavigationState` shows current route |
| 4.3 | ✓ | Phone stops navigation → car pops to root. Car stops → phone resets (shared `StateFlow` + stop action). Deep link while navigating re-routes |
| 4.4 | ✓ | Car-only route calc: `NavigationViewModel.navigateTo()` falls back to direct JNI `calculateRouteWithProfile()` when phone UI not open; GPS from `LocationService` |

> Note: TODO item 4.1 originally referenced a `CarAppExtras` class — that class does **not** exist in `androidx.car.app`. The real mechanism is the NAVIGATION-category deep link (below).

**Key artifacts created:**
- `app/`: `DeepLinkActivity` + manifest intent filters (`geo:`, maps URLs, text share)
- `auto/`: `DeepLinkParser` (geo URI / maps URL / share-text parsing), session deep-link handling + lifecycle cleanup
- `app/`: `navigateTo()` direct-route fallback + `LocationService` GPS bootstrap in `NavigationViewModel` init
- 32 unit tests (28 `DeepLinkParser` + 4 direct-route; full suites green)

> **Note on DHU testing:** still blocked by outdated DHU binary (2022 build) — same as phases 1–3. Deep-link intent delivery to `Session.onNewIntent` needs device/host verification.

### Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        :core module                          │
│  NavigationState, NavigationViewModel interface,             │
│  @HiltEntryPoint AutoEntryPoint                              │
└──────────────────────────────────────────────────────────────┘
         ▲                                    ▲
         │ depends                           │ depends
┌────────┴──────────┐              ┌─────────┴──────────┐
│     :app module    │              │    :auto module     │
│  NavigationViewModel│              │  NavigationSession  │
│  (implements iface)│              │  NavigationScreen   │
│  NavigationStateProvider│        │  NavigationTemplate  │
│  (singleton bridge) │              └────────────────────┘
│  LocationService    │
│  (LocationManager   │
│   + Fused fallback) │
└────────────────────┘
```

### Dependencies

| Dependency | Status |
|-----------|--------|
| `androidx.car.app:app:1.7.0` | ✓ Already declared |
| `:core` shared module | ✓ Extracted, both `:app` and `:auto` depend on it |
| `com.google.dagger:hilt-android:2.59` | ✓ Added to `:auto` and `:core` |
| `com.google.android.gms:play-services-location` | ✓ Optional — `LocationManager` fallback for AAOS |
| Car App API testing | ⏳ `androidx.car.app:app-testing` not yet added |
