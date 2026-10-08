# Spec Delta

## ADDED Requirements

### Requirement: The published route length is a length of that route

The length a calculated route publishes SHALL be derived from the route's own geometry or from its
description. It SHALL NOT be the straight-line (air-line) distance between the route's start and target.
Where both are available they SHALL agree, since one route has one length of road.

#### Scenario: A published length tracks the route that was drawn

- **WHEN** a calculated route's published total distance and its published polyline are read
- **THEN** the total SHALL agree with the great-circle length of that polyline within the description's
  own agreement with it (measured 2026-10-05: description over polyline 1.0014 / 1.0021 / 0.9964)
- **AND** the total SHALL NOT be the start-to-target air-line estimate (measured 2026-10-05: 0.748 /
  0.795 / 0.552 of the polyline, deficits 24.5 km / 4.3 km / 0.7 km)

#### Scenario: A long intercity route carries a real length

- **WHEN** a long intercity route (tens of kilometres) is calculated
- **THEN** the published total SHALL lie within a few percent of the drawn route's length
- **AND** it SHALL NOT be closer to the great-circle distance between its endpoints than to the drawn
  length (the ~70 km case: 72 771 m published against 97 283 m drawn and ~66 km air-line)

#### Scenario: A route without a description still carries a route length

- **WHEN** a calculated route exposes no per-step description, so the per-step sum cannot state its length
- **THEN** the route's published total SHALL still be a length of the route it publishes, not the
  start-to-target estimate
- **AND** a client that falls back to that total SHALL obtain a route length

### Requirement: The start/target estimate is not exposed as a route length

The router's air-line estimate MAY remain available where the router itself needs it — the cost limit and
the progress denominator — but no client of the route result SHALL receive it as the route's length, and a
field that carries it SHALL be named or documented as an estimate.

#### Scenario: No route field states the estimate as a distance

- **WHEN** the fields of a calculated route result are inspected
- **THEN** no field documented or named as the route's distance or length SHALL hold the start-to-target
  estimate
- **AND** a field that does hold it SHALL say that it is an estimate

#### Scenario: A consumer that reads the length gets a length

- **WHEN** a client reads the route's total distance — the card statistic, the step list's sum, the
  progress denominator, or a fallback for a route without steps
- **THEN** it SHALL obtain a length of that route, on every code path that publishes one
