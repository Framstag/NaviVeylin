# Map Download UI — Delta

## Purpose

Makes download, delete, and refresh errors visible to the user with an explicit dismiss action, so failures are never silent and the user always knows the outcome of an action.

## ADDED Requirements

### Requirement: Download errors shown with explicit OK
The system SHALL display a download error in the download task UI with the error message and an explicit [OK] action to dismiss it. The error SHALL remain visible until the user dismisses it.

#### Scenario: Download failure shows error task
- **WHEN** a map download fails (network error, server issue, preparation failure, or registration failure)
- **THEN** the download task shows the error message
- **AND** an explicit [OK] action is available to dismiss the error

#### Scenario: OK dismisses error and resets entry
- **WHEN** user taps [OK] on a download error task
- **THEN** the error is dismissed
- **AND** the map entry returns to the available state
- **AND** the user can retry the download

#### Scenario: Error persists until dismissed
- **WHEN** a download error is shown
- **THEN** the error remains visible until the user taps [OK]
- **AND** it is not silently cleared by progress updates or list refreshes

### Requirement: Delete and refresh errors surfaced
The system SHALL surface errors from map deletion and installed-map refresh to the user instead of swallowing them silently.

#### Scenario: Delete failure shown
- **WHEN** deleting an installed map fails
- **THEN** the user sees an error message describing the failure
- **AND** the map entry remains in its current state

#### Scenario: Installed refresh failure shown
- **WHEN** refreshing the installed map list fails
- **THEN** the user sees an error message describing the failure
- **AND** the previously known installed maps remain visible
