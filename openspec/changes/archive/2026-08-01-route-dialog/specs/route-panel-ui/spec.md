## MODIFIED Requirements

### Requirement: Turn-by-turn instruction list
After successful route calculation, the route panel SHALL trigger display of the route summary dialog. The route panel SHALL be dismissed when the summary dialog appears, and SHALL re-open with full state when the summary dialog is dismissed. The route panel SHALL NOT show the instruction list inline — instructions are displayed in the route summary dialog instead.

#### Scenario: Route summary dialog triggered after calculation
- **WHEN** route calculation completes successfully
- **THEN** the route summary dialog SHALL appear as a full-screen overlay sliding up from the bottom
- **AND** the route panel SHALL be dismissed
- **AND** the instruction list SHALL NOT be shown inline in the route panel

#### Scenario: Route panel re-opens on summary dismiss
- **WHEN** the route summary dialog is dismissed
- **THEN** the route panel SHALL re-open with all previous state intact (start, destination, vehicle, route)

#### Scenario: Instructions scrollable in summary dialog
- **WHEN** the route summary dialog is displayed
- **THEN** the instruction list SHALL be scrollable within the dialog
