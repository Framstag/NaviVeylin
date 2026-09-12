## Why

Panning in Android Auto routing mode (turn-by-turn) is jumpy: while the map is panned, a GPS fix whose speed crosses a speed-band boundary re-engages follow mode and yanks the map back to the vehicle. Free-driving mode is smooth because it gates the whole viewport commit on `!panning`; routing mode gates only the heading-up angle and trusts the (wrong) assumption that a suspended auto-zoom controller never returns a zoom. `AutoZoomController.onSpeed` re-engages on band crossing by design (manual-zoom semantics), so the routing commit block runs while panned and calls `reengageFollow()` — the map glides back to the fix mid-pan.

## What Changes

- **Shared commit gate (B)**: extract the free-driving `shouldCommitViewport(panning, angle, newZoom)` decision into a shared helper (top-level in `MapPanHandler.kt`) and use it in both `NavigationScreen` and `FreeDrivingScreen`, so the two screens can never drift apart again.
- **Gate the auto-zoom feed (C)**: in both screens, do not feed `autoZoomController.onSpeed` while panned (`!panning` gate on the zoom computation). The controller state stays frozen during the pan; auto-zoom resumes on the next speed-band crossing after pan exit (same semantics as the manual zoom buttons).
- **Regression tests**: the exact composition that slipped through — panning + band-crossing speed must not commit a viewport change. Tests for the shared gate and the gated zoom decision; existing `FreeDrivingScreenTest` cases move to the shared helper test.
- **New spec requirement** in `auto/map-pan`: "Speed-band crossing while panned" — a speed change that crosses a speed-band boundary while panned must not change the zoom and must not re-engage follow mode. (ADDED delta; merges with the in-progress `auto-pan-during-navigation` change's `auto/map-pan` spec on archive.)

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `auto/map-pan`: add the "Speed-band crossing while panned" requirement — a speed change that crosses a speed-band boundary while panned must not change the zoom and must not re-engage follow mode. (ADDED delta; merges with the in-progress `auto-pan-during-navigation` change's `auto/map-pan` spec on archive.)

## Impact

- `auto/src/main/java/com/naviveylin/auto/MapPanHandler.kt` — add shared `shouldCommitViewport` helper (top-level).
- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — use the shared gate; gate the `onSpeed` feed on `!panning` (extract a testable zoom-decision function).
- `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt` — use the shared gate; gate the `onSpeed` feed on `!panning` (consistency; removes the local `shouldCommitViewport` companion function).
- Tests: `MapPanHandlerTest.kt` (shared gate), `NavigationScreenTest.kt` (panning + band-crossing zoom → no commit), `FreeDrivingScreenTest.kt` (move gate cases to the shared helper test).
- Additive bug fix; no API, manifest, or native changes. Rollback: revert the change — the pre-change behavior (jumpy routing pan) returns, no data migration.
- Affected guidelines: none (no design principle changes; `guidelines/Design.md` threading/state conventions unchanged).
