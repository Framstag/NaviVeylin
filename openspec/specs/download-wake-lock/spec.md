# Download Wake Lock

## Purpose

Keep screen on and prevent app hibernation or sleep during active map downloads so large maps finish without interruption.

## Requirements

### Requirement: Wake lock acquired during download
The system SHALL acquire a partial wake lock and keep the screen on while any map download is in progress. The wake lock SHALL be released when all downloads complete, are cancelled, or fail.

#### Scenario: Screen stays on during download
- **WHEN** a map download starts
- **THEN** the system acquires a wake lock
- **AND** the screen does not turn off until the download completes

#### Scenario: Wake lock released on download complete
- **WHEN** all active downloads finish successfully
- **THEN** the wake lock is released
- **AND** normal screen timeout behavior resumes

#### Scenario: Wake lock released on cancel
- **WHEN** user cancels the last active download
- **THEN** the wake lock is released

#### Scenario: Wake lock released on error
- **WHEN** the last active download fails
- **THEN** the wake lock is released

### Requirement: App hibernation prevention
The system SHALL use a foreground service notification or similar mechanism to prevent the app from being hibernated or killed during active map downloads on Android 12+.

#### Scenario: Foreground notification shown during download
- **WHEN** a map download starts
- **THEN** a foreground service notification is shown indicating an active download
- **AND** the app is not hibernated by the system

#### Scenario: Notification removed on download end
- **WHEN** all downloads complete, are cancelled, or fail
- **THEN** the foreground notification is removed
