## Context

Current map rendering pipeline (see `MapCanvasViewModel.renderOnDefault()`):

1. Gesture ends → `renderMap()` called
2. Cancels previous `renderJob` coroutine
3. Launches new coroutine on `Dispatchers.Default`
4. Calls `client.render()` at fixed 864×1152 pixels
5. Converts `IntArray` → `Bitmap` → `ImageBitmap`
6. Stores in `MapCanvasUiState.renderedBitmap`
7. Compose `Canvas` draws bitmap scaled to fill screen

Problems: no double buffering (blank frame between renders), no overrun (every pan re-renders), no tile cache (every zoom re-renders entire viewport), crude pan math (degPerPx heuristic), no zoom-at-cursor, no debounce (rapid gestures queue multiple renders).

JavaScout's `MapRenderer.java` solves all of these. This design ports that architecture to Kotlin/Compose.

## Goals / Non-Goals

**Goals:**
- Port JavaScout's `MapRenderer` double-buffering + debounce + sub-region blit to Compose
- Port `ProjectionUtils` Mercator math (geoToScreen, screenToGeo, dragDeltaToNewCenter, zoomAtCursor)
- Port `TileCache` LRU tile cache with epoch invalidation
- Wire gesture handlers in `MapCanvasScreen` to use new pipeline
- Update `MapCanvasViewModel` to delegate to `MapRenderer`
- Update `ZoomControls` to show magnification level

**Non-Goals:**
- No changes to native JNI layer (`OSMScoutClient` API unchanged)
- No changes to map database loading or stylesheet setup
- No rotation support (angle stays 0.0 — deferred)
- No route/track overlay rendering (existing `renderWithRouteAndPois` path preserved)
- No current-location marker (deferred)

## Decisions

### Decision 1: Dedicated render coroutine vs. render thread

JavaScout uses a dedicated `Thread` with a `wait/notify` loop. In Kotlin/Compose, use a dedicated coroutine on `Dispatchers.Default` with a `Channel`-based job queue instead.

**Rationale:** Coroutines integrate naturally with ViewModel `viewModelScope` cancellation. `Channel` with `CONFLATED` capacity matches JavaScout's `AtomicReference<RenderJob>` pattern (only latest job matters). Avoids raw thread management.

**Alternatives considered:**
- Raw `Thread` like JavaScout — works but doesn't integrate with structured concurrency
- `flow` + `debounce` — simpler but loses fine-grained control over epoch checking and retry

### Decision 2: Double buffer as Bitmap pair, not ImageBitmap

Store back/front buffers as `android.graphics.Bitmap` (ARGB_8888). Convert to Compose `ImageBitmap` only when blitting to Canvas.

**Rationale:** `Bitmap` is the native type returned by the JNI layer. Converting to `ImageBitmap` on every frame would be wasteful. Keep the hot path (buffer swap) in `Bitmap` space. Convert once when the buffer is ready for display.

**Alternatives considered:**
- `IntArray` pair — avoids Bitmap allocation but loses easy conversion to Compose Canvas
- `ImageBitmap` pair — requires conversion from IntArray on every render, adds GC pressure

### Decision 3: Sub-region blit via Canvas drawImage with source/dest rects

Compose `Canvas.drawImage()` supports `dstOffset` and `dstSize` but not source sub-rect. Use `DrawScope` with manual coordinate math: compute the visible sub-region of the overrun buffer and draw it centered.

**Rationale:** Compose's `Canvas` doesn't expose `drawImage(sourceRect, destRect)` like JavaFX. The workaround: compute the offset into the overrun buffer and draw the visible portion using `drawImage(image, dstOffset, dstSize)` with the image pre-cropped or by using Android `Canvas.drawBitmap(bitmap, srcRect, dstRect, paint)` via `Canvas.nativeCanvas`.

**Alternatives considered:**
- Pre-crop the overrun buffer to screen size before converting to ImageBitmap — simpler but adds a copy per frame
- Use `Modifier.graphicsLayer` with translation — doesn't compose well with gesture detection

### Decision 4: Tile cache as pure Kotlin class

Port `TileCache.java` directly to Kotlin with `LinkedHashMap` LRU, `TileKey` data class, epoch-based invalidation.

**Rationale:** The tile cache is self-contained with no Android dependencies. Pure Kotlin keeps it testable. The LRU eviction via `LinkedHashMap` access-order mode is identical to JavaScout.

### Decision 5: Debounce via coroutine delay loop, not separate thread

Replace JavaScout's `Thread`-based debounce loop with a coroutine that reads from a `MutableStateFlow<DebounceCommand>` and applies `delay()` based on command type.

**Rationale:** Avoids raw thread management. Coroutine cancellation handles cleanup. `StateFlow` with `conflated` behavior matches JavaScout's `AtomicReference<PendingRender>`.

### Decision 6: Epoch as AtomicLong in ViewModel

Keep epoch counter in `MapCanvasViewModel` as an `AtomicLong`. Increment on every viewport change (pan, zoom, overlay update). Pass to `MapRenderer` for stale render detection.

**Rationale:** Epoch is shared state between gesture handlers (Compose thread) and render pipeline (background coroutine). `AtomicLong` provides safe cross-coroutine visibility without locks.

### Decision 7: Zoom placeholder via Compose Canvas scaling

When zoom changes, scale the current front buffer bitmap using `Matrix` and draw it on Canvas. Use `Bitmap.createScaledBitmap()` for the placeholder, then replace with full render when ready.

**Rationale:** `createScaledBitmap()` is fast (uses bilinear filtering) and matches JavaScout's `gc.drawImage()` with scaled source/dest rects. The placeholder is temporary and will be replaced.

## Risks / Trade-offs

- **[Performance] Overrun buffer at 2.5× uses 6.25× pixels** → On a 1080×1920 screen, the overrun buffer is 2700×4800 = 12.96M pixels. At 4 bytes/pixel = ~50MB per buffer. Two buffers = ~100MB. Mitigation: make overrun configurable, default to 2.5×, allow reduction on low-memory devices.
- **[Complexity] Sub-region blit math is error-prone** → The coordinate transform between overrun buffer space and screen space must match Mercator projection exactly. Mitigation: unit-test `ProjectionUtils` against JavaScout's reference implementation.
- **[Latency] Tile cache adds overhead on first render** → Splitting the buffer into tiles and storing them takes CPU time. Mitigation: only store tiles after the render completes (not on the critical path to display). The first render still goes directly to screen.
- **[Memory] Tile cache at 200 tiles × 256×256 × 4 bytes = ~50MB** → Plus the double buffers (~100MB). Total ~150MB peak. Mitigation: reduce max tiles on low-RAM devices (detect via `ActivityManager.getMemoryClass()`).
- **[Compatibility] Compose Canvas lacks source-rect drawImage** → Must use Android `Canvas.drawBitmap()` via `DrawScope.nativeCanvas`. This ties us to Android-specific Canvas API. Mitigation: wrap in an extension function, keep Compose Canvas for non-map overlays.
