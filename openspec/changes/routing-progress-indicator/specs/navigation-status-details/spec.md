## ADDED Requirements

### Requirement: Route progress lines in routing status card

During active navigation, the routing status card SHALL show the driver's progress along the route as two small horizontal lines: one for percent of distance traveled and one for percent of estimated travel time elapsed. The lines SHALL be small in height, SHALL NOT show labels or percent values, and SHALL each carry a small icon for differentiation (distance line, time line).

#### Scenario: Progress lines always visible during navigation

- **WHEN** navigation is active
- **THEN** the routing status card SHALL show a distance progress line and a time progress line

#### Scenario: Progress lines not shown outside navigation

- **WHEN** navigation is not active
- **THEN** the routing status card SHALL NOT show the progress lines

#### Scenario: Distance line reflects traveled distance

- **WHEN** navigation is active and the driver has traveled part of the route
- **THEN** the distance line SHALL reflect the percent of the total route distance traveled, computed from the remaining distance relative to the total distance

#### Scenario: Time line reflects elapsed time

- **WHEN** navigation is active and part of the estimated travel time has elapsed
- **THEN** the time line SHALL reflect the percent of the estimated travel time elapsed, computed from the elapsed time relative to the estimated total travel time

#### Scenario: Lines differentiated by icon

- **WHEN** the progress lines are displayed
- **THEN** the distance line SHALL carry a distance icon and the time line SHALL carry a time icon

#### Scenario: No labels or percent values shown

- **WHEN** the progress lines are displayed
- **THEN** the lines SHALL NOT show text labels or percent values

#### Scenario: Progress values clamped to valid range

- **WHEN** the computed progress percent is below 0 or above 100
- **THEN** the line SHALL be rendered at the clamped 0–100 position
