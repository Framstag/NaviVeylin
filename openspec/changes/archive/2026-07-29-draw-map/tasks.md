## 1. Viewport State & Persistence

- [x] 1.1 Create `ViewportState` data class (`centerLat`, `centerLon`, `magnification`) with kotlinx.serialization annotations
- [x] 1.2 Create `ViewportStorage` class — save/load `ViewportState` to/from `filesDir/maps/viewport.json` on `Dispatchers.IO`
- [x] 1.3 Add `ViewportStorage` as Hilt `@Singleton` injectable in `AppModule`
- [x] 1.4 Write unit tests for `ViewportStorage` — save, load, missing file, corrupted JSON

## 2. Map Canvas Screen & ViewModel

- [x] 2.1 Create `MapCanvasViewModel` with Hilt injection — holds `OSMScoutClient`, `ViewportStorage`, exposes `ViewportState` + rendered `ImageBitmap` as `StateFlow`
- [x] 2.2 Create `MapCanvasScreen` composable in `ui/map/` package — accepts map path nav argument, hosts Canvas + zoom controls + loading/error states
- [x] 2.3 Wire `MapCanvasViewModel` to open database on init via `OSMScoutClient.openDatabase()`, handle failure with error state
- [x] 2.4 Add `map_canvas/{mapName}` route to `NavGraph.kt` with nav argument for map path
- [x] 2.5 Update `MainScreen` to navigate to `MapCanvasScreen` when a map is selected (or auto-navigate after download)

## 3. Map Rendering Pipeline

- [x] 3.1 In `MapCanvasViewModel`, implement render coroutine — calls `OSMScoutClient.render()` on `Dispatchers.Default` with current viewport params
- [x] 3.2 Convert `int[]` ARGB result to `android.graphics.Bitmap` (`Config.ARGB_8888`), wrap as Compose `ImageBitmap`
- [x] 3.3 Expose rendered `ImageBitmap` as `StateFlow`; show `CircularProgressIndicator` while rendering, error state with retry on null
- [x] 3.4 In `MapCanvasScreen`, draw `ImageBitmap` onto Compose `Canvas` via `drawImage()` filling available space
- [x] 3.5 Trigger re-render when viewport center or magnification changes (collect viewport StateFlow, launch render)

## 4. Gesture Handling

- [x] 4.1 Add `Modifier.pointerInput` with `detectTransformGestures` to the map Canvas for pan + pinch-zoom
- [x] 4.2 Implement pan — convert drag `Offset` delta to lat/lon change using current magnification's meters-per-pixel, update `ViewportState.centerLat`/`centerLon`
- [x] 4.3 Implement pinch-zoom — map pinch zoom float delta to ±1 magnification step, clamp to 4–18 range
- [x] 4.4 Re-render map on gesture end (not during drag) for performance
- [x] 4.5 Save `ViewportState` to `ViewportStorage` after each gesture completes

## 5. Zoom Controls UI

- [x] 5.1 Create `ZoomControls` composable — `Column` of two small `FloatingActionButton`s in bottom-right corner
- [x] 5.2 Zoom in button: "+" icon, increment magnification, disabled at max (18)
- [x] 5.3 Zoom out button: "−" icon, decrement magnification, disabled at min (4)
- [x] 5.4 Overlay `ZoomControls` on `MapCanvasScreen` using `Box` alignment
- [x] 5.5 Wire button clicks to `MapCanvasViewModel` magnification updates + re-render

## 6. Lifecycle & Viewport Restore

- [x] 6.1 Register `LifecycleObserver` in `MapCanvasViewModel` — save viewport on `ON_PAUSE`
- [x] 6.2 Load viewport from `ViewportStorage` on `MapCanvasScreen` init; use default (Dortmund: 51.5136, 7.4653, mag=8) if no saved state
- [x] 6.3 Verify map restores to last position after app restart

## 7. Build & Verify

- [x] 7.1 Run `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` — verify compilation
- [x] 7.2 Run `./gradlew test` — verify unit tests pass
- [ ] 7.3 Manual smoke test: launch app, navigate to map screen, verify render, pan, zoom, restart at same location

## 8. Implementation Refinements

- [x] 8.1 Add logging to render pipeline (timing, pixel count, null checks)
- [x] 8.2 Bundle stylesheets as Android assets, copy to internal storage via AssetCopier
- [x] 8.3 Route map path in nav argument using Base64 encoding (fix slash-in-path crash)
- [x] 8.4 Use singleton OSMScoutClient from Hilt instead of creating new one (fix build() returning null)
- [x] 8.5 Adjust DPI by render-to-screen scale factor (fix thick lines, small fonts)
- [x] 8.6 Set render resolution to 864x1152 (3:4 portrait) for better quality (reduce scale factor)
- [x] 8.7 Scale bitmap to fill canvas (coerceAtLeast) with background fill (fix white bars)
- [x] 8.8 Add overflow menu to MapCanvasScreen with Download Maps / Settings
- [x] 8.9 Skip MainScreen on startup if maps already installed (check MapStorageManager)
- [x] 8.10 Remove auto-navigation after download (let user download multiple maps)
