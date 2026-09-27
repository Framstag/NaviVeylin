# Proposal

## Why

One full render allocates four full-size pixel buffers plus a per-pixel conversion pass: the C++
`argbPixels` vector, the Cairo RGB24 surface, the JNI `jintArray`, and the Java `Bitmap` that
`core/MapRenderUtil.kt` creates per call — ~3.7 MB each at the car's 1296×720 overrun frame, at up
to 5 renders/s. The car overlay draw allocates `Paint`, `Path`, `BlurMaskFilter` and
`LinearGradient` objects per *drawn frame* on top of that (~30 fps). Recorded as `TODO.md` §49 while
fixing `fix-aaos-host-crash`, whose design D7 only bounded the render *rate*, never the per-render
cost.

Why now: `TODO.md` §51 ranks this churn as the leading remaining app-process killer on AAOS (app
process death is what takes the templates host down), and §65 cannot separate a leak from legitimate
cache growth while the same window is full of transient allocation. The app-owned half of §49 is the
work that can be done and verified without a device; the native half is deferred deliberately (see
Non-Goals).

## What Changes

- **The render target becomes reusable and owned.** A new `:core` seam owns a bounded, size-keyed
  pool of ARGB_8888 render targets; `MapRenderUtil` renders into a pooled target instead of calling
  `Bitmap.createBitmap` on every call, and callers **release** the target back to the pool instead of
  calling `recycle()`.
- **Both surfaces adopt the pool.** Phone (`app/ui/map/MapRenderer.kt`) and car
  (`auto/AutoMapRenderer.kt`) obtain their render target from the seam. The car holds at least two
  distinct slots, because its `overrunBitmap` stays displayed (blitted) while the next render is in
  flight — reusing a single instance would overwrite the frame on screen.
- **The car overlay draw stops allocating per frame.** `drawGpsMarker`/`drawDestinationMarker` keep
  their `Path`/`Paint`/shader objects in fields and rebuild them only when an input changes (surface
  size, density, dark-presentation flag); the marker geometry/palette contract is unchanged.
- **Reuse becomes observable.** A test-only allocation counter on the seam lets a unit test assert
  that N consecutive renders allocate no new bitmap, and that a frame draw allocates no new draw
  objects.
- **Not BREAKING**: `MapRenderUtil.renderToBitmap(...)` keeps its current signature and behaviour
  for callers that own their result; the pooled entry point is added alongside it. No resource,
  manifest, flavour or Gradle change; no JNI or submodule change. **Rollback**: revert the change's
  commits — the allocate-per-render behaviour returns, no persisted state is involved.

**Scope**: both surfaces (phone `:app` and car `:auto`), through the shared `:core` seam. This is not
an Android-Auto-only change; the seam is shared, and the phone path pays the same per-render
allocation.

## Capabilities

### New Capabilities

None. The change alters the cost of an existing behaviour, not the presence of a new one.

### Modified Capabilities

- `render-performance`: adds the pooled render target contract (reuse, ownership, release instead of
  recycle, both surfaces) and the rule that a displayed or emitted frame is never overwritten by a
  later render — the invariant the previous allocate-per-render behaviour gave for free. Its purpose
  (per-frame buffer overhead on the shared JNI render path) already covers both surfaces, so it is
  not reworded.
- `auto-map-renderer`: adds the allocation-free per-frame marker draw contract for the car renderer
  (draw objects created on first use, rebuilt only when size, density or presentation changes).

Specs read and deliberately **not** changed: `map-render` (its "Safe bitmap lifecycle for
sub-region blit" rule still holds — every frame handed to Compose stays an independent copy; the pool
changes who owns the render target, not that rule), `canvas-overrun` (its "a new overrun buffer is
created" scenario is about the render happening, not about the allocation source), `auto-map-renderer`
(marker geometry, palette and framing contract unchanged; only the draw-object lifetime is),
`gps-render-coalescing` and `marker-render-accuracy` (unaffected).

## Impact

- `core/src/main/java/com/naviveylin/core/MapRenderUtil.kt` — pooled render entry point; the existing
  `renderToBitmap` retained.
- `core/src/main/java/com/naviveylin/core/RenderBitmapPool.kt` (new) — bounded size-keyed pool,
  ownership contract, test-only allocation counter.
- `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` — `backBuffer`/`frontBuffer` obtain their
  storage from the pool and release instead of recycle (`:478-479`, `:837-871`); the tile path's own
  composition bitmap is untouched.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — only if a buffer is released on
  map-screen teardown; to be confirmed in design.
- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` — two pool slots instead of the
  allocate/recycle pair (`:1262-1263`, `:1489-1490`); `drawGpsMarker` (`:1563`) and
  `drawDestinationMarker` (`:1703`) hoist their draw objects.
- Tests: `app/src/test/java/com/naviveylin/ui/map/MapRenderUtilTest.kt` (existing, 7 cases — extended
  for the pooled path), a new `:core` pool test, and car/phone allocation assertions where a renderer
  test harness already exists (`auto/src/test/java/com/naviveylin/auto/RendererTestRule.kt`).
- `guidelines/MapRendering.md` §2 (Bitmap Lifecycle) — the reuse contract and "release, never
  recycle, a pooled target" rule; §14 (Android Auto renderer) — the two-slot rule for the displayed
  overrun frame.
- `guidelines/Design.md` §12 (single source of truth) — the pool lives in `:core`, one owner of the
  reuse rule; §4 (threading) — the pool's lock discipline, since renders run on a background
  dispatcher while a surface draw may read the displayed slot.
- `TODO.md` §49 — closed as "app-side half done" with the native hand-off recorded as the remaining
  follow-up; §65's measurement gains a quiet-allocation baseline to compare against.
- No manifest, resource, dependency or Gradle change. No `app/src/main/cpp/libosmscout` change, no
  gitlink bump, no `:osmscout-client-java` override.

## Non-Goals

- **No native caller-supplied pixel buffer.** Removing the C++ `argbPixels` vector, the Cairo surface
  and the JNI `jintArray` needs a new JNI entry point (submodule commit on `naviveylin-local` +
  gitlink bump + bridge override). It is the second half of `TODO.md` §49 and stays open there.
- No change to render rates, debounce, overrun math, blit offsets or follow framing.
- No change to the marker's geometry, size or palette.
- No device measurement in this change: with no device attached and the AAOS AVD unusable for
  headless runs (`TODO.md` §40.45), the `dumpsys meminfo` before/after comparison is recorded as an
  open task for a session that has a head unit or an interactive emulator.
