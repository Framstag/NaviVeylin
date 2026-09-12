## Why

libosmscout's native tile-based data cache is actively used by every render
call (main database and basemap), but its capacity is left at the library
default of 25 tiles and is not configurable from the app — no JNI surface
exposes `MapService::SetCacheSize`. Investigation also verified the second
candidate optimization (loading data at one zoom level but rendering at the
next) is **already implemented**: pinch zoom previews hold a scaled front
buffer with no renders or data loads during the gesture, the gesture-end
commit is one render after the gesture finishes, and north-up forced renders
(including zoom commits) already go through the incremental tile path that
reuses cached tiles and renders only missing ones. The remaining gap is the
untuned native cache.

## What Changes

- **Native tile data cache configured.** Add a JNI bridge that exposes
  `MapService::SetCacheSize` (libosmscout 1.1.1 already provides it) and apply
  a tuned capacity to both the main database and the basemap `MapService`
  instances. No libosmscout submodule change required; no user-facing setting.
- Observable behavior only — no data format, style, or routing changes.

## Capabilities

### New Capabilities
- `native-tile-data-cache`: Configure the capacity of libosmscout's
  per-database tile data caches (main + basemap) via the JNI bridge so render
  and pan operations reuse previously loaded tile data across frames and zoom
  levels.

### Modified Capabilities

None — the zoom/data decoupling behavior (data loaded post-gesture, north-up
commit renders through the tile path) already exists and is already
specified; no requirement changes.

## Impact

- **JNI bridge** (`libosmscout-client-java`):
  - `OSMScoutClient.java` (local `:osmscout-client-java` module): new
    `setNativeDataCacheSize(int)` native method.
  - `OSMScoutClient.cpp`: store the configured size; apply
    `MapService::SetCacheSize` to every open database and the basemap inside
    the render job before tile data loads, so it also covers databases that
    open asynchronously (map scan, basemap reload).
  - Test stub `FakeOSMScoutClient` gains the new method.
- **App**:
  - `MapCanvasViewModel.initMap`: invoke `setNativeDataCacheSize` after a
    successful `openDatabase`; constant 512 in the companion.
  - Tests: ViewModel init applies the cache size after successful open, and
    skips it on open failure.
- **Unaffected**: libosmscout submodule source (API already present),
  stylesheets, routing, Android Auto surface, renderer tile-path conditions.
- **Open question for design**: exact cache size value (tile count vs memory
  budget) including the interactive 25-tile basemap cache.
