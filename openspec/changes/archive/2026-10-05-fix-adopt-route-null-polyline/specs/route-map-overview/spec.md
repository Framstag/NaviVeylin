# Spec Delta

## MODIFIED Requirements

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
