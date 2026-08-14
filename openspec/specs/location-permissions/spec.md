# location-permissions Specification

## Purpose

Manages Android runtime location permissions so the app can access GPS position updates, with proper rationale and graceful degradation when permission is denied.

## Requirements

### Requirement: Runtime permission request

The system SHALL request `ACCESS_FINE_LOCATION` at runtime using Android's standard permission request flow. The request SHALL be triggered when the map screen is first displayed and no location permission has been granted.

#### Scenario: Permission granted on first request

- **WHEN** the map screen is displayed for the first time
- **THEN** the system SHALL show the Android system permission dialog for `ACCESS_FINE_LOCATION`
- **WHEN** the user taps "Allow"
- **THEN** the system SHALL start receiving GPS location updates

#### Scenario: Permission denied

- **WHEN** the user taps "Deny" on the permission dialog
- **THEN** the system SHALL NOT request permission again automatically
- **THEN** the map SHALL display normally without a location marker
- **THEN** the system SHALL NOT crash or show error dialogs

#### Scenario: Permission denied with "Don't ask again"

- **WHEN** the user denies permission with "Don't ask again" checked
- **THEN** the system SHALL show a rationale dialog explaining why location access is needed and directing the user to Settings

### Requirement: Graceful degradation without permission

When location permission is not granted, the system SHALL continue to function normally. The map SHALL render, search SHALL work, favorites SHALL work, and all other features SHALL be unaffected. Only the GPS location marker SHALL be absent.

#### Scenario: All features work without location

- **WHEN** location permission is denied
- **THEN** the map SHALL render at the last known or default viewport
- **THEN** search SHALL return results normally
- **THEN** favorites SHALL load and display normally
- **THEN** no error messages related to location SHALL be shown to the user
