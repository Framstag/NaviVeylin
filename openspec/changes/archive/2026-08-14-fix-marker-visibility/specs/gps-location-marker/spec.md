# gps-location-marker Specification (Delta)

## ADDED Requirements

### Requirement: Marker rendered on dedicated overlay target

The system SHALL render the GPS location marker on an overlay layer separate from the map render surface. The marker SHALL NOT be written into cached tiles, the back buffer, the front buffer, or any bitmap that is reused across frames. The map render output SHALL contain only map content.

- The marker overlay SHALL redraw on top of the displayed map whenever a frame is emitted, projecting the marker state that rode with that frame (render-time snapshot)
- The marker SHALL NOT be drawn at the live GPS fix when the displayed frame was rendered for an earlier fix — doing so would place the marker ahead of the road on screen
- No marker pixels SHALL ever enter cached tiles, the back buffer, or the front buffer

#### Scenario: Marker stays on road during frame lag

- **WHEN** a new GPS fix arrives while the displayed frame was rendered for an earlier fix
- **THEN** the marker SHALL be drawn at the position that rode with the displayed frame, not at the live fix
- **THEN** the marker SHALL remain on the road/track of the displayed map

#### Scenario: No ghost marker after cached tile reuse

- **WHEN** the user pans and the pan is served from cached tiles after a marker move
- **THEN** the displayed map SHALL contain no marker pixels from a previous marker position

#### Scenario: Marker hidden leaves no residue

- **WHEN** the marker becomes hidden (e.g., GPS lost) and the map is then panned or zoomed
- **THEN** no marker pixels SHALL remain in the displayed map or in cached tiles

### Requirement: Marker projects against displayed bitmap viewport

The system SHALL project the GPS coordinate to screen pixels using the viewport of the bitmap currently displayed, not the target viewport of a render that has not completed.

- The overlay projection SHALL use the displayed bitmap's center, magnification, rotation, and DPI
- The marker SHALL be reprojected on every displayed frame

#### Scenario: Marker stays anchored during pan

- **WHEN** the user pans and the target viewport leads the rendered frame
- **THEN** the marker SHALL remain at the same screen-relative position over the same map features as the displayed bitmap

#### Scenario: Marker anchored during rotation placeholder

- **WHEN** the map rotates and a placeholder frame is displayed before the final render completes
- **THEN** the marker SHALL reproject against the displayed placeholder viewport each frame
- **THEN** the marker SHALL land on the correct geographic point in the final frame
