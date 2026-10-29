# Spec Delta — route-analysis

## ADDED Requirements

### Requirement: The route's start anchor is the published start position

The first step of a calculated route (the start line) SHALL be anchored at the position the route's
geometry is published from, so that analysing the start line shows the place the route begins at and
the start line owns no leg. When the route begins with an approach from a requested start to the
network, that position SHALL be the requested start; the invariant is that the start anchor and the
polyline's first vertex are the same position, not that the start anchor is a network node.

#### Scenario: Start anchor equals the polyline's first vertex

- **WHEN** a route with a snapped start is analysed
- **THEN** the start line's position SHALL be the polyline's first vertex
- **AND** the start line SHALL own no leg
  (case: `RouteStepSegmentsTest.aPrependedStartAnchorsStepZeroAndOwnsNoLeg`)

#### Scenario: The first turn's leg starts at the published start

- **WHEN** a route with a snapped start is analysed and the first turn is selected
- **THEN** the first turn's highlighted segment SHALL begin at the polyline's first vertex
- **AND** the segment's last vertex SHALL be within the polyline-match tolerance of that step's own
  manoeuvre
  (case: `RouteStepSegmentsTest.aPrependedStartAnchorsStepZeroAndOwnsNoLeg`)

#### Scenario: The start anchor is not shifted when the start is on the network

- **WHEN** a route whose start already lies on the routable network is analysed
- **THEN** the start line's position and the highlighted first leg SHALL be the ones the routed geometry
  produces
  (case: `RouteStepSegmentsTest.straight route maps each step to the leg leading to its manoeuvre`)
