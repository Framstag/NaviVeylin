# Route Overview Fit — Proposal

## Why

When a route is calculated (or restarted) on the phone, the map camera never moves to show the trip: the viewport stays wherever it was — typically near the destination from the details sheet — so only the destination marker is visible while the start marker and the route polyline sit off-screen. The overview idea ("where am I going") is lost. The route bounding box needs to drive the camera so start, target, and the route are all visible.

## What Changes

- **New:** when a route result is drawn on the map (route calculated, or made visible again before navigation starts), the camera SHALL fit to the route bounding box — center on the bbox midpoint and pick a magnification that fits both endpoints (and the polyline) in the viewport.
- **New:** the fit SHALL use the actual canvas pixel size (`setScreenSize`) and SHALL allow zooming out below the area-favorites floor (`computeAreaZoom` currently clamps to 14) so long trips fit.
- **New (revised):** the fit SHALL fit within the **visible map area** — canvas minus the area covered by the open route panel — consuming the panel's measured covered height and shifting the center to the visible-area midpoint; a short settle delay ensures the panel's final size is measured before the fit applies. Closed panel = full canvas, unchanged behavior.
- **New (revised):** selecting a route destination (`onFavoriteSelected`, `openRoutePanelWithStart`) SHALL disable follow mode, matching the search/POI/contacts paths, so a deliberate calculation always produces the overview.
- **New:** the fit SHALL be suppressed while navigation is active (reroute mid-drive) or while follow mode is on (free-drive), so the camera never yanks away from the driver — with follow disabled by the selection paths, the guard protects only reroute/restart and non-selection flows.
- **Unchanged:** the route-drawing pipeline (`mapRenderer.setRoute`, markers, `clearRoute`, visibility toggling per `reroute-route-visibility` spec). Navigation start keeps forcing follow mode and GPS centering as today.
- **New (revised):** the shared bbox→magnification helper (`computeAreaZoom`) SHALL zoom against the renderer's actual ground resolution — the display DPI the map is drawn at (`REFERENCE_DPI / dpi`) and the Mercator `cos(lat)` ground shrink. Without both factors the helper over-zooms (by `dpi / 96` and `1 / cos(lat)`), so no bbox fit can be trusted; all callers (area favorites, POI/radius search fit, route overview) pass the DPI they render at.
- **New:** the route overview verifies the projected bounding box against the visible area and steps one level out if whole-level rounding (or map rotation) would clip it.
- **No new dependencies.** Existing `computeAreaZoom` is generalized with optional minimum-zoom and DPI parameters.

## Capabilities

- **New Capability:** `route-map-overview` — camera fits to the route bounding box when a calculated route is shown on the phone map; fit suppressed while navigating/following.
- **Modified Capabilities:** none. Existing `reroute-route-visibility`, `map-modes`, `map-pan-zoom` requirements are unchanged (additive behavior only).

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — fit logic in the route-result collector (`setRoutePanelViewModel`), new `fitViewportToRoute` helper (settle-delayed, sheet-inset-aware, verified against the visible area), covered-height flow from the route panel, `computeAreaZoom` gains optional `minZoom` and `dpi` parameters (plus the Mercator ground-scale correction) and a route-fit/area-favorites caller each, `onFavoriteSelected`/`openRoutePanelWithStart` disable follow mode.
- `app/src/main/java/com/naviveylin/ui/route/RoutePanel.kt` — reports the sheet's covered height (`SheetState.requireOffset()`) to the map view model; `RoutePanelViewModel` carries the value as a `StateFlow<Int>`.
- `app/src/main/java/com/naviveylin/ui/map/SearchDialog.kt` — POI/radius fit passes the display DPI to `computeAreaZoom`.
- `app/src/test/java/com/naviveylin/ui/map/MapCanvasViewModelRouteFitTest.kt` — new unit tests (viewport center/magnification after route calc; no fit while navigating/following with a real `NavigationViewModel` wired; fav-select disables follow and fits; sheet-inset reduced height/shifted center; settle-window gesture cancel; degenerate/empty polyline fallback).
- Related guidelines: `MapRendering.md` (viewport/camera pipeline is renderer-adjacent), `UI.md` (overview is a map behavior, no screen layout change).
- Scope: **phone only**. Android Auto has its own map/overview path (`AANavigationController`) and is not touched by this change. The `computeAreaZoom` ground-resolution correction does change the phone-side area-favorites and POI-search fit zooms (they zoom out to the geometrically correct level; both previously over-zoomed).
- Additive, non-breaking. Rollback: revert the collector change and the `computeAreaZoom` parameters (defaults preserve the old signature; the old *values* would only return if the DPI/`cos(lat)` correction is reverted too).
- No native/JNI changes — pure Kotlin viewport math on coordinates the app already holds.
