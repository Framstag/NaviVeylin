# Spec Delta

## ADDED Requirements

### Requirement: One route length for a calculated route
A calculated route SHALL expose exactly one length. The per-step legs the same route exposes SHALL sum to
that length, so a client reading the route's total and a client summing its step legs obtain the same
number. A route SHALL NOT carry two different totals for one length of road.

#### Scenario: Total equals the sum of the step legs

- **WHEN** a calculated route exposes a total distance and a per-step distance for each of its instruction lines
- **THEN** the sum of the per-step distances SHALL equal the route's total distance
- **AND** the difference SHALL be no more than the rounding of the per-step values

#### Scenario: A long intercity route has one length

- **WHEN** a long intercity route (tens of kilometres) is calculated
- **THEN** the route's total distance and the sum of its step legs SHALL agree within rounding
- **AND** the total SHALL NOT disagree with that sum by a ratio (measured 2026-10-05: total 72 771 m vs legs 97 416 m, ratio 1.34, on a ~70 km route)

#### Scenario: A short town route has one length

- **WHEN** a short town route (a few kilometres) is calculated
- **THEN** the route's total distance and the sum of its step legs SHALL agree within rounding
- **AND** the total SHALL NOT disagree with that sum by a ratio larger than on a long route

#### Scenario: The total survives the route's geometry being described in steps

- **WHEN** a client obtains only the route's step list for a calculated route
- **THEN** the steps SHALL be sufficient to state that route's total length
- **AND** the client SHALL NOT need a second, independently computed total to report it
