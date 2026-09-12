## MODIFIED Requirements

### Requirement: Road info uses DescriptionService lookup at current position
The system SHALL report the name, reference, and type of the road the vehicle is currently on during an active navigation session, taken from the route's resolved way at the current position — not from an area search at the coordinate. When the vehicle is off the planned route, the system SHALL fall back to the bearing-aware road lookup at the estimated position.

#### Scenario: Data source
- **GIVEN** a position estimate from the navigation engine with the vehicle on the planned route
- **WHEN** the system looks up road info
- **THEN** it uses the route's resolved way at the current point (name, ref, type)
- **AND** no area search is performed at the coordinate

#### Scenario: On-route road info from the route
- **GIVEN** an active navigation session with the vehicle on the planned route
- **WHEN** a position estimate is received
- **THEN** the road info comes from the route's way at the current point (name, ref, type)
- **AND** no area search is performed at the coordinate

#### Scenario: Road info updates on street change
- **GIVEN** an active navigation session
- **WHEN** the vehicle moves onto a different way of the route
- **THEN** the road info updates to the new way's name, ref, and type

#### Scenario: Off-route road info via bearing-aware lookup
- **GIVEN** an active navigation session
- **WHEN** the vehicle is off the planned route
- **THEN** the road info comes from the bearing-aware road lookup at the estimated position
- **AND** the next-turn instructions remain visible

#### Scenario: Road info lookup is throttled
- **GIVEN** frequent position updates
- **WHEN** a position update arrives less than 2 seconds after the last lookup
- **THEN** the lookup is skipped
- **AND** the previous road info remains displayed

#### Scenario: Road info lookup skips small movements
- **GIVEN** the vehicle has moved less than ~50 meters since the last lookup
- **WHEN** a position update arrives
- **THEN** the lookup is skipped
- **AND** the previous road info remains displayed

## ADDED Requirements

### Requirement: Current road info shown in free driving (no active route)
The system SHALL display the current road's name and ref on the phone map when no route is active, derived from the bearing-aware road lookup at the GPS position, so the driver sees the street they are driving on without starting navigation.

#### Scenario: Street label shown while driving
- **GIVEN** the phone map is visible and no route is active
- **WHEN** the GPS position is on a road with a name or ref
- **THEN** the map shows a street label with the road's ref and name (e.g. "B 1 Hauptstrasse")

#### Scenario: Street label updates on street change
- **GIVEN** the phone map is visible and no route is active
- **WHEN** the vehicle moves onto a different road
- **THEN** the street label updates to the new road

#### Scenario: No label when no road found
- **GIVEN** the phone map is visible and no route is active
- **WHEN** no road is found at the GPS position
- **THEN** no street label is shown and no stale text from a previous road remains

#### Scenario: Street label hidden during navigation
- **GIVEN** an active navigation session
- **WHEN** the phone map is visible
- **THEN** the free-driving street label is not shown (the navigation road-info row is used instead)
