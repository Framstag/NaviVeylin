# Spec Delta

## ADDED Requirements

### Requirement: Navigation ends when the car session ends after arrival

When the car session ends and the navigation it presented had reached its destination, the system SHALL end that navigation; when the session ends with the destination not reached, navigation SHALL keep running. Reaching the destination while the session is live SHALL NOT end navigation on its own.

#### Scenario: Session ends after arrival — navigation ends

- **WHEN** the car session ends while navigation is active and the destination had been reached
- **THEN** navigation SHALL end: route guidance stops, the host navigation session ends and the ongoing notification is removed
- **AND** the transition SHALL follow the established stop path, so the car behaviours specified for a user-initiated stop (navigation view leaves, a leaveable root remains) hold unchanged

#### Scenario: Session ends without arrival — navigation continues

- **WHEN** the car session ends while navigation is active and the destination had not been reached
- **THEN** navigation SHALL keep running
- **AND** the phone SHALL keep its guidance and its ongoing notification

#### Scenario: Arrival alone does not end navigation

- **WHEN** the destination is reached while the car session is still live
- **THEN** navigation SHALL keep running with its guidance and travel estimate (arrival alone is not a stop)

#### Scenario: Session ends while no navigation is active

- **WHEN** the car session ends and no navigation is active
- **THEN** nothing SHALL change: no error, no navigation state transition

#### Scenario: Exit is confined when the host is already gone

- **WHEN** the car session ends after the host connection is gone and the destination had been reached
- **THEN** navigation SHALL still end, and no exception SHALL escape the session lifecycle callback

#### Scenario: End decision is diagnosable without coordinates

- **WHEN** the session ends and the end decision is taken
- **THEN** a diagnostics line SHALL record the decision (arrival fact, whether navigation was ended, and the remaining distance in metres when it is known)
- **AND** it SHALL carry no coordinates
