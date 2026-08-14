## Context

See proposal.md — Why for motivation. Current state: map renders via Cairo/JNI, search and favorites work, but no GPS position is shown. The render pipeline (`MapRenderer`) uses double-buffered Cairo rendering with tile cache. Marker will be a Compose overlay, keeping the native renderer untouched.

Key constraints:
- Min SDK 26 — FusedLocationProviderClient available without compat layer
- No Google Play Services dependency yet — must be added
- Existing `ProjectionUtils.geoToScreen()` handles Mercator projection for marker positioning
- `MapCanvasViewModel` already manages viewport state and render triggers

## Goals / Non-Goals

**Goals:**
- Show GPS position marker as Compose overlay on map canvas
- Accuracy circle with radius matching GPS horizontal accuracy in meters
- Direction arrow when bearing available, dot when not
- Marker re-projects correctly on pan, zoom, and rotation
- Location permission request with rationale and graceful degradation
- Hilt-injected `LocationService` wrapping FusedLocationProviderClient

**Non-Goals:**
- Turn-by-turn navigation (future change)
- GPS follow mode / auto-centering (future change)
- Route calculation or display (future change)
- GPX track playback (future change)
- Location marker in Cairo/JNI renderer (stays as Compose overlay)
- Follow mode state persistence across app restarts (stays in memory only for now)

## Decisions

### Decision 1: Compose overlay vs Cairo/JNI marker rendering

**Choice:** Compose overlay on top of the Canvas

**Rationale:**
- No JNI/C++ changes needed — keeps native renderer untouched
- Marker can update independently of map re-render (no full Cairo pass needed for GPS updates)
- Simpler to implement and debug
- Easier to animate (rotation, pulsing accuracy circle)
- JavaScout renders marker via Cairo, but that's because JavaFX doesn't have Compose-style overlays

**Alternatives considered:**
- Cairo rendering via `renderWithRouteAndPois()` — would need to pass location data through JNI, trigger full re-render on each GPS update (wasteful), and modify C++ code
- Separate `MapPainter` pass — same downsides

### Decision 2: Location provider

**Choice:** `FusedLocationProviderClient` (Google Play Services)

**Rationale:**
- Standard Android location API with automatic provider selection (GPS + network + WiFi)
- Battery-efficient with `Priority.PRIORITY_HIGH_ACCURACY`
- Handles permission checks internally
- Works on API 26+ without compat libraries

**Alternatives considered:**
- Raw `LocationManager` — more boilerplate, no automatic provider switching, worse battery
- No Google Play Services — would need `LocationManager` fallback, but the app already targets standard Android distribution (not a niche ROM)

### Decision 3: Location service architecture

**Choice:** Hilt `@Singleton` service exposing `StateFlow<Location?>`

**Rationale:**
- Single location source shared across ViewModels (future navigation needs it too)
- `StateFlow` integrates naturally with Compose `collectAsState()`
- Hilt manages lifecycle — service starts/stops with app, not per-screen
- `LocationService` handles FusedLocationProviderClient registration internally

**Flow:**
```
FusedLocationProviderClient → LocationService (StateFlow<Location?>)
                                    ↓
                          MapCanvasViewModel (combines with viewport)
                                    ↓
                          MapCanvasScreen (LocationMarkerOverlay composable)
```

### Decision 4: Marker rendering approach

**Choice:** Custom Compose `Canvas` drawing in `LocationMarkerOverlay` composable

**Rationale:**
- Full control over appearance (accuracy circle alpha, arrow shape, colors)
- Can use `ProjectionUtils.geoToScreen()` for coordinate projection
- Re-renders only when location or viewport changes (no full map re-render)
- Arrow rotation via `rotate()` transform on Canvas

**Marker visual design (following JavaScout conventions):**
- Accuracy circle: fill with 12% opacity accent color, stroke with 40% opacity
- Arrow: filled triangle with rounded tip, ~24dp size, rotated to bearing
- Dot: filled circle, ~10dp diameter, no rotation
- Colors: use MaterialTheme color scheme (primary or accent)

### Decision 5: Permission handling

**Choice:** `rememberLauncherForActivityResult` with `RequestPermission` contract in `MapCanvasScreen`

**Rationale:**
- Standard Compose pattern for runtime permissions
- Launcher tied to composable lifecycle — automatically shows dialog
- `LocationService` checks permission before requesting updates — no crash if denied

**Flow:**
```
MapCanvasScreen composable
  → LaunchedEffect checks permission
  → If denied → launch permission request
  → If granted → LocationService starts updates
  → If denied permanently → show rationale dialog → open Settings
```

### Decision 6: Options button placement

**Choice:** Bottom area of the map screen, near zoom controls

**Rationale:**
- Consistent with zoom controls placement (bottom-right)
- Does not overlap with top-bar search/favorites buttons
- Easy to reach on phone form factor
- JavaScout places similar controls in bottom area

**Alternatives considered:**
- Top bar — would compete with search/favorites/menu buttons
- Floating action button — too prominent for a secondary control

### Decision 7: Options dialog style

**Choice:** Small `DropdownMenu`-style popup anchored to the button, not a full `ModalBottomSheet`

**Rationale:**
- Only one toggle initially — a full bottom sheet is overkill
- Dropdown feels lightweight and dismissible
- Easy to extend with more options later
- Consistent with existing `DropdownMenu` pattern in `MapCanvasScreen`

### Decision 8: Follow mode behavior

**Choice:** Follow mode auto-disengages on manual pan or zoom

**Rationale:**
- Matches JavaScout behavior — user intent to explore overrides auto-center
- Prevents frustrating "map keeps snapping back" experience
- User can re-enable from the options dialog

**Implementation:**
- `MapCanvasViewModel` holds `followMode: Boolean` state
- When follow mode is on, each GPS location update calls `updateCenter()` + `renderMap()`
- `MapCanvasScreen` gesture handlers (pan/zoom) set follow mode to false
- Options dialog toggle sets follow mode directly

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| Google Play Services not available on some devices (e.g., Huawei) | Add `LocationManager` fallback in a follow-up; for now, marker simply won't show |
| Frequent GPS updates cause excessive re-renders | `LocationService` filters updates with min time (1000ms) and min distance (5m); Compose recomposition is cheap for overlay |
| Permission denied → no marker, user confused | Add a subtle hint on the search/favorites menu or a snackbar on first denial |
| Accuracy circle at low zoom levels appears tiny | Clamp minimum radius to 4dp for visibility |
| Arrow rotation jitter from noisy bearing | Apply low-pass filter or use `Location.getBearing()` which is already smoothed by Android |
