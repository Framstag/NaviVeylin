# Spec Delta — fav-management-ui

## ADDED Requirements

### Requirement: Coordinate entry accepts either decimal separator
The add-favorite dialog's latitude and longitude fields SHALL accept a value written with either the `.` or the `,` decimal separator, and the value the dialog prefills SHALL be saveable unchanged. The Save action SHALL only be unavailable while a required field is empty or its text is not a coordinate. A coordinate string displayed in the sheet (group-row subtitle) SHALL use the locale-stable coordinate format (spec: `i18n-l10n` — Coordinate string is locale-stable), so it is unambiguous on a device whose locale uses a comma as decimal separator.

Phone and Android Auto state the same rule: the car details screen's coordinate row SHALL use the same locale-stable format (spec: `auto-destination-details` — Coordinate row uses the locale-stable format); the car has no coordinate entry UI, so only the display rule applies there.

#### Scenario: Prefilled coordinates can be saved unchanged
- **WHEN** the device locale is German
- **AND** the user taps "Add Current Location" and selects a group
- **THEN** the latitude and longitude fields SHALL be prefilled from the current map center
- **AND** the Save action SHALL be enabled once a name is entered, without editing either coordinate field
- **AND** confirming SHALL store the favorite with the map center's coordinates

#### Scenario: Comma decimal separator is accepted
- **WHEN** the user enters the latitude "51,51391" and the longitude "7,47434"
- **AND** a name is entered
- **THEN** the Save action SHALL be enabled
- **AND** confirming SHALL store the favorite at latitude 51.51391, longitude 7.47434

#### Scenario: Dot decimal separator is accepted
- **WHEN** the user enters the latitude "51.51391" and the longitude "7.47434"
- **AND** a name is entered
- **THEN** the Save action SHALL be enabled
- **AND** confirming SHALL store the favorite at latitude 51.51391, longitude 7.47434

#### Scenario: Invalid coordinate text keeps Save unavailable
- **WHEN** the user enters the latitude "51,51,391"
- **THEN** the Save action SHALL be unavailable

#### Scenario: Group row shows an unambiguous coordinate pair
- **WHEN** the device locale is German
- **AND** a group section shows a favorite at latitude 51.5, longitude 7.4
- **THEN** the row SHALL show "51.50000, 7.40000"
