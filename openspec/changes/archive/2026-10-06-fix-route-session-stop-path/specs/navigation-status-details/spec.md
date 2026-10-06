# Spec Delta

## ADDED Requirements

### Requirement: The status card's stop is the phone's shared stop path

A tap on the routing status card's stop control SHALL end navigation through the same phone stop path every other stop control uses. A route-planning session that is open when navigation stops SHALL enter its stopped state (spec: `route-planning-session` — Grace period after navigation is stopped) instead of being cleared behind its back, and the map SHALL return to the mode that was active before navigation started (spec: `map-modes` — Map mode model).

#### Scenario: An open session enters its stopped state

- **WHEN** a route-planning session is open during active navigation
- **AND** the user taps the card's stop control
- **THEN** navigation SHALL end
- **AND** the session SHALL enter its stopped state with the route still drawn
- **AND** a Restart action and an End action SHALL be presented

#### Scenario: No session, no grace

- **WHEN** no route-planning session is open during active navigation
- **AND** the user taps the card's stop control
- **THEN** navigation SHALL end and the route SHALL be cleared
- **AND** no stopped state SHALL be presented

#### Scenario: The expanded view's stop behaves the same

- **WHEN** a route-planning session is open during active navigation
- **AND** the full-screen route description is open
- **AND** the user taps its stop control
- **THEN** the session SHALL enter its stopped state with the route still drawn

#### Scenario: The map returns to the pre-navigation mode

- **WHEN** the user taps the card's stop control
- **AND** the mode before navigation started was BROWSE
- **THEN** the map mode SHALL be BROWSE
- **AND** the map SHALL be north-up with follow disabled
