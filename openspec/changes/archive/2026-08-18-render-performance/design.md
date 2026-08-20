# Design: Render Performance

## 1. Native node optimization (ways + areas)

**Decision:** `SetOptimizeWayNodes(TransPolygon::fast)` + `SetOptimizeAreaNodes(TransPolygon::fast)` + `SetOptimizeErrorToleranceMm(0.5)` in `OSMScoutClient.cpp` render path (~line 1012, after `MapParameter params;`).

**Alternatives:**
- **A. `TransPolygon::fast` (chosen):** drops intermediate nodes aggressively; biggest render-time win; slight geometry loss only at far zoom-out. Matches libosmscout's own demo defaults.
- **B. `TransPolygon::quality`:** keeps more nodes, less speedup. Good if visual fidelity at low zoom matters more than speed.
- **C. Keep `none` (status quo):** every node transformed + drawn; this is the current bottleneck.

**Rationale:** `fast` is the documented "render speed" mode; navigation zoom levels (mag 14–18) show no visible difference. Tolerance 0.5 mm keeps deviation below a pixel at typical dpi.

**Risk:** Low. Worst case: slightly simplified geometry at very low zoom. Mitigation: tolerance is a single constant; can be raised/lowered or switched to `quality` without API changes.

**Threading/lifecycle:** Runs inside the existing `RunSynchronousJob` on the DB thread — no new threads, no lifecycle impact.

## 2. Multithreaded tile data loading

**Decision:** `searchParam.SetUseMultithreading(true)` in both `loadDbData` lambdas (regular DBs + basemap branch, ~lines 1047 and 1093).

**Alternatives:**
- **A. `SetUseMultithreading(true)` (chosen):** `LoadMissingTileData` parallelizes tile loading across cores; helps cold renders and panning into uncached areas.
- **B. Keep `false` (status quo):** serial tile loading; predictable but slow on multi-core devices.

**Rationale:** Tile loading is I/O + decompression bound and parallelizes well. The JNI render mutex already serializes concurrent renders, so this does not introduce render races.

**Risk:** Medium-low. `LoadMissingTileData` with multithreading uses a worker pool internally; must verify no data races on shared `MapData` (libosmscout handles this internally — the flag is a supported API). Mitigation: run full-suite tests + instrumented render smoke test.

**Threading/lifecycle:** Internal to the DB thread job; no app-side threading changes.

## 3. Double-buffer swap without pixel copies

**Decision:** Replace `getPixels`/`setPixels` round-trip in `MapRenderer.executeRender` (lines 749–751) with `Canvas.drawBitmap(bitmap, 0f, 0f, null)` into the back buffer.

**Alternatives:**
- **A. `Canvas.drawBitmap` (chosen):** GPU-accelerated blit, no IntArray allocation (~10 MB at 1080×2400), no pixel round-trip.
- **B. Keep `getPixels`/`setPixels` (status quo):** allocates `IntArray(w*h)` per frame and round-trips pixels through the JVM — the measured overhead.
- **C. Render directly into the back buffer via JNI:** would need a new JNI entry point accepting a target bitmap; larger change, deferred.

**Rationale:** `drawBitmap` is the standard Android way to blit bitmaps; the back buffer keeps its own storage so the front-buffer independence guarantee (spec: independent front-buffer frame) is preserved.

**Risk:** Low. `drawBitmap` with `null` paint does a straight copy; no scaling involved (same dimensions). Must keep the `bitmap.recycle()` after the blit.

**Threading/lifecycle:** Already inside `bufferLock.withLock` on the render coroutine — unchanged.

## 4. Frame emission only on change

**Decision:** In `executeRender` emission paths, skip the `_frameFlow` `Bitmap.createBitmap` copy when the front buffer content did not change (same epoch + same front buffer reference as the last emission).

**Alternatives:**
- **A. Reuse last emitted frame (chosen):** track last emitted epoch; if unchanged, re-emit the same `FrameState` (or a shallow copy) without allocating a new bitmap.
- **B. Keep copying every emission (status quo):** each `_frameFlow` emission allocates a full-screen ARGB_8888 copy even when nothing changed.
- **C. Share the front buffer directly with Compose:** violates the independent-frame requirement (next render's `setPixels`/blit would mutate the displayed image) — rejected.

**Rationale:** Emission happens on every render completion; unchanged frames are common when GPS ticks are coalesced. Reusing the bitmap avoids GC pressure and allocation stalls on the render thread.

**Risk:** Low. Compose must not mutate the shared bitmap; the overlay only reads it. Verify with existing `MapRenderer` tests that frame identity/epoch semantics hold.

**Threading/lifecycle:** Emission stays inside `bufferLock.withLock`; the shared bitmap is only replaced when a new frame is actually emitted.
