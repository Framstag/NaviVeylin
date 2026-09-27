# Delta: next-turn-overlay (modified: distance must not freeze at 0 m)

## MODIFIED Requirements

### Requirement: Distance display

The overlay SHALL show the distance to the next manoeuvre, formatted appropriately (meters or kilometers). The distance SHALL count down as the vehicle approaches and SHALL NOT display 0 m while the manoeuvre is still ahead and the vehicle is moving, including after a reroute; after a reroute the overlay SHALL show the new route's current distance instead of the previous route's value.

#### Scenario: Distance in meters

- **WHEN** distance to next turn is less than 1000m
- **THEN** the overlay SHALL show the distance in meters (e.g., "450 m")

#### Scenario: Distance in kilometers

- **WHEN** distance to next turn is 1000m or more
- **THEN** the overlay SHALL show the distance in kilometers (e.g., "1.2 km")

#### Scenario: Distance counts down to the manoeuvre

- **WHEN** the vehicle is moving and the next manoeuvre is still ahead on the route
- **THEN** the shown distance decreases as the vehicle approaches
- **AND** it SHALL NOT show 0 m while the manoeuvre node is still ahead of the vehicle
- **AND** when the shown distance is 0 m, the manoeuvre SHALL be the one the vehicle is turning into or already passing

#### Scenario: Distance reset after reroute

- **WHEN** a reroute replaces the route while navigation is active
- **THEN** the overlay shows the new route's next manoeuvre with its own distance
- **AND** the previous route's distance and manoeuvre SHALL NOT persist on the overlay

#### Scenario: Distance not frozen when reroute suppressed

- **WHEN** the vehicle is off the planned route, a reroute would be required but is suppressed, and no fresh instruction data arrives
- **THEN** the overlay SHALL NOT keep a stale distance for a manoeuvre the vehicle has passed or cannot reach
- **AND** it SHALL either show an up-to-date instruction or clear the distance until valid data is available
