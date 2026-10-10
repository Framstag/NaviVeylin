# Spec Delta — location-updates-lease

## ADDED Requirements

### Requirement: An invisible, non-navigating driving mode holds no location lease
The system SHALL NOT hold a location lease for a driving mode whose surface is not visible and whose navigation is not running. Such a mode keeps its ongoing notification and process protection without new fixes, and the notification shows the last known road and speed.

#### Scenario: Phone free driving backgrounded releases its lease
- **WHEN** the phone map leaves the foreground while free driving is active and no navigation is running
- **THEN** the app holds no location lease and device location updates stop
- **AND** the diagnostics stream shows the release with the resulting lease count of zero
- **AND** the free-driving notification remains posted

#### Scenario: Returning to the foreground resumes fixes and refreshes the content
- **WHEN** the user returns to the backgrounded free-driving map
- **THEN** the app acquires its lease again and device location updates resume
- **AND** the notification content refreshes with the next fix

#### Scenario: Navigation takes over the fixes while free driving is backgrounded
- **WHEN** navigation starts while free driving is backgrounded
- **THEN** the navigation engine's lease keeps device location updates running
- **AND** the backgrounded free-driving session does not add a second lease

#### Scenario: The car's free-driving session keeps its own lease
- **WHEN** free driving is active on the car and another car app is in the foreground
- **THEN** the live car session keeps its session lease, so its fixes continue and the notification stays live
