## Purpose

Shows the next manoeuvre during active navigation — turn type icon, distance, street name, and a "next next" hint for close-following turns.

## ADDED Requirements

### Requirement: Next turn overlay visible during navigation
When navigation is active, a next-turn overlay SHALL be displayed on the map showing the upcoming manoeuvre.

#### Scenario: Overlay shows on navigation start
- **WHEN** navigation starts
- **THEN** a next-turn overlay SHALL appear on the map
- **AND** it SHALL show the turn type icon for the first instruction
- **AND** it SHALL show the distance to the next turn
- **AND** it SHALL show the street name to turn into

#### Scenario: Overlay updates on each instruction
- **WHEN** `NavigationListener.onNextRouteInstruction()` is called
- **THEN** the overlay SHALL update with the new turn type, distance, and street name

#### Scenario: Overlay hides on navigation stop
- **WHEN** navigation stops
- **THEN** the next-turn overlay SHALL be hidden

### Requirement: Turn type icon
The overlay SHALL display a visual icon representing the turn type (left, right, straight, roundabout, etc.).

#### Scenario: Left turn icon
- **WHEN** the next instruction has `turnType = LEFT`
- **THEN** the overlay SHALL show a left-turn icon

#### Scenario: Roundabout icon
- **WHEN** the next instruction has `turnType = ROUNDABOUT_ENTER`
- **THEN** the overlay SHALL show a roundabout icon

### Requirement: Distance display
The overlay SHALL show the distance to the next manoeuvre, formatted appropriately (meters or kilometers).

#### Scenario: Distance in meters
- **WHEN** distance to next turn is less than 1000m
- **THEN** the overlay SHALL show the distance in meters (e.g., "450 m")

#### Scenario: Distance in kilometers
- **WHEN** distance to next turn is 1000m or more
- **THEN** the overlay SHALL show the distance in kilometers (e.g., "1.2 km")

### Requirement: "Next next" hint
When a following manoeuvre is within 200m of the current one, the overlay SHALL show a second row with the next-next turn hint.

#### Scenario: Next next shown
- **WHEN** `RouteInstruction.hasNextNext()` returns true
- **THEN** the overlay SHALL display a second row with the next-next turn type and description

#### Scenario: Next next hidden
- **WHEN** `RouteInstruction.hasNextNext()` returns false
- **THEN** the overlay SHALL NOT show a second row
