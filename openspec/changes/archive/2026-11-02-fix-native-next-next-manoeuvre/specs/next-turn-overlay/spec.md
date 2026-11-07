## MODIFIED Requirements

### Requirement: "Next next" hint

When a following manoeuvre is within 200 m of the manoeuvre the overlay announces, the overlay SHALL show a second row with the next-next turn hint, built from the following manoeuvre the native step builder publishes in that step's nested `nextNext` — never from a facts-only route node and never re-derived by the surface.

#### Scenario: Next next shown

- **WHEN** live guidance publishes a manoeuvre whose nested step is a following manoeuvre within 200 m of it
- **THEN** the overlay SHALL display a second row with that manoeuvre's icon and localized short description
- **Case**: `core: ManoeuvreAnnouncementTest` "the published step's own hint is announced unchanged"

#### Scenario: Next next hidden

- **WHEN** the published step carries no nested following manoeuvre
- **THEN** the overlay SHALL NOT show a second row
- **Case**: `core: ManoeuvreAnnouncementTest` "a step without a following manoeuvre announces no hint"

#### Scenario: A facts-only node is never the hint

- **WHEN** the published step ahead is followed by a described route node (a name, a way type) before a manoeuvre
- **THEN** neither the published step nor its nested hint SHALL be that node
- **AND** its stated distance SHALL be the gap between the two manoeuvres
- **Case**: `libosmscout: RouteStepModelTest` "The step ahead is the next manoeuvre, not the next described node"

#### Scenario: A roundabout exit is announced before the ring is entered

- **WHEN** the manoeuvre the overlay announces is a roundabout entry and the route leaves that roundabout at the next manoeuvre, within 200 m
- **THEN** the second row SHALL show the roundabout leave with the exit ordinal the route takes
- **AND** the announced row SHALL stay the entry, with its own turn type
- **Case**: `libosmscout: RouteStepModelTest` "The following manoeuvre is announced before a roundabout is entered"
