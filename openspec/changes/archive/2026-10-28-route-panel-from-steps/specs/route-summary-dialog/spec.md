# route-summary-dialog

## MODIFIED Requirements

### Requirement: Scrollable step list

The route summary dialog SHALL display a scrollable list of the calculated route's rows (spec:
`route-analysis` — Rows are derived from the route's steps), in the device language.

#### Scenario: Steps listed in order
- **WHEN** the route summary dialog is displayed
- **THEN** each row SHALL be listed in order from start to destination
- **AND** each row SHALL show its localized primary text (e.g., "Links abbiegen in Hauptstraße")
- **AND** each row SHALL show the distance of the leg that ends at its manoeuvre
- **Case**: `app: RouteSummaryRowsTest` "rows are listed in route order with their own leg distance"

#### Scenario: Steps scrollable
- **WHEN** the instruction list exceeds the visible area
- **THEN** the step list SHALL be scrollable
