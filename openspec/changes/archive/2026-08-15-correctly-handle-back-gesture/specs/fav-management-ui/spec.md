## MODIFIED Requirements

### Requirement: Full-screen favorites sheet opens from map screen
The system SHALL provide a full-screen sheet (covering the entire screen) for managing favorites. It SHALL be accessible via a "Favorites" button or menu item on the map screen. The sheet SHALL close via the toolbar back button or the system back gesture/button.

#### Scenario: Open favorites sheet
- **WHEN** user taps the "Favorites" button on the map screen
- **THEN** a full-screen sheet SHALL open showing all favorite groups

#### Scenario: Close favorites sheet
- **WHEN** user taps the close/back button
- **THEN** the sheet SHALL close and return to the map

#### Scenario: Back gesture closes favorites sheet
- **WHEN** the favorites sheet is open
- **AND** user performs the system back gesture (edge swipe) or presses the back button
- **THEN** the sheet SHALL close and return to the map
- **AND** the application SHALL NOT exit

## ADDED Requirements

### Requirement: Back gesture from group detail returns to main grid first
The system SHALL treat the group detail sub-screen as a navigation level inside the favorites sheet: the system back gesture/button SHALL first return from the group detail sub-screen to the main group grid, and only close the sheet when already on the main grid.

#### Scenario: Back from group detail sub-screen
- **WHEN** the favorites sheet is open
- **AND** user is viewing a group detail sub-screen
- **AND** user performs the system back gesture or presses the back button
- **THEN** the sheet SHALL return to the main group grid
- **AND** the sheet SHALL remain open

#### Scenario: Back from main grid closes sheet
- **WHEN** the favorites sheet is open
- **AND** user is viewing the main group grid
- **AND** user performs the system back gesture or presses the back button
- **THEN** the sheet SHALL close and return to the map
