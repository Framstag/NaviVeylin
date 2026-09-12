## Why

`initMap` re-enters while a previous `initMap` coroutine is still in flight (map path change: returning to the MAIN screen after a map download/update/delete, or a basemap reload triggers `LaunchedEffect(mapPath)` again). The previous launch runs in `viewModelScope` and is *not* cancelled — only `rendererScope` is torn down. The old coroutine has a real suspension point (`favoriteRepository.init` runs `withContext(defaultDispatcher)`), so it can resume *after* the newer `initMap` has re-armed `viewportRestored = false` and apply its own (older map's or default) viewport to `_uiState` and set `viewportRestored = true`. From then on a lifecycle `saveViewport()` or a view-change listener render persists that stale viewport under the *new* map key — the map can also briefly show the old region until the user next gestures. Verified 2026-09-08: the suspension is real (`FavoriteRepository.init` at `MapCanvasViewModel.kt:1209`), single caller `MapCanvasScreen.kt:563` inside `LaunchedEffect(mapPath)`.

## What Changes

- Make `initMap` supersede any previous in-flight `initMap`:
  - Either track the init `Job` and cancel the previous one before launching a new `viewModelScope.launch` (mirrors `LaunchedEffect(mapPath)` semantics — the composable already cancels its own block; only the escape into `viewModelScope` leaks), or
  - Gate every post-suspension state write (viewport restore + `viewportRestored = true`, plus the error/`mapReady` path) on an `initMap` generation counter so a stale coroutine's writes are dropped
- Sync the renderer to the restored viewport during init (`prepareViewport` after renderer creation) so init-time dark/style/favorites renders use the restored viewport instead of the renderer default — eliminates the low-zoom first frame after re-entry (observed: basemap reload + switch back shows low zoom until refresh)
- Add regression tests: a re-entry while the first `initMap` is still suspended must not apply the stale viewport, must not re-arm `viewportRestored`, and a `saveViewport()` in that window must keep the persisted file untouched; the first init-time render must use the restored viewport, not the renderer default
- Additive fix, no behavior change for the normal single-init path. Rollback: remove the job/generation guard.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `viewport-persist`: the "Apply restored viewport" requirement gains a supersession constraint — when `initMap` re-enters while a previous init is still in flight, the stale init must not apply its viewport to map state, re-arm the save guard, or persist under the new map key (new scenario: re-entry during init → stale init writes are dropped).

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — `initMap` job tracking (or generation counter), guard on post-suspension writes
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelViewportRestoreTest.kt` — new re-entry regression tests (reuse the `GatedDispatcher` pattern)
- `openspec/specs/viewport-persist/spec.md` — delta spec, modified requirement + new scenario
- No UI, native, Auto, or dependency impact. Single caller (`MapCanvasScreen.kt:563`).
