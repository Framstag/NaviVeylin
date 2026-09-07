## ADDED Requirements

### Requirement: Stop navigation from summary dialog hides route
When navigation is stopped from the route summary dialog, the route polyline and markers SHALL be removed from the map, and the dialog SHALL return to summary mode with the route still available for restart.

#### Scenario: Stop from summary dialog hides route
- **WHEN** navigation is active
- **AND** the user taps "Stop Navigation" in the route summary dialog
- **THEN** the route polyline and markers SHALL be removed from the map
- **AND** the dialog SHALL show the "Start Navigation" button again
- **AND** the route summary SHALL remain available

#### Scenario: Restart from summary dialog redraws route
- **WHEN** the user stops navigation from the summary dialog
- **AND** then taps "Start Navigation" again
- **THEN** the route polyline and markers SHALL be rendered on the map again
