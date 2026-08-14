## Purpose

Provides users with app identity information including version, description, and open-source licenses in a standard Android dialog.

## ADDED Requirements

### Requirement: About dialog displays app name and version
The about dialog SHALL display the application name "NaviVeylin" and the current version name (e.g., "1.0.0") sourced from `BuildConfig.VERSION_NAME`.

#### Scenario: Dialog shows correct app name and version
- **WHEN** the about dialog is open
- **THEN** the dialog SHALL show "NaviVeylin" as the app name
- **AND** the dialog SHALL show the version string from `BuildConfig.VERSION_NAME`

### Requirement: About dialog displays app description
The about dialog SHALL display a brief description of the application explaining it is an Android navigation app built on libosmscout.

#### Scenario: Dialog shows description text
- **WHEN** the about dialog is open
- **THEN** the dialog SHALL show descriptive text about the application

### Requirement: About dialog provides link to project source and licenses
The about dialog SHALL provide a way for users to access the project's source code and license information via a link to the project repository.

#### Scenario: Licenses link is visible
- **WHEN** the about dialog is open
- **THEN** the dialog SHALL show a link to the project repository

#### Scenario: Licenses link opens repository in browser
- **WHEN** the user taps the repository link
- **THEN** the system SHALL open the project repository URL in the device browser

### Requirement: About dialog is reachable from map screen menu
The about dialog SHALL be accessible from the overflow menu (⋮) on the map canvas screen.

#### Scenario: Menu item opens about dialog
- **GIVEN** the map canvas screen is displayed
- **WHEN** the user taps the overflow menu (⋮)
- **THEN** the menu SHALL show an "About" item
- **WHEN** the user selects "About"
- **THEN** the about dialog SHALL open

### Requirement: About dialog can be dismissed
The about dialog SHALL have a dismiss action so the user can return to the map.

#### Scenario: Dialog dismisses on close button
- **WHEN** the about dialog is open
- **WHEN** the user taps the close/dismiss button
- **THEN** the dialog SHALL close

#### Scenario: Dialog dismisses on back press
- **WHEN** the about dialog is open
- **WHEN** the user presses the system back button
- **THEN** the dialog SHALL close
