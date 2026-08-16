## Purpose

System back gesture/button handling for the map screen: back dismisses the topmost open sheet or dialog instead of exiting the app, and falls through to default system behavior when nothing is open.

## ADDED Requirements

### Requirement: Back gesture closes topmost sheet or dialog
The system SHALL respond to the system back gesture/button by closing the topmost open sheet or dialog on the map screen, never by exiting the application while an overlay is open.

#### Scenario: Back closes favorites sheet
- **WHEN** the favorites management sheet is open
- **AND** user performs the system back gesture (edge swipe) or presses the back button
- **THEN** the favorites sheet SHALL close
- **AND** the map screen SHALL remain visible

#### Scenario: Back closes search panel
- **WHEN** the search panel is open
- **AND** user performs the system back gesture or presses the back button
- **THEN** the search panel SHALL close
- **AND** the map screen SHALL remain visible

#### Scenario: Back closes dialog
- **WHEN** a dialog (e.g., about, route summary, favorite picker, permission rationale) is open
- **AND** user performs the system back gesture or presses the back button
- **THEN** the dialog SHALL dismiss

#### Scenario: Back closes details sheet
- **WHEN** the location details sheet or location options overlay is open
- **AND** user performs the system back gesture or presses the back button
- **THEN** the sheet SHALL dismiss

### Requirement: Back falls through when nothing is open
The system SHALL preserve default system back behavior when no sheet or dialog is open on the map screen.

#### Scenario: Back on base map screen
- **WHEN** the map screen is shown with no sheet, panel, or dialog open
- **AND** user performs the system back gesture or presses the back button
- **THEN** the app SHALL follow default system behavior (e.g., backgrounding the app)

### Requirement: Back rejected while navigation is active
The system SHALL reject the system back gesture/button while navigation (routing) is active, so the app cannot be closed mid-route. The app SHALL only close after the user stops navigation first.

#### Scenario: Back on base map while navigating
- **WHEN** navigation is active
- **AND** no sheet, panel, or dialog is open
- **AND** user performs the system back gesture or presses the back button
- **THEN** the app SHALL NOT close or background
- **AND** the system SHALL inform the user that navigation must be stopped first

#### Scenario: Back closes overlay while navigating
- **WHEN** navigation is active
- **AND** a sheet, panel, or dialog is open
- **AND** user performs the system back gesture or presses the back button
- **THEN** the topmost overlay SHALL dismiss as usual
- **AND** the app SHALL remain open

#### Scenario: Back allowed after navigation stopped
- **WHEN** navigation was active and has been stopped
- **AND** no sheet, panel, or dialog is open
- **AND** user performs the system back gesture or presses the back button
- **THEN** the app SHALL follow default system behavior (e.g., backgrounding the app)

### Requirement: Predictive back animation
The system SHALL support the predictive back animation on Android 13+ (API 33+) so the back gesture shows the system-provided preview animation while dismissing an overlay.

#### Scenario: Predictive back preview on API 33+
- **WHEN** the app runs on Android 13 or newer
- **AND** user starts the back gesture while a sheet or dialog is open
- **THEN** the system SHALL show the predictive back preview animation
- **AND** completing the gesture SHALL dismiss the overlay
