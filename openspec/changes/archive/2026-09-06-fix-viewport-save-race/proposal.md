## Why

`MapCanvasViewModel.initMap` wires the view-change listener (`renderer.addViewChangeListener`) BEFORE the restored viewport is applied to `_uiState` and before the first render is requested. The listener persists every completed render via `viewportStorage.save`. During the restore computation (`viewportStorage.load` on IO + `client.getDatabaseBoundingBox` on the JNI thread) the coroutine suspends; on the main thread the composable's `onSizeChanged` → `setScreenSize` can then fire, and because `screenWidth` is still 0 it calls `renderMap()` with the **uninitialized default viewport** (`ViewportState()` = Dortmund 51.5136/7.4653, mag 8). That render completes, `onViewChanged` fires, and the default viewport is persisted — clobbering the restored center.

Observed on emulator (task 4.2, poi-operator-brand-in-results): `viewport-<map>.json` containing only `{"magnification":13.0}` — the center fields are dropped because kotlinx.serialization omits fields equal to their defaults (`encodeDefaults=false`). The next session restores the default center, the map opens at the wrong place, and POI search (which centers on `_uiState.viewport`) searches the wrong area. `ki_processing_failures.log` (2026-09-05) records the same root cause: "app restored viewport from file, then an early onViewChanged saved a NaN center back".

## What Changes

- **Reorder `initMap` (A)**: compute the restored viewport (load + bbox + clamp) BEFORE the renderer is created — during that suspension `mapRenderer` is still null, so an early `setScreenSize`/`renderMap` cannot submit a render with the uninitialized viewport. After the restore, create the renderer, apply the restored viewport to `_uiState`, and only then wire the view-change listener. Everything from renderer creation to listener wiring runs synchronously on the main dispatcher (no suspension), so no interleaving is possible.
- **Guard `ViewportStorage.save` (B)**: reject invalid viewport states (NaN/infinity, out-of-range lat/lon, non-positive or NaN magnification, NaN angle) before writing. `ViewportState.isValid()` is the single choke point — it also covers `saveViewport()` on `ON_PAUSE`, which can fire while `initMap` is still suspended.
- **Regression tests**: `ViewportStorageTest` gains cases for rejected invalid states and for the race scenario (an invalid save must not clobber an existing valid file).
- **New spec requirements** in `viewport-persist`: "Save SHALL reject invalid viewport states" and "Restore SHALL be applied before the view-change listener is wired".

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `viewport-persist`: add two requirements — (1) invalid/uninitialized viewport states are never persisted; (2) the restored viewport is applied before the view-change listener is wired, so no render-triggered save can overwrite it with an uninitialized viewport.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — reorder `initMap`: restore computation moves before renderer creation; restored viewport applied to `_uiState` before listener wiring.
- `app/src/main/java/com/naviveylin/data/ViewportState.kt` — add `isValid()`.
- `app/src/main/java/com/naviveylin/data/ViewportStorage.kt` — `save` rejects invalid states.
- `app/src/test/java/com/naviveylin/data/ViewportStorageTest.kt` — new guard + race tests.
- Additive bug fix; no API, manifest, or native changes. Rollback: revert the change — the pre-change behavior (restored viewport clobbered by an early save) returns, no data migration.
- Affected guidelines: none (no design principle changes; threading/state conventions in `guidelines/Design.md` unchanged).
