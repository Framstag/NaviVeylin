# Spec Delta

## ADDED Requirements

### Requirement: Free-driving restore is idempotent

When a car session starts while free driving is still active, the system SHALL show exactly one
free-driving view for that session, pushed on top of the map root. A session SHALL NOT push a second
free-driving view, and restoring SHALL NOT require the driver to leave a duplicate view.

#### Scenario: Session restarts during free driving

- **WHEN** the car session is recreated (host restart, re-connect) while free driving was active and the app process survived
- **THEN** the session shows the map root with one free-driving view on top
- **AND** exactly one renderer and one free-driving view exist for the session

#### Scenario: Warmup completes after the first screen

- **WHEN** the session's first screen is created while warmup is still running and free driving is active
- **THEN** the restore happens once, whichever of the two completes last
- **AND** the second restore attempt does not push another view

#### Scenario: Exit after a restore

- **WHEN** the driver exits the free-driving view that was restored, or presses back once
- **THEN** the map root is shown, with no further free-driving view beneath it
