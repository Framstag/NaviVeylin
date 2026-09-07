# Design: Align phone map controls across standard and routing views

## Context

See proposal.md for motivation. Current state in `MapCanvasScreen.kt`:

- **Routing view** (`navState.isNavigating`): one right-side column — `Spacer(weight 1f)` + compass + 8dp + speed widget — bottom-anchored above `NavigationStateOverlay`. No zoom controls.
- **Standard view, landscape**: two separate right-side clusters — compass + speed at top-right, location-options gear + zoom at bottom-right.
- **Standard view, portrait**: one top-anchored right column — compass, speed, gear, zoom.

The routing placement is the reference; the standard view must adopt it, and the routing view must gain zoom controls at the bottom of its column.

## Goals / Non-Goals

**Goals**
- One right-side widget column definition shared by the standard view (both orientations) and the routing view, so the two views cannot drift apart again.
- Zoom controls at the bottom of the column, below all other controls, in both views.
- Zoom during navigation behaves like the existing pinch-zoom: follow mode stays on, auto-zoom suspends, re-center button appears.

**Non-Goals**
- Adding the location-options gear to the routing view (routing gains only zoom; the gear stays standard-view-only).
- Changing the left action column, re-center button, turn hints, or routing status bar.
- Android Auto / AAOS layouts (phone-only change).

## Decisions

### D1: Extract a shared right-widget column composable

Extract `MapRightWidgetColumn` (private, in `MapCanvasScreen.kt`) containing, top to bottom: compass, speed widget, optional location-options slot, zoom controls. Signature roughly:

```kotlin
@Composable
private fun MapRightWidgetColumn(
    isLandscape: Boolean,
    compassNorthUp: Boolean,
    mapAngleRadians: Double,
    gpsFixQuality: GpsFixQuality,
    onCenterClick: () -> Unit,
    onToggleOrientation: () -> Unit,
    speedInput: SpeedWidgetInput?,
    canZoomIn: Boolean,
    canZoomOut: Boolean,
    currentMag: Double,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    locationOptions: (@Composable () -> Unit)? = null,
    reserveSpeedSlot: Boolean = false,
    modifier: Modifier = Modifier
)
```

- `reserveSpeedSlot` is true only in the routing view: it keeps the speed-widget slot reserved (invisible) when no speed data is available, so the compass above does not shift (existing routing behavior, spec: map-speed-widget — stable widget layout). The standard view keeps its current behavior (widget rendered only when speed data exists).

- Column content is `horizontalAlignment = CenterHorizontally` (matches the routing column; preserves the spec'd compass/speed common center axis).
- Positioning stays at the call sites: the standard view passes a bottom-anchored modifier (`align(BottomEnd)`, `navigationBarsPadding()`, `verticalScroll`); the routing view wraps it in its existing `Spacer(weight 1f)`-anchored column inside the nav `Box(weight 1f)`.
- The location-options slot is `null` in the routing view; the standard view passes the existing `LocationOptionsOverlay(...)` call (all ~20 settings params stay at the call site, not in the shared composable). `MapLocationZoomBlock` is folded into the new composable (its gear + zoom split: gear via slot, zoom as the column's last element).

Rationale: three branches (landscape standard, portrait standard, routing) would otherwise carry near-identical compass/speed/zoom wiring; one definition guarantees the alignment this change is about. Alternative considered: minimal in-place restructure (move the compass+speed block into the bottom-right column, re-anchor portrait, append zoom in routing) — smaller diff but leaves the duplication that caused the divergence.

### D2: Standard view — single bottom-anchored right column

- **Landscape**: merge the top-right (compass + speed) and bottom-right (gear + zoom) clusters into one bottom-anchored column at `BottomEnd` with the existing bottom-right padding (`end = 8.dp`, `bottom = 44.dp`, `navigationBarsPadding()`).
- **Portrait**: move the top-right column to the same bottom-anchored position and padding.
- Column order: compass → speed widget → location options → zoom controls (zoom at the bottom, below all other controls).
- The `if (!navState.isNavigating)` guards stay as-is (columns hidden during navigation).

### D3: Routing view — zoom controls at the bottom of the column

Append `Spacer(8.dp)` + `ZoomControls(...)` after the speed widget in the routing column, so the zoom sits directly above `NavigationStateOverlay`.

Zoom handlers for the routing view:

```kotlin
onZoomIn = {
    attributionInteractionTick++
    viewModel.zoomIn()
    viewModel.renderMap()
    animateDiscreteZoomToCenter()
}
// onZoomOut symmetric
```

Deliberately **no** `viewModel.disengageFollowMode()`: during navigation the map must keep following the vehicle. `updateMagnification` already suspends auto-zoom on user zoom (`setAutoZoomSuspended(true)`), which makes the re-center button appear (`shouldShowReCenterButton`: `autoZoomPaused && isNavigating`); tapping it re-engages auto-zoom (`onToggleFollowMode(true)` → `setAutoZoomSuspended(false)`). This matches the existing pinch-zoom behavior during navigation (UI.md §7).

`ZoomControls` already takes `isLandscape` and renders horizontal (landscape) / vertical (portrait), satisfying the zoom-controls delta requirement.

### D4: Zoom orientation during navigation

Pass the `BoxWithConstraints`-derived `isLandscape` into the routing view's `ZoomControls` (the routing layout itself stays orientation-agnostic). Horizontal row in landscape, vertical column in portrait — same rule as the standard view.

## Risks / Trade-offs

- [Moving compass + speed to the bottom of the standard view contradicts the current `map-canvas-screen` / `landscape-layout` / `compass-button` specs] → Mitigation: this change updates those specs (delta specs in `specs/`); archive applies them on completion.
- [Zoom during navigation now suspends auto-zoom and surfaces the re-center button — new behavior for button zoom] → Mitigation: identical to existing pinch-zoom semantics; re-center button re-engages auto-zoom; no new state.
- [Shared-composable refactor could disturb the existing standard layout] → Mitigation: positioning stays at call sites; existing Compose tests (`SpeedWidgetTest`, `RoutePanelComposeTest`) plus a manual smoke check on device cover the change.
- [Centering the gear + zoom (was right-aligned in landscape) shifts them a few dp] → Mitigation: accepted; matches the routing column and the compass/speed center axis.

## Migration Plan

Single commit; no data or persistence changes. Rollback: revert the commit. No feature flag needed — layout-only change.

## Open Questions

None — the deferrable details (exact spacing between column items, portrait bottom padding) are task-level and do not affect the specs or approach.
