## MODIFIED Requirements

### Requirement: Start Navigation button in route panel
When a route is calculated and navigation is not active, the route panel SHALL display a "Start Navigation" button.

#### Scenario: Start Navigation button visible
- **WHEN** a route is calculated
- **AND** navigation is not active
- **THEN** a "Start Navigation" button SHALL be visible in the route panel
- **AND** tapping it SHALL start navigation

#### Scenario: Start Navigation hidden during active nav
- **WHEN** navigation is active
- **THEN** the "Start Navigation" button SHALL be replaced with a "Stop Navigation" button
- **AND** tapping it SHALL stop navigation
