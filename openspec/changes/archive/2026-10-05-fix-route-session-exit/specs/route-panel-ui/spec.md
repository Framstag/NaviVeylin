# Spec Delta — route-panel-ui

## MODIFIED Requirements

### Requirement: Session overlay dismissal ends the session

The session overlay SHALL be dismissable, and dismissing it SHALL end the session: the route polyline and the start/target markers SHALL be removed from the map, the session state SHALL be reset, and the overlay SHALL be gone. Collapsing the overlay to the compact anchor SHALL NOT be a dismissal.

#### Scenario: Dismiss removes route

- **WHEN** a route is displayed in the session
- **AND** the user dismisses the overlay without starting navigation
- **THEN** the route polyline and the start/target markers SHALL be removed from the map
- **AND** re-opening the session SHALL show the empty state

#### Scenario: Collapsing is not dismissing

- **WHEN** a route is displayed in the session
- **AND** the user collapses the overlay to the compact anchor
- **THEN** the route polyline and markers SHALL remain visible
- **AND** the session state SHALL be preserved

#### Scenario: The close control dismisses without collapsing

- **WHEN** a route is displayed in the session and the card is in max
- **AND** the user activates the card's close control
- **THEN** the overlay SHALL be dismissed and the session SHALL end
- **AND** the route SHALL be removed from the map

#### Scenario: A dismissed overlay stays dismissed

- **WHEN** the user dismisses the overlay
- **THEN** no part of the session's surface (card, strip or affordance) SHALL remain visible
- **AND** re-opening the session SHALL open it in editing, not in a route-ready state
