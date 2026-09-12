## Context

libosmscout 1.1.1 `MapService` owns a `DataTileCache` (default capacity 25 tiles,
LRU, lazy eviction). The bridge's `renderWithRouteAndPois` already drives the
tile-data pipeline (`LookupTiles` → `LoadMissingTileData` →
`AddTileDataToMapData` → `DrawMap`) for every render — main database and
basemap each hold their own `MapService`/cache. No JNI surface exists to tune
capacity, and no code calls `SetCacheSize`. See proposal.md — Why.

**Design discovery during implementation:** the second proposed optimization
(data/render zoom decoupling) was already fully in place, so the design below
covers only the cache configuration. Evidence: `MapRenderer.executeRender`
tile-path condition (`TILES && (angle == 0 || !forceFullRender)`) routes every
north-up render — including forced zoom commits from `onRenderRequested` —
through the incremental tile path (pinned by `TileCacheRenderTest.
tilePathRendersMissingTilesAndReusesCachedTiles`); pinch gestures render
nothing during the gesture (scaled front buffer, `zoom-transition-scaling`)
and commit once at gesture end. No renderer changes are part of this change.

## Goals / Non-Goals

**Goals:**
- Expose a JNI knob for the native tile data cache capacity and apply a tuned
  size to every open database (regional + basemap) before its data loads.

**Non-Goals:**
- No user-facing cache setting (persisted constant, spec: "tuned constant").
- No change to renderer tile-path conditions, zoom gesture handling, or
  `DIRECT` mode (verified already decoupled; untouched).
- No libosmscout core patches — `SetCacheSize` already exists; changes stay in
  the `libosmscout-client-java` bridge + app.
- No change to eviction policy or idle flush (`DBThread::FlushCaches`).

## Decisions

### D1: Native cache capacity configured via JNI, applied in the render job

Add `OSMScoutClient.setNativeDataCacheSize(int)` (native) and store the value
in `ClientData` (`tileDataCacheSize`). Inside `renderWithRouteAndPois`'s
existing `RunSynchronousJob`, a pre-pass before the data-loading loop calls
`SetCacheSize` on every open database's `MapService` — iterating `databases`
and explicitly covering `basemapDatabase`, since the basemap renders through
a separate code block when no regional database is loaded (it is NOT part of
the `loadDbData` lambda in that case).

**Why apply in the render job instead of at the open call sites:** open
database registration and basemap reload both complete asynchronously on the
DBThread worker (see `initMap` — `openDatabase` returns after registering the
path). A one-shot apply right after `openDatabase` can race the scan and miss
the just-opened instances. Applying per render job is idempotent
(`SetCacheSize` with the same size is a plain assignment — no cleanup,
negligible lock cost next to data loading) and self-heals for every database
that opens later, including basemap reloads. The JNI setter stays the
configuration surface for tuning/tests.

**Application points** (all idempotent):
- C++ render job (`loadDbData` lambda) — authoritative application.
- Kotlin `MapCanvasViewModel.initMap` after `openDatabase` success — records
  the configured size in the client and matches spec wording "applied when a
  database is opened".

**Size constant:** `NATIVE_TILE_DATA_CACHE_SIZE = 512` in
`MapCanvasViewModel` (companion). Viewport ≈ 2–6 tiles at overrun size; 512
holds panning neighborhoods across several zoom levels while remaining small
next to the app's 200-tile bitmap cache budget. Tuning documented; per spec it
exceeds the library default by a meaningful margin and is a perf-only knob.

Alternative considered: applying once in `reloadBasemap`/`openDatabase` JNI
only (no render-job application) — rejected because the async scan race
leaves regional databases at the default 25 until a later explicit call.
Patching `libosmscout-client` `DBThread` to set the size at instance open was
also considered — automatic, but reaches into the submodule core for a config
concern better kept in the already-local bridge; rejected.

### D2: Test strategy

- `FakeOSMScoutClient` gains matching `setNativeDataCacheSize` no-op (JNI
  stub requirement — AGENTS.md classloader rules apply).
- ViewModel tests (`MapCanvasViewModelStyleTest`):
  - `initMap` applies the cache size after a successful `openDatabase`;
  - open failure skips the configuration.

## Risks / Trade-offs

- [512 tiles may be off target] → constant is single-point tunable; verify
  with `GetCacheSize`/`GetCurrentCacheSize` logging and tile miss logs.
- [New JNI method misses a fake → test-classloader break] → added to
  `FakeOSMScoutClient` in the same commit (AGENTS.md stub rules).
- [Per-render `SetCacheSize` mutex cost] → same-size call is an assignment;
  negligible versus tile data loading that follows.
- [Idle cache flush (`FlushCaches`) empties the cache regardless of size] →
  unchanged behavior; size still pays off within active sessions.

## Migration Plan

Internal change, no data migration. Rollback: revert the JNI method and the
initMap call site; both additive. Verify with `:app:assembleMobileDebug` +
`./gradlew test` per guidelines/Build.md.

## Open Questions

- Exact cache size tuning (512 provisional — measure tile-miss and render
  times on device with `logcat -s NaviVeylin TileCache`).
