## Context

See proposal.md for motivation. Current nav-end restore lives in `MapCanvasViewModel.setNavigationViewModel` (nav-state collector, ~L2086-2102): when `isNavigating` transitions true→false it copies back the pre-navigation `followMode` and `driveSuspended` — and nothing else. The map camera keeps the last heading-up angle and driving zoom because the follow-mode GPS collector stops moving the camera once `followMode` is off, and no render is triggered after the restore. The explicit drive-toggle exit (`exitFreeDrive`) already applies the BROWSE representation (north-up, angle 0, `freeFormNorthUp=true`) — the nav-end path diverges from it.

Per user decision: restore representation *settings* only; keep the map's geographic position and zoom where routing ended.

## Goals / Non-Goals

**Goals:**
- Navigation end applies the restored mode's representation preset — BROWSE: north-up + follow off + suspension/drift cleared + re-render; FREE_DRIVE: follow on per pre-nav state.
- Viewport center and magnification are untouched by the restore.
- Reuse one representation-apply helper for both exit paths to prevent further divergence.

**Non-Goals:**
- No restoration of pre-navigation viewport/zoom (explicitly out; map stays at routing end).
- No change to viewport persistence (`onViewChanged`, `saveViewport`) — persisted viewport intentionally tracks the routing end position.
- No change to `autoZoomEnabled` flag — it is a persisted user setting and inert in BROWSE (auto-zoom is follow-gated).
- No change to the Android Auto controller (`AANavigationController`) — separate car-mode semantics.

## Decisions

### D1: Apply representation inside the existing nav-state collector
Extend the `!navState.isNavigating && preNavFollow != null` branch of the collector in `setNavigationViewModel` rather than introducing a new state machine. It is the single place that observes nav start/stop on the phone map.

- Restore `followMode`/`driveSuspended` as today.
- Landing in BROWSE is defined by the same condition as the mode derivation: restored `followMode == false` **and** `driveSuspended == false` (a restored suspended FREE_DRIVE has follow off but suspension on and must NOT collapse to BROWSE). In that case set `freeFormNorthUp = true`, `viewport.angle = 0.0`, `browseDrifted = false`, then `renderMap()`.
- Restored **follow on** (active FREE_DRIVE): restore suspension (existing) and `renderMap()`; the follow collector re-applies heading/orientation from the persisted `navNorthUp` on the next GPS fix, so no angle write here.
- Restored **suspended FREE_DRIVE** (follow off, `driveSuspended = true`): keep both flags as captured — no browse representation, no angle write, `renderMap()` only.
- Center and magnification are copied through untouched.

Alternatives:
- Call `exitFreeDrive()` on nav end when restoring to BROWSE — rejected: it is gated on `mode == FREE_DRIVE` and also clears fields the restore path must not touch (it is mode-toggle semantics, and nav end may restore while the mode is still NAVIGATION until the flags land).
- Full state snapshot/restore of `MapCanvasUiState` at nav start — rejected: copies transient render/runtime fields, over-engineered for two flags + representation.

### D2: Shared representation helper to prevent divergence
Extract the BROWSE-representation mutation (`freeFormNorthUp=true`, `angle=0.0`, `browseDrifted=false`, follow off) into a small internal helper (e.g. `applyBrowseRepresentation()`) used by both `exitFreeDrive()` and the nav-end restore branch. Keeps both paths semantically identical; the nav-end BROWSE case becomes "restore follow flags, then apply browse representation".

### D3: `browseDrifted` reset on BROWSE landing
After nav end the center is at the routing end position (≈ GPS), so a stale `browseDrifted=true` would show a phantom re-center button. Reset to `false` in the restore-browse path. Alternative (leave stale) rejected: visible inconsistency.

### D4: Testability — drive the collector via the NavigationViewModel state flow
Nav-end restore is async inside a `viewModelScope.launch` collector on `Main.immediate`. Unit tests create a `NavigationViewModel`, call `setNavigationViewModel`, flip `state.value` to navigating and back, and assert the resulting `MapCanvasUiState` (orientation, angle, suspension, center/zoom retention). Follow the existing `MapCanvasViewModel` Robolectric test pattern (`@RunWith(RobolectricTestRunner)` default sandbox, `FakeOSMScoutClient` per the classloader rule in AGENTS.md). `Dispatchers.Main` control via `StandardTestDispatcher`/`runTest` where used by existing tests.

Alternative: extract the restore into an `internal` pure function and unit-test it directly — rejected: flipping the real state flow also verifies the collector wiring and races.

## Risks / Trade-offs

- [Restore branch races with `stopNavigation`'s own `onFollowModeChanged?.invoke(false)`] → Both run on `Main.immediate`; `onToggleFollowMode(false)` only flips `followMode` (no else-branch side effects), so order is idempotent. Verified in analysis; covered by a unit test that calls `stopNavigation()` end-to-end.
- [`renderMap()` after restore is a no-op if `mapRenderer` is null] → `renderMap` already null-guards; the angle write is still visible on next render (marker-only renders use the viewport angle snapshot).
- [Stale `lastUsedAngle` fallback (TODO.md §13) can re-apply an old angle on the *next* follow entry] → Pre-existing, separate trigger (north-up toggle while moving); out of scope, remains tracked in TODO.md.
- [FREE_DRIVE restore shows heading-up only on next GPS fix] → Accepted: follow collector re-applies orientation each fix; no visible frame flash because the restore re-renders at the existing angle first.

## Migration Plan

Additive bug fix. No persisted-data format change, no config migration. Rollback: revert the collector branch + helper in `MapCanvasViewModel.kt`; delete added unit tests.

## Open Questions

None — restored-mode semantics, viewport/zoom retention, and drift-flag handling were decided with the user during exploration (restore settings, not viewport and zoom).
