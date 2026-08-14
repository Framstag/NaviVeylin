## Context

NaviVeylin currently shows a placeholder grid on `MainScreen` with an overflow menu to navigate to the map download screen. Downloaded maps exist on disk at `filesDir/maps/<map-name>/` but are never rendered. The `OSMScoutClient` JNI bridge provides `render()` and `renderWithRoute()` methods that return ARGB pixel buffers from libosmscout's Cairo backend.

The app uses Jetpack Compose with a single-Activity architecture, Hilt DI, and Jetpack Navigation. No map rendering, gesture handling, or viewport persistence exists yet.

## Goals / Non-Goals

**Goals:**
- Render a downloaded libosmscout map onto a Compose `Canvas` using the JNI `render()` method
- Support touch-based pan (single-finger drag) and pinch-to-zoom gestures
- Display floating zoom in/out buttons overlaid on the map
- Persist viewport state (center lat/lon, zoom level) to a JSON file on pause/stop
- Restore viewport state on app startup so the map opens at the last location

**Non-Goals:**
- Map rotation (angle stays 0, north-up)
- Route rendering or POI markers (deferred to later changes)
- Smooth animated zoom transitions (step zoom is acceptable)
- Multiple map styles or layer toggling
- Offline tile caching beyond what libosmscout provides

## Decisions

### 1. Render via JNI `render()` → `ImageBitmap` → Compose `Canvas`

Call `OSMScoutClient.render()` on a background thread to produce an `int[]` ARGB buffer, convert to `android.graphics.Bitmap` via `Bitmap.createBitmap()` with `Config.ARGB_8888`, then draw that bitmap onto a Compose `Canvas` using `drawImage()`.

`render()` already exists in the JNI bridge — no new native code needed. Cairo rendering is CPU-bound; offloading to `Dispatchers.Default` keeps UI thread free.

### 2. Viewport state as data class with JSON persistence

Define a `ViewportState` data class (`centerLat`, `centerLon`, `magnification`). Serialize to/from JSON using kotlinx.serialization. Store in `filesDir/maps/viewport.json`.

kotlinx.serialization is already a transitive dependency. JSON is human-readable for debugging. Save on `ON_PAUSE` via `LifecycleEventObserver`.

### 3. Zoom level as magnification index, not float

Use libosmscout's `magnification` integer directly. Zoom buttons increment/decrement by 1. Range: 4–18.

Avoids float-to-magnification conversion complexity. libosmscout's magnification levels correspond to discrete zoom steps.

### 4. Gesture handling via Compose `pointerInput` modifier

Use `Modifier.pointerInput` with `detectTransformGestures` for simultaneous pan + pinch-zoom. Render and persist viewport on each gesture event; previous render job is cancelled to avoid backlog.

### 5. Zoom buttons as FAB pair

Two stacked `FilledIconButton`s in bottom-right corner. "+" for zoom in, "−" for zoom out. Disabled at min/max magnification. Each button triggers render + persist.

### 6. New `MapCanvasScreen` composable, separate from `MainScreen`

`MapCanvasScreen` in `ui/map/` package. `MainScreen` is a hub/launcher. Nav route `map_canvas/{mapPath}` with Base64-encoded path argument.

### 7. DPI scaling for correct visual sizing

Adjust DPI passed to libosmscout by ratio of render width to screen width: `adjustedDpi = actualDpi / (screenWidth / renderWidth)`.

Without adjustment, libosmscout draws fonts and lines for the actual screen DPI (e.g., 420). When the low-res render is scaled 2-3x to fill the screen, everything looks 2-3x too thick.

### 8. Render resolution and aspect ratio

Render at 864×1152 (3:4 portrait aspect). This matches phone portrait aspect closely, keeping scale factor below 1.7x on typical phones (vs 3.3x with 4:3 landscape).

Higher resolutions improve quality but increase render time proportionally. 864×1152 (~1M pixels) is a reasonable balance. Full screen (1080×1920, ~2M pixels) doubles render time.

### 9. Singleton OSMScoutClient via Hilt

Single `OSMScoutClient` instance created at app startup via Hilt `@Singleton` in `MapDownloadModule`. `MapCanvasViewModel` injects this existing client.

The C++ `activeClient` global in the JNI bridge only allows one client at a time. `MapDownloadModule` was already creating a singleton for `MapDownloadManager`. Creating a second client caused `build()` to return null.

### 10. Stylesheets bundled as Android assets

`libosmscout/stylesheets/` copied to `app/src/main/assets/stylesheets/`. On first launch, `AssetCopier` copies them from assets to `filesDir/stylesheets/`.

libosmscout requires `.oss` stylesheet files at a filesystem path. Android assets cannot provide a direct filesystem path; files must be extracted to internal storage.

### 11. Navigation route encoding for file paths

Base64-encode the map filesystem path when passing as a Navigation Compose route argument. Decode on the receiving end.

Map paths contain `/` characters which Navigation treats as path segment separators. URL encoding (`%2F`) is decoded by Navigation before route matching, still causing splits. Base64 produces only URL-safe characters.

### 12. Startup routing based on installed maps

`NavGraph` checks `MapStorageManager.mapsRootDir` for installed map subdirectories at startup. If maps exist, start destination is `MapCanvasScreen` for the first map. Otherwise, `MainScreen` (welcome + "Get Maps") is shown.

### 13. Canvas fill strategy

Scale rendered bitmap to fill entire canvas (`coerceAtLeast`), cropping edges that exceed screen bounds. Fill remaining area with surface background color.

Map apps typically fill screen edge-to-edge. Cropping is less distracting than letterbox bars. User can pan to see cropped areas.

### 14. Overflow menu on map screen

Top-right overflow menu (`⋮`) on `MapCanvasScreen` with "Download Maps" and "Settings" options, matching `MainScreen`.

### 15. Default viewport center

Default center: Dortmund, Germany (51.5136, 7.4653). Default magnification: 8. Previous default (0, 0) was Atlantic Ocean — no map data visible.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| Render is slow on low-end devices (CPU-bound Cairo) | Run on `Dispatchers.Default`; show loading indicator; cancel previous render job on new gesture |
| Pinch-to-zoom + pan on same gesture detector may feel janky | `detectTransformGestures` handles both; render only on gesture event (not during drag) |
| Viewport JSON file may become stale if app crashes before `onPause` | Also save on each zoom button press and gesture event |
| Large map databases may cause OOM on render | Cap render dimensions to 864×1152 |
| Stylesheets copied from assets may be out of date | AssetCopier only copies if destination doesn't exist; delete `filesDir/stylesheets/` to refresh |
| Base64 nav arguments produce long, unreadable URLs | Acceptable trade-off — route matching is reliable |
| Singleton client is never closed | Client lives for app lifetime; `onCleared()` not called; acceptable since Hilt manages lifecycle |

## Open Questions

- Should render resolution be dynamic based on device screen size and performance?
- Should we add tile caching to avoid full re-render on every pan?
- What zoom level range should we expose? Currently 4–18.
- Should the map re-render during drag for smoother feel, or only on gesture end?
