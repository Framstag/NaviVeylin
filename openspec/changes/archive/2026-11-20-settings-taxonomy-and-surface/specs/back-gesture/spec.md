# Spec Delta — back-gesture

## MODIFIED Requirements

### Requirement: Back gesture closes topmost sheet or dialog
The system SHALL respond to the system back gesture/button by closing the topmost open sheet or dialog on the map screen, never by exiting the application while an overlay is open.

#### Scenario: Back closes favorites sheet
- **WHEN** the favorites management sheet is open
- **AND** user performs the system back gesture (edge swipe) or presses the back button
- **THEN** the favorites sheet SHALL close
- **AND** the map screen SHALL remain visible
- **Case**: none — the surface's back cases are written in tasks.md group 2

#### Scenario: Back closes search panel
- **WHEN** the search panel is open
- **AND** user performs the system back gesture or presses the back button
- **THEN** the search panel SHALL close
- **AND** the map screen SHALL remain visible
- **Case**: none — the surface's back cases are written in tasks.md group 2

#### Scenario: Back closes dialog
- **WHEN** a dialog (e.g., about, route summary, favorite picker, permission rationale) is open
- **AND** user performs the system back gesture or presses the back button
- **THEN** the dialog SHALL dismiss
- **Case**: none — the surface's back cases are written in tasks.md group 2

#### Scenario: Back closes details sheet
- **WHEN** the location details sheet or location options overlay is open
- **AND** user performs the system back gesture or presses the back button
- **THEN** the sheet SHALL dismiss
- **Case**: none — the surface's back cases are written in tasks.md group 2

#### Scenario: Back closes the settings surface one level at a time
- **WHEN** the settings surface shows a topic page
- **AND** user performs the system back gesture or presses the back button
- **THEN** the settings surface SHALL show its root
- **WHEN** user performs the gesture again on the root
- **THEN** the settings surface SHALL close
- **AND** the map screen SHALL remain visible
- **Case**: none — the surface's back cases are written in tasks.md group 2
