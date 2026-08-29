## MODIFIED Requirements

### Requirement: GPS position marker on car map

The system SHALL display the current GPS position as a marker on the car map, reusing the existing `LocationService.location` data. In follow mode between fixes, the marker SHALL be drawn at the predicted position (extrapolated from the last fix, speed, and heading) so the marker glides with the blitted map.

#### Scenario: GPS marker shown

- **WHEN** GPS position is available
- **THEN** a position marker appears on the car map at the current coordinates

#### Scenario: GPS marker updates

- **WHEN** the vehicle moves more than 5 meters
- **THEN** the GPS marker position updates on the car map

#### Scenario: GPS marker glides between fixes

- **WHEN** the vehicle moves at constant speed between two fixes in follow mode
- **THEN** the marker SHALL move incrementally each display frame along the predicted path
- **AND** the marker SHALL NOT jump from fix to fix

### Requirement: Map re-renders on viewport change

The system SHALL update the displayed map when the viewport center, zoom, or rotation changes. A viewport center change within the overrun region SHALL be served by blitting the overrun buffer; a full re-render SHALL occur only when the center exits the overrun region or when zoom or rotation changes.

#### Scenario: Re-render on pan

- **WHEN** the user pans the map
- **THEN** the map updates at the new center position (blit within overrun, full render beyond it)

#### Scenario: Re-render on zoom

- **WHEN** the user zooms in or out
- **THEN** the map re-renders at the new magnification level

#### Scenario: Re-render on rotation

- **WHEN** the map rotation changes
- **THEN** the map re-renders at the new angle

#### Scenario: Follow-mode move served by blit

- **WHEN** the vehicle moves and the new viewport center stays within the overrun region
- **THEN** the map SHALL be updated by blitting the overrun buffer
- **AND** no full native render SHALL be initiated
