## Context

See proposal.md — Why. Current state: `initMap` (MapCanvasViewModel.kt) wires the view-change listener at the point where the renderer is created, then suspends on `viewportStorage.load` (Dispatchers.IO) and `client.getDatabaseBoundingBox` (JNI), then applies the restored viewport to `_uiState` and requests the first render. The listener's `onViewChanged` persists every completed render job's coordinates via `viewportStorage.save`. Because the listener is wired before the restore is applied, any render that completes during the restore window — e.g. the one triggered by `setScreenSize` → `renderMap()` with the still-default `_uiState.viewport` — persists the uninitialized viewport and clobbers the restore. `ViewportStorage.save` has no validity guard, and kotlinx.serialization's default `encodeDefaults=false` omits fields equal to their defaults, so the corrupt file shows a dropped center (`{"magnification":13.0}`).

Both the restore computation and the listener wiring run inside `initMap`'s `viewModelScope.launch` (Dispatchers.Main.immediate). The renderer and its render loop run on a dedicated `Dispatchers.Default` scope; `onViewChanged` is invoked from `executeRender` on that scope and hops to `viewModelScope` for the save. No new threads or lifecycle components are introduced.

## Goals / Non-Goals

**Goals:**
- The restored viewport is never overwritten on disk by an early render with the uninitialized default viewport.
- Invalid viewport states (NaN, infinity, out-of-range) are never persisted, from any save path (`onViewChanged` and `saveViewport` on `ON_PAUSE`).
- Regression tests for the exact failure: an early render with the default viewport must not clobber a restored viewport; an invalid save must not clobber a valid file.

**Non-Goals:**
- Changing the save-on-every-render design (the listener persists continuously so pan/zoom survive process death without waiting for `ON_PAUSE`).
- Changing the restore fallback chain (saved → bounding box → default) or the magnification clamp.
- The multi-DB POI search `limit` issue (TODO.md §10) — separate change.

## Decisions

### D1: Reorder `initMap` — restore before renderer creation, apply before listener wiring (A)

New order inside the `initMap` launch block:

1. Load saved viewport + bounding box, compute `vp` (clamped) — **suspends here, `mapRenderer` is still null**, so `setScreenSize`'s `renderMap()` early-returns ("mapRenderer is null — no render").
2. Create the renderer, set screen size, assign `mapRenderer`.
3. `_uiState.value = _uiState.value.copy(viewport = vp, isLoading = false)`.
4. Launch the frame collector, wire the view-change listener.
5. Favorites, dark presentation, style sheet, `requestRender(vp)`.

Steps 2–5 run synchronously on the main dispatcher (no suspension between the viewport application and the listener wiring), so the composable's `setScreenSize` (main thread) cannot interleave. Any render that fires `onViewChanged` after the listener is wired was submitted with the restored viewport or a later user-initiated change.

Alternatives:
- (a) Wire the listener only after the first `requestRender(vp)` — insufficient: a render job with the default viewport queued earlier (by `setScreenSize` during the restore suspension) can still complete after the wiring and fire `onViewChanged` with the default viewport. Rejected.
- (b) Guard `save` only (D2) — insufficient alone: the default viewport (51.5136/7.4653) is valid-looking, so a validity guard cannot distinguish it from a real user position. Rejected as the sole fix.
- (c) Gate `setScreenSize`'s early render on a "viewport restored" flag — adds state and still leaves the listener-wiring window; the reorder removes the window entirely. Rejected.

### D2: Guard `ViewportStorage.save` with `ViewportState.isValid()` (B)

`ViewportState.isValid()` returns false for NaN/infinity coordinates, lat outside [-90, 90], lon outside [-180, 180], NaN/infinity or non-positive magnification, and NaN/infinity angle. `ViewportStorage.save` rejects such states with a warning before touching the file. This is the single choke point for both save paths (`onViewChanged` and `saveViewport` on `ON_PAUSE` — the latter can fire while `initMap` is still suspended and would otherwise persist the default viewport).

Alternatives:
- (a) Guard only in the `onViewChanged` listener — misses the `ON_PAUSE` `saveViewport` path. Rejected.
- (b) No guard — the bug. Rejected.
- (c) Also reject the exact default center — brittle (a user can legitimately be at Dortmund); the reorder (D1) is the correct protection for the default-center case. Rejected.

### D3: Regression tests target the race, not the parts

- `ViewportStorageTest`: `saveRejectsNaNCoordinates`, `saveRejectsOutOfRangeCoordinates`, `saveRejectsInvalidMagnification`, `saveRejectedStateDoesNotClobberExistingFile` (the race scenario: valid file on disk, invalid save leaves it untouched), `isValidRejectsUninitializedState`.
- The `initMap` reorder itself is covered by the existing `MapCanvasViewModelSharedLocationTest`/`MapCanvasViewModelStyleTest` suites (they exercise `initMap` end-to-end with `FakeOSMScoutClient`); the full unit suite must stay green.

Threading: all decisions are pure functions or main-thread state updates; the restore suspension happens while `mapRenderer` is null, so no render can be in flight during it. No new dispatchers, no lifecycle changes (guidelines/Design.md §4).

## Risks / Trade-offs

- [Restore now happens before renderer creation] → The restore computation (IO load + JNI bbox) is unchanged in cost; it just runs earlier in the launch block. The first render still happens after the renderer exists. No visible timing change beyond the loading indicator clearing at the same point relative to the first frame.
- [`isValid()` rejects mag <= 0] → A persisted mag of 0 or negative is never renderable anyway (the restore clamps to `MIN_MAG`); rejecting it on save is safe.
- [Default-center saves still pass the guard] → Intended: the guard is defense-in-depth for invalid states; the default-center clobber is eliminated by the reorder (D1), which is the primary fix.

## Migration Plan

Bug fix, no data migration. Rollback: revert the change — pre-change behavior (restored viewport clobbered by an early save) returns. No manifest, API, or native changes. A previously corrupted `viewport-<map>.json` (dropped center) is self-healing: the next user-initiated view change saves a valid state, and the restore fallback (bbox center) covers the interim.

## Open Questions

None.
