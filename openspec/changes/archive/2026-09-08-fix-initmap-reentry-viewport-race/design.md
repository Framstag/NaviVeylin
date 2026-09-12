## Context

See proposal.md — Why. Current `initMap` facts (verified 2026-09-08):

- Single caller: `MapCanvasScreen.kt:563` inside `LaunchedEffect(mapPath)` — re-fires on every map-path change (MAIN screen after map download/update/delete, basemap reload via `BasemapReloadNotifier`).
- `initMap` re-arms `viewportRestored = false` synchronously, then launches into `viewModelScope` (Main.immediate). The previous launch is **not** tracked or cancelled — only `rendererScope` is torn down (line 1146) and `mapRenderer` shut down.
- The launch body has a genuine suspension point: `favoriteRepository.init(path)` (line 1209) runs `withContext(defaultDispatcher)` (`FavoriteRepository.kt:43`). While suspended there, the main thread is free, so a re-entry `initMap` runs to completion (re-arms flag, applies its restore, sets `viewportRestored = true`). When the old coroutine resumes it executes the tail — renderer creation, `mapRenderer = renderer` (line 1254), `_uiState` viewport copy (1260) — and sets `viewportRestored = true` (1263) **after** the newer init, clobbering both state and the save guard.

## Goals / Non-Goals

**Goals:**
- A re-entered `initMap` fully supersedes the previous in-flight initialization: no stale viewport apply, no stale `mapRenderer` assignment, no stale save-guard re-arm, no stale error/`mapReady` writes.
- Normal single-init path byte-identical behavior; existing guard tests stay green.
- Regression tests that reproduce the two-coroutine interleaving deterministically.

**Non-Goals:**
- No changes to the restoration/save semantics themselves (see `viewport-persist` spec — all existing requirements unchanged).
- No changes outside `MapCanvasViewModel.kt` + its viewport-restore test.
- No Auto/AA path changes (`auto` module has its own map lifecycle).

## Decisions

### D1: Cancel the previous init job (generation-counter alternative rejected)

Track the init coroutine and cancel it on re-entry, mirroring `LaunchedEffect(mapPath)` semantics (the composable already cancels its block on key change — only the escape into `viewModelScope` leaks):

```kotlin
private var initJob: Job? = null

fun initMap(mapPath: String) {
    ...
    viewportRestored = false
    initJob?.cancel()          // supersede any in-flight init
    initJob = viewModelScope.launch {
        ... // unchanged body; cancellation aborts it at the suspension point
    }
}
```

Why cancellation wins over a generation counter:

- `withContext(defaultDispatcher)` is a cooperative cancellation point — a cancelled old coroutine rethrows `CancellationException` on resume and never executes the tail (renderer creation, `mapRenderer =`, viewport copy, flag set). One mechanism covers all stale writes, including ones a guard would miss (`mapRenderer = renderer` at line 1254).
- A generation counter needs an explicit guard at every post-suspension write (renderer assignment, `_uiState` viewport, `viewportRestored = true`, error/`mapReady` path) — easy to miss one, and the renderer assignment is a real clobber that a state-only guard wouldn't fix.
- `cancel()` on a completed job is a no-op, so the single-init path is unaffected.

Interleaving safety (Main.immediate serializes): at re-entry the old coroutine is either (a) queued → cancel prevents execution, (b) suspended at `withContext` → aborted on resume, or (c) already completed → its writes preceded the new re-arm, correct final state. A cancelled `withContext` does not run the block a second time.

### D2: Re-arm the guard before cancel, inside `initMap` (unchanged position)

`viewportRestored = false` stays where it is (synchronous, first thing). Cancellation must come after the re-arm so the tail of a stale coroutine — if it somehow survived — cannot set the flag again. Order: re-arm → cancel → launch.

### D3: Regression tests via a gated suspension

Extend `MapCanvasViewModelViewportRestoreTest` (Robolectric, default sandbox — JNI stub constraint). Reproduce the interleaving by making the suspension point block on a test gate:

- Gate the storage dispatcher: `ViewportStorage.ioDispatcher` is a settable field, so the test injects `FirstDispatchGatedDispatcher` — a dispatcher that holds only the FIRST dispatched block until `release()` and runs all later blocks inline. The first dispatch is `initMap(pathA)`'s viewport load, so pathA's init suspends there while the re-entry `initMap(pathB)` completes its own (later, inline) load. A `FavoriteRepository` subclass was considered first but rejected: `FavoriteRepository.init` is **final** and cannot be overridden.
- Test 1 (stale restore never applied): start `initMap(pathA)` (load held), call `initMap(pathB)` (cancels A, B completes), release the gate, drain the dispatcher → `_uiState.viewport` equals pathB's restore; `viewportRestored` stays true for B; `saveViewport()` persists B's center.
- Test 2 (save guard during re-entry): while B's init has re-armed the guard but not applied its restore, `saveViewport()` writes nothing (seeded file retains prior content).
- Test 3 (single-init unchanged): existing tests already cover; keep them green as the regression net.
- Test 4 (renderer sync, D4): gate the VM's `defaultDispatcher` so `applyStyleSheet` suspends; the first native render must be at 2^restoredMag (4096), not 2^5 (32) — `FakeOSMScoutClient` records a `renderMags` history for the assertion.

### D4: Sync the renderer to the restored viewport (low-zoom first frame)

`initMap`'s final `requestRender(vp)` (line 1355) already renders at the restored viewport, but the init-time dark/style pushes (`invalidateStyle` via `pushDarkPresentation`/`applyStyleSheet`) submit BEFORE it. During the `applyStyleSheet` suspension (`withContext(defaultDispatcher)`), the debounce fires and renders at the renderer's DEFAULT viewport (mag 5) — a zoomed-out frame is displayed until the restored render completes (observed on device after basemap reload + switch back; a manual refresh fixes it). Fix: `renderer.prepareViewport(vp...)` right after renderer creation, so every init-time submit uses the restored viewport. Alternative considered: reordering the dark/style pushes after `requestRender` — rejected, the style must load before the first render. Test: gate the VM's `defaultDispatcher` so `applyStyleSheet` suspends; the first native render must be 2^restoredMag (4096), not 2^5 (32) — discriminates (verified: fails with the fix disabled).

## Risks / Trade-offs

- [Cancellation interrupts fav-init tail work if a future initMap body adds suspension-unsafe work] → Cancellation only takes effect at suspension points; synchronous critical sections (JNI open, state writes) are never preempted mid-block. Any future suspension added to the body inherits the same well-defined abort behavior.
- [A cancelled old coroutine leaves `favoriteRepository` initialized for the old path] → Harmless: `init()` is idempotent per path and the repository is shared app-wide; new init re-initializes it.
- [Gated-dispatcher tests are order-sensitive to test-runner threading] → Gate with a deterministic `CompletableDeferred` + `runTest(mainDispatcherRule.dispatcher)`, same pattern the existing restore tests use; no wall-clock sleeps.

## Migration Plan

Single-file additive change; rollback = revert the `initJob` field + cancel line. No data migration, no serialized format change.
