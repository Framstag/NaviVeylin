# route-map-overview Specification

## Purpose
Defines the phone map overview when a calculated route is shown: the camera fits the route bounding box so start, target, and the route polyline are all visible, except while the driver is actively navigating or following.

## Requirements

### Requirement: Route overview fits the route bounding box
When a route calculation completes in an active route-planning session, the system SHALL position the camera so that the route start marker, the target marker, and the route polyline are all visible: the viewport center SHALL be the center of the map area the session's overlay leaves free and the magnification SHALL fit the bounding box (with margin) in both dimensions using that free area. The free area SHALL be derived from the covered height the overlay **reports** for the state it is in — a nominal fraction of the screen height SHALL NOT be used, because the phone card's real height never agreed with one (device measurement, 2026-10-03: the fit shifted for 528 px while the card covered 1142 px, hiding the destination end of the route). The fit SHALL be applied by the session once per route result, and SHALL be re-applied when the reported covered height **grows** while the session is reviewing a route, so that a state change of the overlay can never leave the route behind it. **While a manoeuvre focus stands** (the user analysed a step), a growing height SHALL re-place that step on the centre of the new free area instead of re-fitting the route, and SHALL widen the magnification only if the focused segment no longer fits: a layout change must never move the zoom in, and it must never leave the analysed segment behind the card (owner findings, 2026-10-03: "suddenly without action zoom away from the visible step" and "segment still cut off" — the min card grows with a longer instruction after the focus ran). A **shrinking** reported height SHALL NOT move the camera: it only frees map area, and it accompanies an explicit step selection whose manoeuvre focus must stand (owner finding, 2026-10-03: the first page after selecting a row showed the overview instead of the manoeuvre, later pages were fine because the height had settled). Ending the session SHALL NOT move the camera either: it frees the whole map area, and the viewport the session left stays (spec `route-planning-session` — Ending the session removes its surface).

#### Scenario: Route calculation shows the whole trip
- **WHEN** the user calculates a route in the session and the map is not navigating
- **THEN** the viewport SHALL be centered on the route bounding-box midpoint within the map area the overlay leaves free and the magnification SHALL fit the start and target markers plus the route polyline within that area

#### Scenario: Route calculated from a favorite while free-driving
- **WHEN** the user picks a favorite and calculates a route while the map was following (free-driving)
- **THEN** the session lease SHALL suspend following for the duration of the session
- **AND** the route overview SHALL fit within the map area the overlay leaves free

#### Scenario: Long trip zooms out below the area-favorites floor
- **WHEN** the calculated route spans farther than the area-favorites fit floor allows and navigation is not active
- **THEN** the magnification SHALL zoom out below that floor as needed so both endpoints remain visible, clamped only to the render-stability minimum

#### Scenario: Stale route result does not re-fit
- **WHEN** the already-drawn route result is re-emitted unchanged (e.g., screen recreation re-subscribing the collector) and the viewport has since been moved by the user
- **THEN** the camera SHALL remain at the user-moved viewport

#### Scenario: Changing the overlay's state keeps the route visible
- **WHEN** the route overview has been fitted
- **AND** the user grows the session overlay (e.g. from min to max, or by opening a field)
- **THEN** the camera SHALL be fitted again for the newly free map area
- **AND** the start marker, the target marker and the polyline SHALL remain visible in that area

#### Scenario: A growing overlay re-places the focused step

- **WHEN** a step's segment is focused and the overlay's reported height then grows
- **THEN** the camera SHALL re-place the focused segment on the centre of the new free area
- **AND** the magnification SHALL stay unless the segment no longer fits, in which case it SHALL only be widened

#### Scenario: A shrinking overlay leaves the camera alone
- **WHEN** a step is analysed (the camera is on its manoeuvre) and the overlay then shrinks (the card collapses to min)
- **THEN** the camera SHALL stay on the manoeuvre
- **AND** paging on from there SHALL move the camera from manoeuvre to manoeuvre

#### Scenario: The fit uses the reported height
- **WHEN** the overlay reports that it covers N pixels of the screen height
- **THEN** the fit SHALL shift the camera for N free-map pixels
- **AND** the whole drawn route SHALL lie in the map area above the overlay

#### Scenario: An unchanged height does not move a user-moved viewport
- **WHEN** the route has been fitted
- **AND** the overlay reports the same covered height again (recomposition, screen recreation)
- **AND** the user has moved the viewport in between
- **THEN** the camera SHALL remain at the user-moved viewport

#### Scenario: Ending the session frees the whole map without moving the camera

- **WHEN** the session's card is on screen and the user ends the session
- **THEN** the overlay SHALL report a covered height of zero
- **AND** the camera SHALL stay where the session left it
### Requirement: Route fit is suppressed while driving
While the phone is navigating or follow mode is active, route result updates SHALL redraw the route without moving the camera; the driver's viewport SHALL not be yanked to a route overview.

#### Scenario: Reroute during navigation keeps the driving viewport
- **WHEN** a reroute calculation completes while navigation is active
- **THEN** the new route SHALL be drawn over the current viewport and the camera SHALL NOT refit

#### Scenario: Restarting navigation keeps follow behavior
- **WHEN** the user starts navigation on a calculated route
- **THEN** the camera SHALL follow the GPS position as today and SHALL NOT jump to a route overview

### Requirement: Degenerate route geometry degrades safely
When the route result has no usable polyline points or only coincides with the endpoint pair, the system SHALL still place the camera on the available coordinates (start/dest midpoint) at a sane zoom rather than leaving the prior viewport or failing. Polyline coordinates that are **absent** (not merely empty) SHALL be handled as no usable polyline: no exception, treated exactly as an empty polyline, nothing drawn for it.

#### Scenario: Empty polyline falls back to endpoints
- **WHEN** a route result arrives with an empty polyline but valid start and target coordinates
- **THEN** the camera SHALL center on the start/target midpoint at a zoom that keeps both markers visible

#### Scenario: Missing coordinates leave the viewport untouched
- **WHEN** no usable coordinates exist at all (polyline empty, endpoints invalid)
- **THEN** the camera SHALL stay at the current viewport and the map SHALL not fail to render

#### Scenario: Absent polyline coordinates are treated as an empty polyline
- **WHEN** a calculated route result arrives whose polyline coordinates are absent and valid start/target coordinates exist
- **THEN** the system SHALL NOT raise an exception
- **AND** the result SHALL be handled exactly as an empty polyline: the camera SHALL fall back to the start/target midpoint
- **AND** no polyline SHALL be drawn

#### Scenario: Adoption of a route without geometry keeps the session
- **WHEN** a surface adopts a route that was acquired for it and the route carries no polyline coordinates
- **THEN** the surface's session state (active navigation, start and destination) SHALL be adopted
- **AND** no route geometry SHALL be published for drawing
- **AND** the same session state SHALL remain observable on the other surface, unchanged
