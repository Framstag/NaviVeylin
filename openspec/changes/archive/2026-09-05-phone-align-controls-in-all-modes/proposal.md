# Proposal: Align phone map controls across standard and routing views

## What Changes

The phone map screen renders two overlay layouts that place the right-side widgets differently:

- **Routing (navigation) view** — the reference placement: a single right-side column with the compass directly above the speed widget, bottom-anchored above the routing status bar. This placement is considered appropriate, but the view has **no zoom controls**.
- **Standard (free-form) view** — a different placement: compass + speed widget at the top-right with the location-options button + zoom controls at the bottom-right (landscape), or all four stacked in a top-anchored right column (portrait). It has zoom controls, but the widget placement diverges from the routing view.

This change unifies both views on the routing variant's placement:

1. **Routing view**: add zoom controls at the bottom of the right-side column, below all other controls (compass + speed widget), directly above the routing status bar.
2. **Standard view**: adapt to the routing variant — a single bottom-anchored right-side column: compass directly above the speed widget, then the location-options button, then zoom controls at the bottom below all other controls. In landscape this merges the current top-right (compass + speed) and bottom-right (location options + zoom) clusters into one column; in portrait the top-right column moves to the bottom.

The location-options button remains standard-view-only (not added to the routing view); the routing view gains only the zoom controls.

## Capabilities

### New Capabilities

None — this change modifies existing layout requirements.

### Modified Capabilities

- `map-canvas-screen`: the right view column moves from top-right to bottom-anchored; column order becomes compass → speed widget → location options → zoom controls, with zoom at the bottom below all other controls.
- `landscape-layout`: the view cluster becomes a single bottom-anchored right column (compass, speed widget, location options, zoom) instead of compass + speed at top-right and location options + zoom at bottom-right.
- `compass-button`: the compass sits at the top of the bottom-anchored right view column (was the top of the top-anchored column).
- `map-speed-widget`: the compass sits directly above the speed widget in the standard view too (currently only specified during navigation).
- `zoom-controls`: zoom controls are also shown during navigation, at the bottom of the right-side column below all other controls.

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — restructure the right-side overlay columns in both the standard (landscape + portrait) and navigation branches; reuse the existing `ZoomControls` composable in the navigation branch.
- Specs updated: `map-canvas-screen`, `landscape-layout`, `compass-button`, `map-speed-widget`, `zoom-controls`.
- UI tests: existing Compose tests (e.g. `RoutePanelComposeTest`, `SpeedWidgetTest`) may assert layout positions and need updating.
