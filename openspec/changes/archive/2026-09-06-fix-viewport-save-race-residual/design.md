## Context

`MapCanvasViewModel.saveViewport()` (line 2338) persists `_uiState.value.viewport` directly on `ON_PAUSE`. `initMap` (line 1114) runs in a `viewModelScope.launch` and suspends at `viewportStorage.load` (line 1180) and `client.getDatabaseBoundingBox` (line 1182) before applying the restored viewport to state (line 1224). If `ON_PAUSE` fires inside that window, the default viewport is written. `ViewportStorage.save`'s `isValid()` guard does not catch it — the default center (e.g. 0,0 or the global default) is valid-looking. The prior change `fix-viewport-save-race` reordered `initMap` so the restore is applied before the renderer exists and the view-change listener is wired, but the lifecycle-save path does not go through the renderer, so it was not covered.

## Goals / Non-Goals

**Goals:**
- A lifecycle save during the restore window must never overwrite the persisted viewport
- Keep the existing behavior: after the restore is applied, `saveViewport()` persists normally
- Regression tests for both sides of the flag

**Non-Goals:**
- Changing the render-path race (already fixed by `fix-viewport-save-race`)
- Changing `ViewportStorage` or `ViewportState.isValid()` semantics
- Touching the view-change listener persistence path

## Decisions

### D1: `viewportRestored` flag guard in `saveViewport()` (chosen)

Add `private var viewportRestored = false` to `MapCanvasViewModel`. Reset to `false` at the top of `initMap` (synchronously, before the coroutine launches — `initMap` is called on the main thread and the coroutine starts on `Main.immediate`, so the reset is visible before any suspension). Set to `true` immediately after `_uiState.value = _uiState.value.copy(viewport = vp, isLoading = false)` (line 1224). Guard `saveViewport()`:

```kotlin
fun saveViewport() {
    if (!viewportRestored) {
        Log.d(TAG, "saveViewport: skipped, viewport restore not yet applied")
        return
    }
    viewModelScope.launch {
        viewportStorage.save(currentMapKey ?: "default", _uiState.value.viewport)
    }
}
```

All flag reads/writes happen on the main thread (initMap call site, coroutine body on Main.immediate, ON_PAUSE observer), so no synchronization needed.

**Alternatives considered:**

- **A1 — Flag guard (chosen):** Minimal, explicit, matches the TODO's fix candidate. The flag is set exactly when the restore becomes visible to `saveViewport`. Failure paths (DB open error → `return@launch` before the flag) leave the flag false, so the old persisted viewport survives — correct.
- **A2 — Route `saveViewport()` through the renderer:** Make the lifecycle save go through the same path as the view-change listener (which is wired only after the restore). Rejected: the renderer may be null during the window (it is created at line 1211, after the load suspension), and the renderer's viewport may lag the UI state; the flag is simpler and covers the exact window.
- **A3 — State-based check (e.g. compare against default):** Skip the save when the current viewport equals the default. Rejected: fragile — a user could legitimately be at the default center, and the "default" is not a single canonical value (global default vs bbox fallback). The flag is unambiguous.

### D2: Tests reuse the `GatedDispatcher` pattern

The existing `MapCanvasViewModelViewportRestoreTest` already gates `viewportStorage.ioDispatcher` to hold `initMap` suspended at the load. Two new tests:

1. **During-suspension no-op:** seed a persisted viewport, gate the dispatcher, `initMap`, `runCurrent` (suspends at load), call `saveViewport()`, release, `advanceUntilIdle` — assert the file still holds the seeded center (not the default).
2. **After-restore persists:** after the restore completes, call `saveViewport()` — assert the file holds the current viewport.

## Risks / Trade-offs

- **Low:** the flag only suppresses saves in a window where saving is wrong. After the restore, behavior is unchanged.
- **initMap failure:** flag stays false → `saveViewport()` no-ops for that map. Correct — the map failed to open, nothing meaningful to save, old viewport preserved.
- **Re-entry:** `initMap` can run again (MAIN screen after map downloads); the reset at the top of `initMap` re-arms the guard for the new restore window.
- **Verification:** unit tests on the test scheduler + the existing real-dispatcher wait loop; `:app:testMobileDebugUnitTest` and `assembleMobileDebug` must stay green.
