## Why

After route calculation succeeds, the user sees turn-by-turn instructions inside the route panel but has no clear way to start navigation or review the full route summary. A dedicated route dialog gives users a focused view of route stats (distance, time), a scrollable step list, and a Start Navigation button — bridging route planning to active navigation.

## What Changes

- New **RouteSummaryDialog** composable shown after route calculation completes
- Dialog overlays the route panel, displaying route statistics and scrollable step list
- "Start Navigation" button (Material 3) — no-op for now, wired later
- Dismissing dialog returns to route panel (still open with calculated route)
- Same dialog reused during active navigation, highlighting current step
- `RoutePanelViewModel` extended with state for dialog visibility and active step index

## Capabilities

### New Capabilities
- `route-summary-dialog`: Route summary dialog showing stats (distance, time), scrollable step list, and Start Navigation button. Dismiss returns to route panel. Reusable for active navigation with current-step highlighting.

### Modified Capabilities
- `route-panel-ui`: After route calculation completes, route panel triggers route summary dialog instead of showing instructions inline. Route panel remains open behind the dialog.

## Impact

- **New file**: `app/src/main/java/com/naviveylin/ui/route/RouteSummaryDialog.kt`
- **Modified**: `RoutePanelViewModel.kt` — add `showSummaryDialog`, `activeStepIndex` state, dialog lifecycle methods
- **Modified**: `RoutePanel.kt` — conditionally show summary dialog after calculation
- **Modified**: `MapCanvasScreen.kt` — wire summary dialog visibility
- **No native/JNI changes** — reuses existing `RouteEntry`, `RouteInstruction` types
