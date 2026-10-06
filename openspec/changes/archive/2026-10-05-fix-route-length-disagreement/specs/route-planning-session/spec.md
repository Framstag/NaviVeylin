# Spec Delta

## ADDED Requirements

### Requirement: The planning card's route statistics state the route's length
When a route exists, the planning card SHALL report the route's total distance and its duration. The reported distance SHALL be the route's length as the card's own step list sums it (spec: `osmscout-jni` — One route length for a calculated route), so the card's statistic and the steps below it state one length.

#### Scenario: Headline agrees with the card's step list

- **WHEN** a route exists and the planning card shows the route's distance
- **THEN** the shown distance SHALL equal the sum of the card's listed steps' distances within rounding
- **AND** the card SHALL NOT show a distance that disagrees with that sum by a ratio

#### Scenario: A long route's statistic is not a second total

- **WHEN** a long intercity route (tens of kilometres) is calculated
- **THEN** the card's distance SHALL agree with the sum of its step list within rounding
- **AND** the two SHALL NOT differ by a factor (measured 2026-10-05: 1.34× between the card statistic and the step legs on a ~70 km route)

#### Scenario: The statistic follows the route that is shown

- **WHEN** a route is replaced by a newly calculated one
- **THEN** the card's distance SHALL be the new route's length
- **AND** it SHALL NOT keep the previous route's statistic
