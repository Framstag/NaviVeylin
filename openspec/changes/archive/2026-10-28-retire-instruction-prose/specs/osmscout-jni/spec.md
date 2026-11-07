# osmscout-jni

## RENAMED Requirements

- FROM: `### Requirement: A route instruction carries the roundabout exit ordinal`
- TO: `### Requirement: A route step carries the roundabout exit ordinal`

- FROM: `### Requirement: A route instruction carries the roundabout circulation direction`
- TO: `### Requirement: A route step carries the roundabout circulation direction`

- FROM: `### Requirement: An unbuildable instruction never reaches a listener as null`
- TO: `### Requirement: An unbuildable step never reaches a listener as null`

## MODIFIED Requirements

### Requirement: Per-step leg values on a calculated route

A calculated route SHALL expose, on every step it publishes, one distance (in metres) and one duration (in
seconds), each describing the leg that **ends at that step's manoeuvre** — never a value measured between two
consecutive route nodes that carry no step. The legs SHALL cover the route once: their sum SHALL be the route's
own length as the description measures it, and SHALL NOT be one geometry edge per step. (The router's overall
distance is a separate native number; the two may disagree — TODO.md §126.) A route that yields no step SHALL
publish no per-step values, so a client never reads a value that belongs to no manoeuvre.

#### Scenario: Legs sum to the route's total

- **WHEN** a route of 17.3 km with 19 steps is calculated
- **THEN** the per-step distances SHALL cover the route once, i.e. sum to the route's own total as the description measures it (the router's overall distance is a second, independent number - measured on device 2026-10-05 as 1.34× on a 70 km route, TODO.md §126)
- **AND** the sum SHALL NOT be the length of a single geometry edge per step (measured before this change: a few hundred metres for a 17,3 km route)

#### Scenario: A step's values belong to its own leg

- **WHEN** a step's values are inspected
- **THEN** its distance SHALL be the route distance from the previous step's manoeuvre to this one
- **AND** its duration SHALL be the travel time of that same leg
- **AND** the value SHALL NOT be the distance or time of the last geometry edge before the manoeuvre

#### Scenario: Unaligned values are absent, not shifted

- **WHEN** a route yields no described node at all
- **THEN** the route SHALL publish no step and therefore no per-step distance and no per-step duration
- **AND** the route's own geometry and totals SHALL remain valid and complete

#### Scenario: Start line owns no leg

- **WHEN** the route's first step is the start step
- **THEN** its distance SHALL be zero and it SHALL carry no duration

### Requirement: A next instruction's time is the remaining time of its leg

For the step ahead — the one published for the current position — the remaining time SHALL be the **remaining**
travel time of that step's leg, consistent with the remaining distance the same step reports, and it SHALL reach
zero as the manoeuvre is reached.

#### Scenario: The arrival estimate shrinks while the leg is driven

- **WHEN** the step ahead is published, then published again after the driver has covered most of its leg
- **THEN** the second remaining time SHALL be smaller than the first
- **AND** the estimate of arriving at that manoeuvre SHALL NOT stay at its initial value while the manoeuvre is approached

#### Scenario: Arrival estimate is not seconds away for a long leg

- **WHEN** the next manoeuvre's leg is 900 m in city traffic
- **THEN** the remaining time reported for it SHALL be on the order of a minute
- **AND** an arrival time derived from it SHALL NOT land within the next few seconds

## REMOVED Requirements

### Requirement: Instruction segment time is the leg's travel time

**Reason**: The requirement described a second, parallel projection of the same values — an instruction list's
segment times — that existed only because the bridge published an instruction type besides the step model. With
the instruction type gone there is one source for a step's travel time, and two requirements stating it invite a
future reader to believe two sources still exist.

**Migration**: Read the step's own leg duration (`Per-step leg values on a calculated route`, scenario "A step's
values belong to its own leg"). The behaviour it protected is unchanged and still proven — the case
`RouteStepModelBridgeTest` "step legs sum to the route length" and `app: RouteStepValuesTest` "rows sum to the
route length" replace the instrumented assertion that a segment time is not a single edge.
