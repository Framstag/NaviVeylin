# Spec Delta — about-dialog

## ADDED Requirements

### Requirement: About dialog states what the app does with location and diagnostics
The about dialog SHALL contain a short, plain-language statement of what location and diagnostic data the app handles, so the in-app privacy claim matches the implemented behaviour and the store declaration.

- The statement SHALL say that location is used on-device for navigation and is not transmitted
- The statement SHALL say that the diagnostics log stays on the device, contains no coordinates and is kept for at most 7 days
- The statement SHALL name the map-data licence attribution already present in the dialog rather than duplicating it
- The statement SHALL be a translatable resource (no hardcoded literal) and SHALL exist in German
- The statement SHALL be short enough to read in the dialog without scrolling past it

#### Scenario: Statement is visible in the about dialog

- **WHEN** the user opens the about dialog
- **THEN** the location/diagnostics statement is visible

#### Scenario: Statement states on-device use and the retention window

- **WHEN** the user reads the statement
- **THEN** it names on-device use without transmission
- **AND** it names the 7-day diagnostics retention and that the log contains no coordinates

#### Scenario: Statement is localized

- **WHEN** the device locale is German
- **THEN** the statement is shown in German

#### Scenario: Car about screen keeps the same statement

- **WHEN** the car about screen is shown
- **THEN** it shows the same wording (subject to the existing car-app content constraints for that screen)
