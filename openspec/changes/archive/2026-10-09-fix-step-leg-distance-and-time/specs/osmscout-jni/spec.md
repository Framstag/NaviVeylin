# Spec Delta

## ADDED Requirements

### Requirement: Per-step leg values on a calculated route

A calculated route SHALL expose one distance (in metres) and one duration (in seconds) per instruction line,
each describing the leg that **ends at that line's manoeuvre** — never a value measured between two
consecutive route nodes that carry no instruction line. Index alignment SHALL hold with the route's
instruction lines and with the per-step positions, and the legs SHALL cover the route once: their sum SHALL
be the route's own length as the description measures it, and SHALL NOT be one geometry edge per step.
(The router's overall distance is a separate native number; the two may disagree — TODO.md §126.) Index
alignment SHALL hold with the route's instruction lines and with the per-step positions. When the values
cannot be aligned one-to-one with the instruction lines, both SHALL be absent for that route rather than
shifted.

#### Scenario: Legs sum to the route's total

- **WHEN** a route of 17.3 km with 19 instruction lines is calculated
- **THEN** the per-step distances SHALL cover the route once, i.e. sum to the route's own total as the description measures it (the router's overall distance is a second, independent number - measured on device 2026-10-05 as 1.34× on a 70 km route, TODO.md §126)
- **AND** the sum SHALL NOT be the length of a single geometry edge per step (measured before this change: a few hundred metres for a 17,3 km route)

#### Scenario: A step's values belong to its own leg

- **WHEN** a step's values are inspected
- **THEN** its distance SHALL be the route distance from the previous instruction's manoeuvre to this one
- **AND** its duration SHALL be the travel time of that same leg
- **AND** the value SHALL NOT be the distance or time of the last geometry edge before the manoeuvre

#### Scenario: Unaligned values are absent, not shifted

- **WHEN** the per-step values cannot be aligned one-to-one with the instruction lines
- **THEN** no per-step distance and no per-step duration SHALL be published for the route
- **AND** the instruction lines SHALL remain valid and complete

#### Scenario: Start line owns no leg

- **WHEN** the route's first instruction line is the start line
- **THEN** its distance SHALL be zero and it SHALL carry no duration

### Requirement: Instruction segment time is the leg's travel time

The time a route exposes for an instruction SHALL be the travel time of the leg that ends at that
instruction's manoeuvre, so that a client can tell how long driving that step takes. It SHALL NOT be the
travel time between two consecutive route nodes, and it SHALL agree with the per-step duration the same
route exposes for that step.

#### Scenario: A step's time covers its whole leg, not one of its edges

- **WHEN** a step's leg spans several hundred metres between two manoeuvres
- **THEN** its reported time SHALL be the travel time of that whole leg, i.e. at least tens of seconds
- **AND** SHALL NOT be the sub-second to few-second time of a single geometry edge inside the leg

#### Scenario: A city step is reported in minutes, not in seconds

- **WHEN** a step's leg covers several hundred metres of a city route
- **THEN** its reported time SHALL be on the order of the driving time of that leg
- **AND** SHALL NOT be the sub-second to few-second time of a single geometry edge

#### Scenario: The two step lists of one route agree

- **WHEN** a route exposes per-step durations and an instruction list built from the same route
- **THEN** the time of an instruction SHALL equal the per-step duration of the same step

### Requirement: A next instruction's time is the remaining time of its leg

For the next instruction — the one the instruction list is asked for ahead of the current position — the
time SHALL be the **remaining** travel time of that instruction's leg, consistent with the remaining
distance the same instruction reports, and it SHALL reach zero as the manoeuvre is reached.

#### Scenario: The arrival estimate shrinks while the leg is driven

- **WHEN** the next instruction is reported, then reported again after the driver has covered most of its leg
- **THEN** the second remaining time SHALL be smaller than the first
- **AND** the estimate of arriving at that manoeuvre SHALL NOT stay at its initial value while the manoeuvre is approached

#### Scenario: Arrival estimate is not seconds away for a long leg

- **WHEN** the next manoeuvre's leg is 900 m in city traffic
- **THEN** the remaining time reported for it SHALL be on the order of a minute
- **AND** an arrival time derived from it SHALL NOT land within the next few seconds
