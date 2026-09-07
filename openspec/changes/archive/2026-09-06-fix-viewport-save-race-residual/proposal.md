## Why

`MapCanvasViewModel.saveViewport()` persists `_uiState.value.viewport` directly on `ON_PAUSE`. If the app is backgrounded while `initMap` is still suspended at `viewportStorage.load` (before the restored viewport is applied to state), the default viewport is written to disk. The `isValid()` guard in `ViewportStorage.save` passes — the default center is valid-looking — so the previously restored viewport is clobbered. Rare (background within ~100 ms of launch) but the same bug class as the already-fixed `fix-viewport-save-race` (which covered the render path, not the lifecycle-save path).

## What Changes

- Add a `viewportRestored` flag to `MapCanvasViewModel`:
  - Reset to `false` at the top of `initMap` (synchronously, before the coroutine launches)
  - Set to `true` immediately after the restored viewport is applied to `_uiState` (the `copy(viewport = vp, ...)` line)
- Guard `saveViewport()`: no-op (with a debug log) until `viewportRestored` is set, so a lifecycle save during the restore window can never persist the default viewport
- Add regression tests in `MapCanvasViewModelViewportRestoreTest`:
  - `saveViewport()` during the restore suspension does not write (seeded file keeps the restored center)
  - `saveViewport()` after the restore completes persists the current viewport
- Additive change, no breaking behavior. Rollback: remove the flag and guard.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `viewport-persist`: the "Save viewport state" requirement gains the constraint that a lifecycle save before the viewport restore is applied must not overwrite the persisted file (new scenario: pause during restore → no clobber).

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — flag field, reset in `initMap`, set after restore, guard in `saveViewport()`
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelViewportRestoreTest.kt` — 2 new tests (reuse the existing `GatedDispatcher` pattern)
- `openspec/specs/viewport-persist/spec.md` — delta spec (MODIFIED requirement + scenario)
- No UI, native, or Auto impact. Single `saveViewport()` caller (`MapCanvasScreen.kt:587`).
