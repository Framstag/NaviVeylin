## ADDED Requirements

### Requirement: System back dismisses topmost overlay
The map canvas screen SHALL respond to the system back gesture/button by dismissing the topmost open overlay (sheet, panel, or dialog) instead of exiting the application.

#### Scenario: Back dismisses search panel
- **WHEN** the search panel overlay is open on the map canvas
- **AND** user performs the system back gesture or presses the back button
- **THEN** the search panel SHALL close
- **AND** the map canvas SHALL remain visible

#### Scenario: Back dismisses favorites sheet
- **WHEN** the favorites sheet is open on the map canvas
- **AND** user performs the system back gesture or presses the back button
- **THEN** the favorites sheet SHALL close
- **AND** the map canvas SHALL remain visible

#### Scenario: Back on base map keeps default behavior
- **WHEN** no overlay is open on the map canvas
- **AND** user performs the system back gesture or presses the back button
- **THEN** the app SHALL follow default system back behavior
