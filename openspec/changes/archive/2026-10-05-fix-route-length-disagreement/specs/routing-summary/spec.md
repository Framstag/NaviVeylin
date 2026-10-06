# Spec Delta

## MODIFIED Requirements

### Requirement: Route summary component
The route summary SHALL be a reusable composable component (`RouteSummary`) that displays the total distance, the estimated travel duration, and a scrollable list of turn-by-turn steps for a calculated route. The total distance it displays SHALL be the route's length as its listed step legs sum it (spec: `osmscout-jni` — One route length for a calculated route), so the summary's header and its step list describe one route of one length.

#### Scenario: Distance and duration shown
- **WHEN** the route summary component is displayed
- **THEN** the total route distance SHALL be shown (e.g., "12.4 km")
- **AND** the estimated travel duration SHALL be shown (e.g., "1h 20min")
- **AND** the shown total SHALL equal the sum of the listed steps' distances within rounding

#### Scenario: Steps listed in order
- **WHEN** the route summary component is displayed
- **THEN** each step SHALL be listed in order from start to destination
- **AND** each step SHALL show a turn icon, the distance for the step, the time for the step, and the instruction text

#### Scenario: Step list scrollable
- **WHEN** the step list exceeds the visible area
- **THEN** the step list SHALL be scrollable
