# current-road-info Specification (Delta)

## Purpose

Report and display the name, reference, and type of the road the vehicle is currently on during an active navigation session, giving the driver immediate spatial context.

## MODIFIED Requirements

### Requirement: Current road info is displayed in the route status view

The system SHALL display the current road information as the first row inside the NavigationStateOverlay card, above the ETA/Dist/Speed stats row.

#### Scenario: Road info visible during navigation

- **GIVEN** an active navigation session with road info available
- **WHEN** a position estimate is received
- **THEN** the road info row shows the road reference, type, and name in a single line
- **AND** the row uses the same font size and styling as the route status labels

#### Scenario: Road info row larger font

- **WHEN** the road info row is displayed
- **THEN** the road name SHALL use a slightly larger font size than the previous `bodyMedium` style (e.g. `titleMedium`)

#### Scenario: Road info row centered

- **WHEN** the road info row is displayed
- **THEN** the road name SHALL be horizontally centered within the card

#### Scenario: No road info available

- **GIVEN** an active navigation session
- **WHEN** no road info is available (off-route, unnamed road, etc.)
- **THEN** the road info row is hidden
- **AND** the next-turn instructions remain visible
