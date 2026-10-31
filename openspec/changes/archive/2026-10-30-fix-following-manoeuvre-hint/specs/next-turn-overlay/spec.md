## MODIFIED Requirements

### Requirement: "Next next" hint

When a following manoeuvre is within 200 m of the manoeuvre the overlay announces, the overlay SHALL show a second row with the next-next turn hint, built from that following manoeuvre of the route's step list — never from the published step's own nested `nextNext`, and never from a facts-only route node.

#### Scenario: Next next shown

- **WHEN** the route's step list carries a manoeuvre after the announced one, within 200 m of it
- **THEN** the overlay SHALL display a second row with that manoeuvre's icon and localized short description
- **Case**: `core: ManoeuvreAnnouncementTest` "the following manoeuvre within the window is the hint"

#### Scenario: Next next hidden

- **WHEN** no manoeuvre of the route's step list lies within 200 m after the announced one
- **THEN** the overlay SHALL NOT show a second row
- **Case**: `core: ManoeuvreAnnouncementTest` "a following manoeuvre beyond the window is not shown"

#### Scenario: A facts-only node is never the hint

- **WHEN** the route's step list carries a facts-only node (a name, a way type, a max speed) between the announced manoeuvre and the following one
- **THEN** the hint SHALL be the following manoeuvre, not that node
- **AND** its stated distance SHALL be the gap between the two manoeuvres
- **Case**: `core: ManoeuvreAnnouncementTest` "a facts-only node between the manoeuvres is not the hint"

#### Scenario: A roundabout exit is announced before the ring is entered

- **WHEN** the manoeuvre the overlay announces is a roundabout entry and the route leaves that roundabout at the next manoeuvre, within 200 m
- **THEN** the second row SHALL show the roundabout leave with the exit ordinal the route takes
- **AND** the announced row SHALL stay the entry, with its own turn type
- **Case**: `core: ManoeuvreAnnouncementTest` "the roundabout exit is the hint before the ring is entered"
