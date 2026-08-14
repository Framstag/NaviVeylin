## 1. Projection Utilities

- [x] 1.1 Create `ProjectionUtils.kt` with `computeScale()`, `geoToScreen()`, `screenToGeo()`, `dragDeltaToNewCenter()`, `zoomAtCursor()` — port from JavaScout `ProjectionUtils.java`
- [x] 1.2 Write unit tests for `ProjectionUtils` — verify against JavaScout reference values for known lat/lon/mag/screen combinations
- [x] 1.3 Verify `computeScale()` matches libosmscout's `MercatorProjection` at zoom levels 4–18

## 2. Tile Cache

- [x] 2.1 Create `TileCache.kt` with LRU `LinkedHashMap`, `TileKey` data class, epoch-based invalidation — port from JavaScout `TileCache.java`
- [x] 2.2 Implement `storeTiles()` — split `IntArray` buffer into 256×256 tiles and store
- [x] 2.3 Implement `compose()` — reconstruct full image from cached tiles, report missing count
- [x] 2.4 Write unit tests for `TileCache` — LRU eviction, epoch invalidation, tile grid computation, partial cache hit

## 3. Render Pipeline (MapRenderer)

- [x] 3.1 Create `MapRenderer.kt` with double-buffer pair (`Bitmap` back/front), lock-protected swap, epoch tracking — port from JavaScout `MapRenderer.java`
- [x] 3.2 Implement debounce mechanism: `Channel<RenderJob>` (conflated), coroutine delay loop (50ms pan, 200ms zoom)
- [x] 3.3 Implement `enqueueRenderJob()` — compute overrun dimensions, snapshot current viewport + overlays, push to channel
- [x] 3.4 Implement render loop coroutine — read from channel, call `client.render()` or `client.renderWithRouteAndPois()`, check epoch, store tiles, swap buffers
- [x] 3.5 Implement `trySubRegionBlit()` — compute new viewport position in overrun buffer via Mercator projection, blit visible sub-region if within bounds
- [x] 3.6 Implement zoom placeholder — scale front buffer via `Bitmap.createScaledBitmap()` on zoom change, display immediately, trigger full render
- [x] 3.7 Implement `blitFrontBuffer()` — extract screen-sized region from overrun buffer center, draw to Compose Canvas via `DrawScope.nativeCanvas`
- [x] 3.8 Add render timing: log elapsed time per render, WARNING if >500ms

## 4. ViewModel Integration

- [x] 4.1 Add `MapRenderer` instance and `AtomicLong epoch` to `MapCanvasViewModel`
- [x] 4.2 Replace `renderOnDefault()` with `MapRenderer.enqueueRenderJob()` call
- [x] 4.3 Wire `MapRenderer` front-buffer updates to `MapCanvasUiState.renderedBitmap` via `StateFlow`
- [x] 4.4 Remove old `renderJob` coroutine management (replaced by MapRenderer's internal pipeline)
- [x] 4.5 Wire `saveViewport()` to trigger after debounced render completes (not on every gesture end)
- [x] 4.6 Add `canvasOverrun` config field to `MapCanvasUiState` (default 2.5)

## 5. Gesture Handler Updates

- [x] 5.1 Replace `degPerPx` pan math in `MapCanvasScreen` pointer input with `ProjectionUtils.dragDeltaToNewCenter()`
- [x] 5.2 Replace step-based pinch zoom with `ProjectionUtils.zoomAtCursor()` — keep pinch center geo point fixed
- [x] 5.3 Update long-press handler to use `ProjectionUtils.screenToGeo()` (already uses similar math — align with reference)
- [x] 5.4 Remove `renderMap()` and `saveViewport()` calls from gesture-end handlers (now handled by debounce pipeline)
- [x] 5.5 Add `pointerInput` for scroll-wheel zoom (for emulator/testing)

## 6. Zoom Controls

- [x] 6.1 Add magnification level text display to `ZoomControls` composable (e.g., "Zoom: 12")
- [x] 6.2 Wire zoom button taps through debounced pipeline (not direct `renderMap()`)
- [x] 6.3 Update `ZoomControls` to read magnification from `MapCanvasUiState`

## 7. Build & Verify

- [x] 7.1 Run `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` — verify compilation
- [x] 7.2 Run `./gradlew test` — verify existing unit tests pass
- [x] 7.3 Verify `openspec validate` passes for the change
