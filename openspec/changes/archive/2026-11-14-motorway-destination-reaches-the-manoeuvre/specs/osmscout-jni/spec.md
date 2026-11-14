# Spec Delta

## ADDED Requirements

### Requirement: A motorway manoeuvre's step carries the signposted destination

A published step that describes entering or changing a motorway SHALL carry the signposted destination of the
way it leads onto, wherever in the route description that record was placed. When the step's own described node
carries no record but the node at which the manoeuvre was decided carries one, the step SHALL carry that record.
A step whose route holds no such record for that way SHALL carry none.

#### Scenario: A change through a motorway link carries the record of the node it was decided at

- **WHEN** a route changes motorway through a `highway_motorway_link` and the route description places the
  signposted destination on the node before the link, while the change sits on the link node
- **THEN** the published change step carries that destination record
- **AND** a surface wording the manoeuvre renders the motorway together with it ("A 40 towards Essen"),
  not the bare reference
- **Case**: `app/src/main/cpp/libosmscout/Tests/src/RouteStepModelTest.cpp` — "A motorway change through a link
  carries the destination of the way it leads onto"

#### Scenario: A step that carries its own record keeps it

- **WHEN** the node of a motorway enter or change carries a destination record of its own, and the node before
  it carries a different one
- **THEN** the published step carries its own record, not the neighbouring one
- **Case**: `app/src/main/cpp/libosmscout/Tests/src/RouteStepModelTest.cpp` — "A motorway step keeps its own
  destination when its node carries one"

#### Scenario: No record on the node or the one it was decided at

- **WHEN** neither the manoeuvre's node nor the node it was decided at carries a destination record
- **THEN** the published step carries none
- **AND** every surface keeps the wording it has today for that step
- **Case**: `app/src/main/cpp/libosmscout/Tests/src/RouteStepModelTest.cpp` — "A motorway change carries no
  destination when no node carries a record"
