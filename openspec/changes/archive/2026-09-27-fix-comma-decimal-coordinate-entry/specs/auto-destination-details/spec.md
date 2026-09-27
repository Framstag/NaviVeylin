# Spec Delta — auto-destination-details

## ADDED Requirements

### Requirement: Coordinate row uses the locale-stable format
The details screen's coordinate row SHALL use the same locale-stable coordinate format as the navigation destination text (`NavigationTemplateMapper.coordinatesText`), i.e. a `.` decimal separator with 5 decimal places (spec: `i18n-l10n` — Coordinate string is locale-stable), and SHALL NOT derive its separators from the device locale. Phone parity: the phone details sheet and the favorites sheet use the identical coordinate string rule (spec: `enhanced-details-sheet` — Coordinates display; spec: `fav-management-ui` — Coordinate entry accepts either decimal separator).

#### Scenario: Car coordinates row is locale-stable
- **WHEN** the device locale is German
- **AND** the details screen is open for latitude 51.51391, longitude 7.47434
- **THEN** the coordinates row SHALL show "51.51391, 7.47434"
- **AND** the row SHALL NOT show "51,51391, 7,47434"

#### Scenario: Destination text and coordinates row agree
- **WHEN** the same destination is shown on the details screen and then on the navigation template
- **THEN** both coordinate strings SHALL be formatted identically
