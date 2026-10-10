# Spec Delta

## MODIFIED Requirements

### Requirement: Route summary component

The route summary SHALL be a reusable composable component (`RouteSummary`) that displays the total distance, the estimated travel duration, and a scrollable list of turn-by-turn steps for a calculated route. Each listed step's distance and duration SHALL be the values of its own leg (spec: `osmscout-jni` — Per-step leg values on a calculated route), taken from the route's per-step values and formatted by the app, so the listed steps cover the route once instead of each counting the last geometry edge before its manoeuvre.

#### Scenario: Distance and duration shown

- **WHEN** the route summary component is displayed
- **THEN** the total route distance SHALL be shown (e.g., "12.4 km")
- **AND** the estimated travel duration SHALL be shown (e.g., "1h 20min")

#### Scenario: Steps listed in order

- **WHEN** the route summary component is displayed
- **THEN** each step SHALL be listed in order from start to destination
- **AND** each step SHALL show a turn icon, the distance for the step, the time for the step, and the instruction text

#### Scenario: Step values sum to the shown totals

- **WHEN** the route summary is displayed for a 17.3 km route with 19 steps
- **THEN** the steps' distances SHALL add up to the route's length and be of the same order as the shown total distance
- **AND** they SHALL NOT be a few hundred metres in total, which is what they were before this change (the last geometry edge of each step)

#### Scenario: A step's values are its own leg

- **WHEN** a step row of the summary is inspected
- **THEN** its distance and duration SHALL be those of the leg leading to that step's manoeuvre
- **AND** SHALL NOT be those of the last geometry edge before the manoeuvre

#### Scenario: The summary formats its own values

- **WHEN** the summary shows a step's distance and duration on a device with the decimal-comma locale
- **THEN** both values SHALL be formatted by the app's shared formatters with the locale's decimal separator
- **AND** a duration below a minute SHALL be shown in seconds rather than as "0 min"

#### Scenario: Step list scrollable

- **WHEN** the step list exceeds the visible area
- **THEN** the step list SHALL be scrollable
