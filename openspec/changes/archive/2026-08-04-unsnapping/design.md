## Context

MapCanvasViewModel already has `disengageFollowMode()` called from all gesture handlers (pan, pinch zoom, rotation, scroll wheel, keyboard zoom, zoom buttons). `onToggleFollowMode(true)` already exists and re-centers on GPS. The compass button's `onCenterClick` already calls `onToggleFollowMode(true)`.

Missing: a persistent re-center button visible when follow mode is off + GPS available. Follow mode toggle is currently only in the LocationOptionsOverlay (settings bottom sheet).

## Goals / Non-Goals

**Goals:**
- Add re-center button to right-side button column in MapCanvasScreen
- Button visible when `!followMode && gpsFixQuality != NONE`
- Button calls `onToggleFollowMode(true)` + re-centers on GPS
- Use Material Icons `MyLocation` for the icon

**Non-Goals:**
- No changes to gesture handling or disengage logic (already works)
- No changes to LocationOptionsOverlay
- No changes to ViewModel beyond what already exists

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Button placement | Right-side column, below zoom controls | Consistent with existing button layout (compass, search, favorites, zoom) |
| Icon | `Icons.Filled.MyLocation` | Standard Material crosshair icon, immediately recognizable |
| Visibility condition | `!followMode && gpsFixQuality != NONE` | Hide when follow is already active or no GPS to center on |
| Re-center action | `onToggleFollowMode(true)` + `updateCenter(loc.lat, loc.lon)` | Reuses existing VM methods; compass button already does the same |
| No new VM state | Use existing `followMode` and `gpsFixQuality` from `MapCanvasUiState` | No new state fields needed |

## Risks / Trade-offs

- [Button overload] Right column already has 4 controls → Mitigation: re-center replaces no existing button, stacks below zoom. If column gets too tall on small screens, future refactor can group into a collapsible panel.
- [Compass redundancy] Compass button already re-centers on long-press → Mitigation: compass center-click is not discoverable (long-press). Dedicated button is explicit and matches user expectation.
