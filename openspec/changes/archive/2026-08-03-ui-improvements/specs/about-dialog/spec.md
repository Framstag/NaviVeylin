## ADDED Requirements

### Requirement: About dialog displays author name

The about dialog SHALL display the author name "Tim Teulings" to credit the original libosmscout author.

#### Scenario: Dialog shows author name

- **WHEN** the about dialog is open
- **THEN** the dialog SHALL show "Tim Teulings" as the author

### Requirement: About dialog displays copyright year

The about dialog SHALL display "Copyright 2026" to indicate the copyright year.

#### Scenario: Dialog shows copyright

- **WHEN** the about dialog is open
- **THEN** the dialog SHALL show "Copyright 2026"

## MODIFIED Requirements

### Requirement: About dialog is reachable from map screen menu

The about dialog SHALL be accessible from the overflow menu (⋮) on the map canvas screen AND from the main screen menu.

#### Scenario: Menu item opens about dialog
- **GIVEN** the map canvas screen is displayed
- **WHEN** the user taps the overflow menu (⋮)
- **THEN** the menu SHALL show an "About" item
- **WHEN** the user selects "About"
- **THEN** the about dialog SHALL open

#### Scenario: Menu item opens about dialog from main screen
- **GIVEN** the main screen is displayed
- **WHEN** the user taps the menu
- **THEN** the menu SHALL show an "About" item
- **WHEN** the user selects "About"
- **THEN** the about dialog SHALL open
