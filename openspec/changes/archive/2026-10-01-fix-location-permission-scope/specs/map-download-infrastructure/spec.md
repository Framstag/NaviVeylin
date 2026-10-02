# Spec Delta — map-download-infrastructure

## MODIFIED Requirements

### Requirement: Foreground service for download
The system SHALL start a foreground service with a visible notification during active map downloads to prevent the app from being killed by the Android power management system, and SHALL handle the platform ending that service.

- The service SHALL be started from the download the user initiated and SHALL stop when no download is active (complete, cancelled or failed)
- The service SHALL implement the platform's foreground-service timeout notification and end cleanly when the platform ends it
- A platform-ended service SHALL NOT be reported as a completed download: the affected downloads SHALL remain resumable and their state SHALL be readable after the service ends
- Any wake lock held by the service SHALL be released when the service ends, whether the app or the platform ended it

#### Scenario: Foreground service starts with download
- **WHEN** a map download starts
- **THEN** a foreground service is started with a notification showing download progress

#### Scenario: Foreground service stops when downloads end
- **WHEN** all downloads complete, are cancelled, or fail
- **THEN** the foreground service is stopped

#### Scenario: Platform timeout ends the service cleanly
- **WHEN** the platform ends the foreground service because its type's runtime limit was reached
- **THEN** the service SHALL stop itself and release its resources without an error dialog
- **AND** the affected download SHALL be reported as not completed and SHALL remain resumable

#### Scenario: Wake lock does not outlive the service
- **WHEN** the service ends (by the app or by the platform)
- **THEN** no wake lock from the download service remains held
