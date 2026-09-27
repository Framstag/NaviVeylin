# gps-location-marker Delta Specification

## Purpose

Shows the user's current GPS-estimated position on the map as a Compose overlay, with visual indicators for accuracy and heading direction.

## MODIFIED Requirements

### Requirement: Marker position tracks map viewport

The marker SHALL project the GPS coordinate to screen pixel coordinates using the current map viewport (center, zoom, rotation). The marker SHALL move correctly when the user pans or zooms the map.

In follow mode the marker SHALL ride the displayed (eased predicted) position, which is derived from the same position source that frames are rendered on (the engine-filtered position during route guidance, the raw GPS fix otherwise). When the display loop stops (speed below the movement threshold), the marker SHALL remain at the frozen displayed position instead of falling back to a raw-fix projection; on resume it SHALL continue from that position without snapping.

#### Scenario: Marker moves during pan

- **WHEN** the user pans the map
- **THEN** the marker SHALL remain at the correct geographic position relative to map features


#### Scenario: Marker repositions on zoom

- **WHEN** the user zooms in or out
- **THEN** the marker SHALL re-project to the correct screen position at the new zoom level


#### Scenario: Marker hidden when off-screen

- **WHEN** the user's GPS position is outside the visible map viewport
- **THEN** the marker SHALL NOT be rendered (no off-screen indicators)


#### Scenario: Marker position is stable during map rotation

- **WHEN** the map rotates while the GPS position is visible
- **THEN** the marker SHALL stay at the same geographic location on screen
- **THEN** the direction arrow SHALL rotate to remain aligned with the direction of travel


#### Scenario: Marker stays put at a brief stop

- **WHEN** the vehicle speed drops below the movement threshold (e.g., traffic light) while route guidance is active
- **THEN** the marker SHALL remain at the frozen displayed position
- **AND** the marker SHALL NOT re-project to the raw GPS fix at a different screen position



#### Scenario: Marker resumes without a jump

- **WHEN** the vehicle accelerates after a brief stop and the display loop resumes
- **THEN** the marker SHALL continue from the frozen position along the predicted path
- **AND** the marker SHALL NOT snap back to an anchor or raw-fix position

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

#### Scenario: Marker holds instead of gliding backward at a curve

- **WHEN** the vehicle turns a corner and the predicted position lies ahead of the true fix
- **THEN** the marker SHALL hold at its current position (no backward glide)
- **AND** the marker SHALL resume forward once extrapolation from the new fix advances beyond it

