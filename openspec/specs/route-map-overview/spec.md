# route-map-overview Specification

## Purpose
Defines the phone map overview when a calculated route is shown: the camera fits the route bounding box so start, target, and the route polyline are all visible, except while the driver is actively navigating or following.

## Requirements

### Requirement: Route overview fits the route bounding box
When a calculated route is shown on the phone map and the user is not navigating or in follow mode, the system SHALL position the camera so that the route start marker, the target marker, and the route polyline are all visible within the visible map area (the canvas minus the area covered by the open route panel): the viewport center SHALL be the visible-area center and the magnification SHALL fit the bounding box (with margin) in both dimensions using the canvas size reduced by the panel's covered height. The fit SHALL run once per route result (after a short layout-settle delay so the panel's covered height is measured at its final size) and SHALL NOT run again when neither the route result nor its visibility changes. When the route panel is closed the visible area equals the full canvas.

#### Scenario: Route calculation shows the whole trip
- **WHEN** the user calculates a route in the route panel and the map is not in follow mode
- **THEN** the viewport SHALL be centered on the route bounding-box midpoint within the visible map area and the magnification SHALL fit the start and target markers plus the route polyline within that area — even when the route panel is open during the calculation

#### Scenario: Route calculated from a favorite while free-driving
- **WHEN** the user picks a favorite and calculates a route while follow mode is active (free-driving)
- **THEN** selecting the destination SHALL disable follow mode (as the search/POI/contacts paths already do) and the route overview SHALL fit within the visible map area

#### Scenario: Long trip zooms out below the area-favorites floor
- **WHEN** the calculated route spans farther than the area-favorites fit floor allows and navigation is not active
- **THEN** the magnification SHALL zoom out below that floor as needed so both endpoints remain visible, clamped only to the render-stability minimum

#### Scenario: Stale route result does not re-fit
- **WHEN** the already-drawn route result is re-emitted unchanged (e.g., screen recreation re-subscribing the collector) and the viewport has since been moved by the user
- **THEN** the camera SHALL remain at the user-moved viewport

### Requirement: Route fit is suppressed while driving
While the phone is navigating or follow mode is active, route result updates SHALL redraw the route without moving the camera; the driver's viewport SHALL not be yanked to a route overview.

#### Scenario: Reroute during navigation keeps the driving viewport
- **WHEN** a reroute calculation completes while navigation is active
- **THEN** the new route SHALL be drawn over the current viewport and the camera SHALL NOT refit

#### Scenario: Restarting navigation keeps follow behavior
- **WHEN** the user starts navigation on a calculated route
- **THEN** the camera SHALL follow the GPS position as today and SHALL NOT jump to a route overview

### Requirement: Degenerate route geometry degrades safely
When the route result has no usable polyline points or only coincides with the endpoint pair, the system SHALL still place the camera on the available coordinates (start/dest midpoint) at a sane zoom rather than leaving the prior viewport or failing.

#### Scenario: Empty polyline falls back to endpoints
- **WHEN** a route result arrives with an empty polyline but valid start and target coordinates
- **THEN** the camera SHALL center on the start/target midpoint at a zoom that keeps both markers visible

#### Scenario: Missing coordinates leave the viewport untouched
- **WHEN** no usable coordinates exist at all (polyline empty, endpoints invalid)
- **THEN** the camera SHALL stay at the current viewport and the map SHALL not fail to render
