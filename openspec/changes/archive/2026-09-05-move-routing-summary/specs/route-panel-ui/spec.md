# route-panel-ui Specification

## MODIFIED Requirements

### Requirement: Turn-by-turn instruction list
After successful route calculation, the route panel SHALL show the route summary inline, below the calculate button. The route panel SHALL remain open and SHALL NOT be dismissed. The route panel SHALL NOT show the instruction list separately — the route summary component contains the instructions.

#### Scenario: Route summary dialog triggered after calculation
- **WHEN** route calculation completes successfully
- **THEN** the route summary SHALL be shown inline in the route panel below the calculate button
- **AND** the route panel SHALL remain open
- **AND** the instruction list SHALL NOT be shown separately in the route panel

#### Scenario: Route panel re-opens on summary dismiss
- **WHEN** the route summary dialog is dismissed
- **THEN** the route panel SHALL re-open with all previous state intact (start, destination, vehicle, route)

#### Scenario: Instructions scrollable in summary dialog
- **WHEN** the route summary component is displayed in the route panel
- **THEN** the instruction list SHALL be scrollable within the summary component

### Requirement: Start Navigation button in route panel
When a route is calculated and navigation is not active, the route panel SHALL display a "Start Navigation" button below the calculate button and above the route summary component.

#### Scenario: Start Navigation button visible
- **WHEN** a route is calculated
- **AND** navigation is not active
- **THEN** a "Start Navigation" button SHALL be visible in the route panel below the calculate button
- **AND** the button SHALL be positioned above the route summary component
- **AND** tapping it SHALL start navigation
- **AND** the route panel SHALL close

#### Scenario: Start Navigation hidden during active nav
- **WHEN** navigation is active
- **THEN** the "Start Navigation" button SHALL be replaced with a "Stop Navigation" button
- **AND** tapping it SHALL stop navigation
