# Spec Delta

## MODIFIED Requirements

### Requirement: Route description list

The expanded view SHALL show the route description list, styled like the route details view (`RouteSummaryDialog`): each step with its turn icon, distance, and instruction text. A step's distance SHALL be the distance of that step's own segment — the leg leading to its manoeuvre — and each step SHALL additionally show the time for its segment, so both values match the per-step values shown in the route summary (spec: `routing-summary`). A value whose meaning is a route-start-to-here distance next to a leg time SHALL NOT be shown.

#### Scenario: Steps listed in order

- **WHEN** the full-screen view is open
- **THEN** each route instruction SHALL be listed in order from start to destination
- **AND** each step SHALL show the turn type icon, distance, and instruction text

#### Scenario: Per-step time shown

- **WHEN** the full-screen view is open
- **THEN** each step SHALL show the time for its segment (e.g., "5 min")
- **AND** the time SHALL match the per-step time shown in the route summary step list

#### Scenario: Per-step distance is the step's own segment

- **WHEN** the full-screen view is open for a 17.3 km route
- **THEN** each step's distance SHALL be the leg leading to that step's manoeuvre
- **AND** the distances SHALL NOT be distances measured from the route's start

#### Scenario: List scrollable

- **WHEN** the instruction list exceeds the visible area
- **THEN** the list SHALL be scrollable
