# navigation-ongoing-notification Specification

## Purpose
TBD - created by archiving change fix-host-crash-residual-paths. Update Purpose after archive.

## Requirements

### Requirement: Distinct notification identity

The system SHALL post the ongoing navigation notification and the car map-style failure notice under
distinct notification identities, taken from one shared source. A map-style failure notice SHALL NOT
replace, cancel or take over the identity of the ongoing notification — that notification is also the
foreground-service notification and the carrier of the car rail-widget turn hint.

#### Scenario: Style notice while navigating

- **WHEN** a map-style failure notice is posted while the ongoing navigation notification is showing
- **THEN** both notifications exist under their own identities
- **AND** the foreground-service notification and the car rail-widget turn hint are unaffected

#### Scenario: Navigation updates after a style notice

- **WHEN** navigation content changes after a map-style failure notice was posted
- **THEN** the ongoing notification is updated on its own identity and the style notice is not withdrawn by that update

#### Scenario: Identities stay unique

- **WHEN** the notification identities of the app are compared
- **THEN** the ongoing navigation notification, the map-download notification and the map-style failure notice each have a distinct identity
