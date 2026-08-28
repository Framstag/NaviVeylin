## Purpose

Manages the optional `READ_CONTACTS` permission for address-book person search: a first-use rationale dialog before the request, a remembered decision, and permission state driving feature visibility.

## ADDED Requirements

### Requirement: First-use rationale dialog before permission request

On the first app start, before any permission request, the system SHALL show a dialog explaining why the app wants to read the address book, stating that access is optional and can be denied. The system permission request SHALL only be triggered after the user acknowledges this dialog.

#### Scenario: Rationale shown on first start

- **WHEN** the app starts for the first time after installation
- **THEN** a dialog SHALL appear explaining the address book access is needed for address-book person search
- **AND** the dialog SHALL state that access is optional and can be denied
- **AND** no system permission request SHALL be shown at this point

#### Scenario: Permission request follows rationale acknowledgment

- **WHEN** the user acknowledges the rationale dialog (e.g. taps the continue action)
- **THEN** the system SHALL show the Android runtime permission dialog for `READ_CONTACTS`

#### Scenario: User declines at the rationale dialog

- **WHEN** the user dismisses the rationale dialog without proceeding (e.g. taps the cancel/not-now action)
- **THEN** no system permission request SHALL be shown
- **AND** the address-book menu entry and functionality SHALL remain hidden

### Requirement: Requested permission can be granted or denied

The system SHALL use the standard Android runtime permission flow for `READ_CONTACTS`, so the user can either grant or deny access. Denying SHALL NOT crash the app or degrade any other functionality.

#### Scenario: Permission granted

- **WHEN** the user taps "Allow" on the system permission dialog
- **THEN** the app SHALL be able to read the address book
- **AND** the address-book menu entry and functionality SHALL become available

#### Scenario: Permission denied

- **WHEN** the user taps "Deny" on the system permission dialog
- **THEN** the app SHALL NOT read the address book
- **AND** the address-book menu entry and functionality SHALL remain hidden
- **AND** the app SHALL continue to function normally in all other ways

### Requirement: Decision is remembered

The rationale dialog SHALL be shown exactly once per installation. On subsequent app starts, the system SHALL NOT show the rationale dialog again and SHALL NOT automatically re-trigger the permission request, regardless of whether the user granted or denied access earlier.

#### Scenario: No rationale on later starts

- **WHEN** the app starts again after the rationale dialog was shown once
- **THEN** no rationale dialog SHALL appear
- **AND** no automatic permission request SHALL be triggered

#### Scenario: Denied permission not re-requested automatically

- **WHEN** the user denied `READ_CONTACTS` on a previous start
- **AND** the app starts again
- **THEN** the system SHALL NOT show the permission dialog again automatically
- **AND** the address-book menu entry and functionality SHALL remain hidden

### Requirement: Permission state drives feature visibility

The address-book menu entry and all its functionality SHALL be visible only while `READ_CONTACTS` is granted, and SHALL be hidden while it is denied. When the user changes the permission in system settings, the visibility SHALL update accordingly without a rationale dialog, automatic re-request, or app restart.

#### Scenario: Entry hidden while denied

- **WHEN** `READ_CONTACTS` is denied
- **THEN** the address-book menu entry SHALL NOT be shown in the phone map menu
- **AND** the address-book entry SHALL NOT be shown on the Android Auto car screen

#### Scenario: Entry appears silently after later grant

- **WHEN** the user previously denied `READ_CONTACTS`
- **AND** later grants it in system settings
- **AND** the app regains focus (e.g. user returns from Settings)
- **THEN** the address-book menu entry and functionality SHALL become available
- **AND** no rationale dialog SHALL be shown
- **AND** no permission request SHALL be re-triggered

#### Scenario: Entry disappears after revocation

- **WHEN** `READ_CONTACTS` is granted
- **AND** the user later revokes it in system settings
- **AND** the app regains focus
- **THEN** the address-book menu entry and functionality SHALL become hidden
