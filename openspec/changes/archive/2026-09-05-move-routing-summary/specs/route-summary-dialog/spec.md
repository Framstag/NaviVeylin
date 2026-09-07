# route-summary-dialog Specification

## MODIFIED Requirements

### Requirement: Route summary dialog shown after calculation
When route calculation completes successfully, the route summary SHALL be shown inline in the route panel, below the calculate button. The route panel SHALL remain open. The full-screen route summary dialog SHALL NOT be shown automatically after calculation; it remains available via the "Show Route" action in the route panel.

#### Scenario: Dialog appears after successful calculation
- **WHEN** route calculation completes successfully
- **THEN** the route summary SHALL be shown inline in the route panel below the calculate button
- **AND** the route panel SHALL remain open

#### Scenario: Dialog not shown on calculation failure
- **WHEN** route calculation fails
- **THEN** the route summary SHALL NOT be shown
- **AND** an error message SHALL be displayed in the route panel

## ADDED Requirements

### Requirement: Route summary dialog via Show Route action
The route panel SHALL provide a "Show Route" action that displays the route summary as a full-screen dialog overlay.

#### Scenario: Show Route opens the dialog
- **WHEN** a route is calculated
- **AND** the user taps "Show Route"
- **THEN** the full-screen route summary dialog SHALL appear
- **AND** the route panel SHALL be dismissed

#### Scenario: Dialog dismiss returns to route panel
- **WHEN** the route summary dialog is dismissed
- **THEN** the route panel SHALL re-open with the calculated route state intact
