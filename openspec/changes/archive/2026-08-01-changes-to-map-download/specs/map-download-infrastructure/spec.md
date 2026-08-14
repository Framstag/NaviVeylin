## ADDED Requirements

### Requirement: Wake lock managed via download lifecycle
The system SHALL integrate wake lock acquisition and release with the map download lifecycle — acquire when the first download starts, release when the last download ends (complete, cancelled, or error).

#### Scenario: Wake lock acquired on first download
- **WHEN** the first map download begins
- **THEN** a wake lock is acquired via Android `PowerManager`

#### Scenario: Wake lock released on last download end
- **WHEN** the last active download finishes, is cancelled, or fails
- **THEN** the wake lock is released

### Requirement: Foreground service for download
The system SHALL start a foreground service with a visible notification during active map downloads to prevent the app from being killed by the Android power management system.

#### Scenario: Foreground service starts with download
- **WHEN** a map download starts
- **THEN** a foreground service is started with a notification showing download progress

#### Scenario: Foreground service stops when downloads end
- **WHEN** all downloads complete, are cancelled, or fail
- **THEN** the foreground service is stopped
