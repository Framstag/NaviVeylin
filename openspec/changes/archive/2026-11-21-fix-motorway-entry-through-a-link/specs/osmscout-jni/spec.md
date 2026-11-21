# Spec Delta

## ADDED Requirements

### Requirement: A motorway manoeuvre outranks a turn on its node

A published step SHALL state the manoeuvre its node is, not the first manoeuvre a hook reported for it. Where one
node carries a motorway enter, change or leave beside a turn, the step's kind SHALL be the motorway manoeuvre, and
the step SHALL keep the records of that node — the signposted destination among them.

#### Scenario: An entry through a link is the entry, not the turn beside it

- **WHEN** a route enters a motorway through a `highway_trunk_link` and the route description carries the link's
  direction change and the motorway enter on the same node
- **THEN** the published step SHALL report `motorway_enter`, with the way it leads onto
- **AND** a client SHALL NOT read the entry as a plain turn
- **Case**: `app/src/main/cpp/libosmscout/Tests/src/RouteStepModelTest.cpp` — "A motorway enter through a link is
  the entry, not the turn"

#### Scenario: The entry keeps the signposted destination its node carries

- **WHEN** the node of such an entry carries a destination record of its own — the ramp's tag or the record the
  manoeuvre took over from the node it was decided at
- **THEN** the published step SHALL carry that record
- **AND** a surface wording the manoeuvre SHALL render the way it leads onto together with it ("Enter onto B 236
  towards Dortmund"), not as a bare turn
- **Case**: `app/src/main/cpp/libosmscout/Tests/src/RouteStepModelTest.cpp` — "A motorway enter through a link is
  the entry, not the turn"

#### Scenario: A plain turn is still a turn

- **WHEN** a node carries a turn description and no motorway description
- **THEN** the published step SHALL report `turn`
- **AND** the ranks SHALL change no other kind a node publishes
- **Case**: `app/src/main/cpp/libosmscout/Tests/src/RouteStepModelTest.cpp` — "A description converts into one step
  per described node, in route order"