## MODIFIED Requirements

### Requirement: Start Navigation button
The route summary dialog SHALL have a "Start Navigation" button that starts turn-by-turn navigation.

#### Scenario: Start Navigation starts navigation
- **WHEN** user taps "Start Navigation"
- **THEN** `OSMScoutClient.startNavigationWithVehicle()` SHALL be called with the route handle and selected vehicle
- **AND** the dialog SHALL switch to active navigation mode with step highlighting

### Requirement: Stop Navigation button
When navigation is active, the route summary dialog SHALL show a "Stop Navigation" button instead of "Start Navigation".

#### Scenario: Stop Navigation visible during active nav
- **WHEN** navigation is active
- **THEN** the "Start Navigation" button SHALL be replaced with a "Stop Navigation" button
- **AND** tapping it SHALL stop navigation and return to summary mode

### Requirement: Active navigation mode with step highlighting
The route summary dialog SHALL highlight the current navigation step and auto-scroll to keep it visible.

#### Scenario: Current step highlighted during navigation
- **WHEN** navigation is active
- **AND** the route summary dialog is displayed
- **THEN** the current navigation step SHALL be visually highlighted
- **AND** the step list SHALL auto-scroll to the current step

#### Scenario: Step highlighting updates on progress
- **WHEN** the user progresses to the next navigation step
- **THEN** the highlighted step SHALL advance to the new current step
- **AND** the step list SHALL auto-scroll to keep the current step visible
