# Spec Delta

## MODIFIED Requirements

### Requirement: GPS marker position matches projected map coordinate

The system SHALL project the GPS coordinate to the screen pixel using the same projection and state that the native renderer uses for the current map viewport.

- The projection SHALL use the viewport center, current magnification, map rotation, and the same DPI value the frame it is drawn on was rendered with — the DPI carried by that render request, never a separately tracked display or client-wide value.
- The marker screen position SHALL be recomputed on every GPS fix and on every viewport change.
- When the DPI of the surface changes, marker positions SHALL be recomputed for the new value before the next frame is displayed.

#### Scenario: Marker stays on road while panning

- **WHEN** the user pans the map and the GPS location is on a visible road
- **THEN** the location marker SHALL remain on that road relative to the map features

#### Scenario: Marker does not drift during zoom transition

- **WHEN** the user pinch-zooms the map
- **THEN** the GPS marker screen position SHALL be recomputed at the current placeholder magnification
- **THEN** the marker SHALL land on the same geographic point after the native render completes

#### Scenario: Marker stays on the map across a surface switch

- **WHEN** the car surface and the phone canvas both render in the same process, without a restart in between
- **THEN** on each surface the marker lands on the map content of that surface's own frame
- **THEN** neither surface's frames are projected at the other surface's DPI
