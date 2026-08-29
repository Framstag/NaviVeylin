## MODIFIED Requirements

### Requirement: Marker rendered on dedicated overlay target

The system SHALL render the GPS location marker on an overlay layer separate from the map render surface. The marker SHALL NOT be written into cached tiles, the back buffer, the front buffer, or any bitmap that is reused across frames. The map render output SHALL contain only map content.

- The marker overlay SHALL redraw on top of the displayed map whenever a frame is emitted, projecting the marker state that rode with that frame (render-time snapshot)
- In follow mode between fixes, the marker SHALL be drawn at the predicted position (extrapolated from the fix that rode with the frame, the speed, and the heading) so the marker glides with the blitted map
- The marker SHALL NOT be drawn at the live GPS fix when the displayed frame was rendered for an earlier fix — doing so would place the marker ahead of the road on screen
- No marker pixels SHALL ever enter cached tiles, the back buffer, or the front buffer

#### Scenario: Marker stays on road during frame lag

- **WHEN** a new GPS fix arrives while the displayed frame was rendered for an earlier fix
- **THEN** the marker SHALL be drawn at the predicted position extrapolated from the fix that rode with the displayed frame
- **THEN** the marker SHALL remain on the road/track of the displayed map

#### Scenario: No ghost marker after cached tile reuse

- **WHEN** the user pans and the pan is served from cached tiles after a marker move
- **THEN** the displayed map SHALL contain no marker pixels from a previous marker position

#### Scenario: Marker hidden leaves no residue

- **WHEN** the marker becomes hidden (e.g., GPS lost) and the map is then panned or zoomed
- **THEN** no marker pixels SHALL remain in the displayed map or in cached tiles

## ADDED Requirements

### Requirement: Marker glides between fixes

The system SHALL update the GPS marker position every display frame in follow mode while the vehicle is moving, at the predicted position, instead of only on each GPS fix.

#### Scenario: Marker moves smoothly between fixes

- **WHEN** the vehicle moves at constant speed and the display loop runs at 60 fps between two fixes
- **THEN** the marker SHALL move incrementally each frame along the predicted path
- **AND** the marker SHALL NOT jump from fix to fix

#### Scenario: Marker stationary when vehicle stops

- **WHEN** the vehicle speed drops below the movement threshold
- **THEN** the marker SHALL remain at the last position
- **AND** the marker SHALL NOT drift
