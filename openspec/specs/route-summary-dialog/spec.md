# route-summary-dialog Specification

## Purpose

Shows a focused route summary after calculation with key statistics, a scrollable step list, and a Start Navigation button. Reusable during active navigation with current-step highlighting.

## Requirements

### Requirement: Route summary dialog shown after calculation
When route calculation completes successfully, the route panel SHALL trigger display of the route summary dialog on top of the route panel.

#### Scenario: Dialog appears after successful calculation
- **WHEN** route calculation completes successfully
- **THEN** the route summary dialog SHALL appear as an overlay on top of the route panel
- **AND** the route panel SHALL remain open behind the dialog

#### Scenario: Dialog not shown on calculation failure
- **WHEN** route calculation fails
- **THEN** the route summary dialog SHALL NOT appear
- **AND** an error message SHALL be displayed in the route panel

### Requirement: Route statistics displayed
The route summary dialog SHALL display key route statistics: total distance and estimated travel time.

#### Scenario: Distance shown
- **WHEN** the route summary dialog is displayed
- **THEN** the total route distance SHALL be shown (e.g., "12.4 km")

#### Scenario: Estimated time shown
- **WHEN** the route summary dialog is displayed
- **THEN** the estimated travel time SHALL be shown (e.g., "~25 min")

### Requirement: Scrollable step list
The route summary dialog SHALL display a scrollable list of turn-by-turn instructions for the calculated route.

#### Scenario: Steps listed in order
- **WHEN** the route summary dialog is displayed
- **THEN** each `RouteInstruction` entry SHALL be listed in order from start to destination
- **AND** each instruction SHALL show the primary text (e.g., "Left onto Main Street")
- **AND** each instruction SHALL show the distance to the next turn

#### Scenario: Steps scrollable
- **WHEN** the instruction list exceeds the visible area
- **THEN** the step list SHALL be scrollable

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

### Requirement: Dialog dismiss returns to route panel
Dismissing the route summary dialog SHALL return the user to the route panel with the calculated route still visible.

#### Scenario: Dismiss via close button
- **WHEN** user taps the close (X) button on the route summary dialog
- **THEN** the dialog SHALL be dismissed
- **AND** the route panel SHALL remain visible with the calculated route state

#### Scenario: Dismiss via back gesture
- **WHEN** user presses the system back button
- **THEN** the route summary dialog SHALL be dismissed
- **AND** the route panel SHALL re-open with the calculated route state

### Requirement: Active navigation mode with step highlighting
The same route summary dialog SHALL be reusable during active navigation, highlighting the current navigation step.

#### Scenario: Current step highlighted during navigation
- **WHEN** active navigation is in progress
- **AND** the route summary dialog is displayed
- **THEN** the current navigation step SHALL be visually highlighted in the step list
- **AND** the step list SHALL auto-scroll to the current step

#### Scenario: Step highlighting updates on progress
- **WHEN** the user progresses to the next navigation step
- **THEN** the highlighted step SHALL advance to the new current step
- **AND** the step list SHALL auto-scroll to keep the current step visible
