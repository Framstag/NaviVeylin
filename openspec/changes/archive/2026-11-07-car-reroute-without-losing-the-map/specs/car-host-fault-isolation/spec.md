# Spec Delta

## ADDED Requirements

### Requirement: A pushed notice dismisses itself within a bounded time

The car session SHALL remove a notice it pushed over another screen after a bounded display time, without requiring a tap: a notice SHALL NOT stay on screen until the driver acts. The removal SHALL target that notice by identity, and a repeated error SHALL update the standing notice and restart its display time rather than stack a second one.

#### Scenario: The notice ends without a tap

- **WHEN** a notice has been pushed and the driver does nothing
- **THEN** the notice SHALL be removed once its display time has elapsed
- **AND** the screen underneath it SHALL be current again
- **Case**: `auto: ErrorNoticeDismissalTest.theNoticeIsRemovedAfterTheDelayAndNothingHappensBefore`

#### Scenario: Repeated errors restart one notice

- **WHEN** errors are raised while a notice is displayed
- **THEN** the standing notice SHALL show the newest wording
- **AND** its display time SHALL run again from that update
- **AND** no second notice SHALL be pushed
- **Case**: `auto: ErrorNoticeDismissalTest.aSupersededNoticeIsNotRemoved`

#### Scenario: A dismissal never outlives its session

- **WHEN** the display time elapses while the car session is not started
- **THEN** no host mutation SHALL be made
- **AND** the removal SHALL be owed to the next started sync
- **Case**: `auto: ErrorNoticeDismissalTest.aStoppedSessionGetsNoHostCallAndOwesTheRemoval`

## MODIFIED Requirements

### Requirement: Host screen-stack mutations are balanced

The system SHALL keep its screen-stack bookkeeping consistent with the stack it actually created: the
car session SHALL pop only screens it pushed, SHALL NOT remove a screen from the stack whose state
the session still relies on, and a transient overlay SHALL pop only itself. Screen-stack bookkeeping
SHALL be updated from the mutation that succeeded, never in advance of it. A notice shown "over" a
screen means above it on the stack: the Car App Library has no overlay layer, so the notice IS the
displayed template until it is removed.

#### Scenario: Error raised while navigating

- **WHEN** the app raises an error notice while navigation is active
- **THEN** the notice is pushed above the navigation view on the stack
- **AND** the navigation view remains on the stack when the notice is dismissed, and guidance stays visible
- **Case**: `auto: SessionScreenStackTest.anErrorNoticeLeavesTheNavigationViewOnTheStack`

#### Scenario: Error notice auto-dismissed

- **WHEN** an error notice is dismissed
- **THEN** only the notice is removed from the stack
- **AND** the screen that was underneath it is unchanged and still current
- **Case**: `auto: SessionScreenStackTest.aDismissalRemovesOnlyTheNotice`

#### Scenario: Repeated errors

- **WHEN** errors are raised repeatedly in one session
- **THEN** the stack does not grow unboundedly and the screen the errors are shown over is unchanged
- **Case**: `auto: SessionScreenStackTest.repeatedErrorsDoNotGrowTheStack`

#### Scenario: Rejected screen push

- **WHEN** a screen push is rejected by the host
- **THEN** the app does not treat that screen as present
- **AND** a later request for the same screen pushes it again
- **Case**: `auto: SessionScreenStackTest.aRejectedPushIsNotRecordedAsPresent`
