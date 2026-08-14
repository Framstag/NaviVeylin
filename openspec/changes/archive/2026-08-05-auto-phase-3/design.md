## Context

Phases 1-2 established the Android Auto architecture: `NavigationSession` manages a screen stack with `RootScreen` (PaneTemplate), `SearchScreen` (SearchTemplate), `FavoritesScreen` (PlaceListTemplate), and `NavigationScreen` (NavigationTemplate). The `:auto` module depends on `:core` for `NavigationViewModel` interface and `NavigationState`, and on `:app` via `NavigationStateProvider` bridge for state observation and navigation control.

Phase 3 adds map rendering on the car display. The phone-side `MapRenderer` already renders libosmscout maps to Android `Bitmap` via `OSMScoutClient.renderWithRouteAndPois()`. The car needs a similar renderer that writes to a `Surface` provided by Android Auto's `MapController` (from `MapTemplate`). Google Play Services is available for the `MapController` API.

See `proposal.md` for motivation and `specs/` for requirements.

## Goals / Non-Goals

**Goals:**
- `MapTemplate` with custom `Surface` renderer showing libosmscout map tiles on car display
- `AutoMapRenderer` that renders map to `Surface` via `OSMScoutClient.renderWithRouteAndPois()`, reusing phone-side render logic
- GPS position marker and favorites markers on car map
- Pan/zoom/rotation gestures via rotary controller and touch
- Map-based destination picker: select location → navigate
- `MapScreen` composable integrated into existing screen stack

**Non-Goals:**
- No changes to phone-side `MapRenderer` (may extract render utility, but no behavioral changes)
- No tile caching on car (re-render on each viewport change — acceptable for car display refresh rate)
- No voice guidance (deferred)
- No favorites editing on car map (read-only markers)

## Decisions

### Decision: `MapTemplate` with `MapController` Surface renderer
**Why:** `MapTemplate` provides a full-screen map surface via `MapController.SurfaceCallback`. We render libosmscout tiles to this `Surface` using a background render loop. Alternative: `MapWithContentTemplate` with periodic bitmap snapshots — but that adds latency and flicker. Since Google Play Services is available, `MapTemplate` works directly.

### Decision: Extract render-to-bitmap utility from phone `MapRenderer`
**Why:** The phone `MapRenderer` has complex debounce, double-buffering, tile cache, and sub-region blit logic. The car renderer needs only the core render call: `client.renderWithRouteAndPois()` → `int[]` pixels → `Bitmap`. Extract a `MapRenderUtil` object with a single `renderToBitmap()` function that takes viewport params + overlays and returns a `Bitmap`. Both phone and car renderers can use it. This avoids duplicating the JNI call setup.

### Decision: `AutoMapRenderer` runs render loop on `Dispatchers.Default`
**Why:** Rendering is CPU-bound (Cairo on native thread). `Dispatchers.Default` matches the phone renderer's approach. The `Surface` is written to from the render thread via `Surface.lockCanvas()`/`unlockCanvasAndPost()`. A `StateFlow<RenderRequest>` drives re-renders on viewport change, with 100ms debounce (car display is lower refresh than phone).

### Decision: Gesture handling via `MapController` input listeners
**Why:** `MapController` provides `setOnMapCenterChangedListener()`, `setOnZoomChangedListener()`, and `setOnMapClickedListener()` for touch/rotary input. These map directly to viewport state changes. No need for custom gesture detection — the Car App API handles input model abstraction (touch vs rotary).

### Decision: `MapScreen` as new Screen class in `:auto`
**Why:** Follows the existing pattern: `NavigationScreen`, `SearchScreen`, `FavoritesScreen` are all `Screen` subclasses. `MapScreen` holds an `AutoMapRenderer` instance and observes `NavigationViewModel.state` for GPS position updates. Screen transitions use `ScreenManager.push()`/`popToRoot()` — same as existing screens.

### Decision: Destination picker uses `MapController.setOnMapClickedListener()`
**Why:** `MapController` provides click callback with lat/lon. On selection, show a `Pane` overlay with coordinates + "Navigate here" action. "Navigate here" calls `NavigationViewModel.navigateTo()` (existing bridge from Phase 2). This keeps the flow consistent with search/favorites destination picking.

### Decision: GPS and favorites markers rendered natively via `renderWithRouteAndPois()`
**Why:** The JNI render method already supports `favoriteLats`/`favoriteLons` and `gpsMarkerLat`/`gpsMarkerLon` parameters. No need for a separate marker overlay layer — markers are baked into the rendered bitmap. This matches the phone renderer's approach.

### Decision: `NavigationSession` adds "Map" option to `RootScreen`
**Why:** `RootScreen` currently shows Search and Favorites rows. Adding a "Map" row follows the same pattern. When selected, `screenManager.push(MapScreen)`. When navigation starts from map, `NavigationSession`'s existing `isNavigating` observer pushes `NavigationScreen` and pops the map screen.

## Risks / Trade-offs

- **[Risk] Car display resolution varies widely** → Mitigation: `AutoMapRenderer` queries `Surface` dimensions from `SurfaceCallback.onSurfaceCreated()` and renders at that resolution. No hardcoded sizes.
- **[Risk] Render latency on car hardware** → Mitigation: 100ms debounce on viewport changes. Car displays typically run at 30fps, so 100ms refresh is acceptable. If rendering takes >200ms, show a loading indicator.
- **[Risk] `MapController` Surface lifecycle** → Mitigation: `SurfaceCallback.onSurfaceCreated()`/`onSurfaceDestroyed()` controls render loop start/stop. Match to `Screen` lifecycle (onStart/onStop).
- **[Risk] Rotary controller focus management** → Mitigation: `MapController` handles focus internally. Map actions (zoom, re-center) are exposed as `ActionStrip` items for non-touch access.
- **[Risk] `MapRenderUtil` extraction may break phone renderer** → Mitigation: Extract as pure function, no state. Phone `MapRenderer` continues to use its own debounce/buffer/cache logic — only the JNI call is shared.

## Migration Plan

1. Extract `MapRenderUtil.renderToBitmap()` from phone `MapRenderer` (shared utility)
2. Create `AutoMapRenderer` in `:auto` — render loop, Surface management, debounce
3. Create `MapScreen` composable — `MapTemplate` + `MapController` + `AutoMapRenderer`
4. Wire GPS position → `AutoMapRenderer.setGpsMarker()` via `NavigationViewModel.state`
5. Wire favorites → `AutoMapRenderer.setFavoriteLocations()` via `AutoFavoritesProvider`
6. Add gesture handling via `MapController` listeners → viewport state updates
7. Add destination picker: map click → details overlay → "Navigate here" → `navigateTo()`
8. Update `RootScreen` with "Map" option
9. Update `NavigationSession` for map screen transitions
10. Unit tests for `AutoMapRenderer`, `MapScreen`, gesture handling

## Open Questions

- None. Specs, approach, and task breakdown are clear.
