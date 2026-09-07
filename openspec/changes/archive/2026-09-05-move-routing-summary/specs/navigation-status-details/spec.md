# navigation-status-details Specification

## MODIFIED Requirements

### Requirement: Route description list
The expanded view SHALL show the route description list, styled like the route details view (`RouteSummaryDialog`): each step with its turn icon, distance, and instruction text. Each step SHALL additionally show the time for its segment, matching the per-step time shown in the route summary.

#### Scenario: Steps listed in order
- **WHEN** the full-screen view is open
- **THEN** each route instruction SHALL be listed in order from start to destination
- **AND** each step SHALL show the turn type icon, distance, and instruction text

#### Scenario: Per-step time shown
- **WHEN** the full-screen view is open
- **THEN** each step SHALL show the time for its segment (e.g., "5 min")
- **AND** the time SHALL match the per-step time shown in the route summary step list

#### Scenario: List scrollable
- **WHEN** the instruction list exceeds the visible area
- **THEN** the list SHALL be scrollable
