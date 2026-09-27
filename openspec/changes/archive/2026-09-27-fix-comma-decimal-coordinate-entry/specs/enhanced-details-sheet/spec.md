# Spec Delta — enhanced-details-sheet

## MODIFIED Requirements

### Requirement: Coordinates display
The sheet SHALL always display the latitude and longitude of the selected location, formatted to 5 decimal places using the locale-stable coordinate format (spec: `i18n-l10n` — Coordinate string is locale-stable), so the pair stays unambiguous on a device whose locale uses a comma as decimal separator.

#### Scenario: Coordinates shown in sheet
- **WHEN** the details sheet is open
- **THEN** the coordinates SHALL be displayed as "lat, lon" formatted to 5 decimal places
- **AND** the text SHALL use a subdued color style

#### Scenario: Coordinates stay unambiguous in a comma-decimal locale
- **WHEN** the device locale is German
- **AND** the details sheet is open for latitude 51.51391, longitude 7.47434
- **THEN** the sheet SHALL show "51.51391, 7.47434"
- **AND** the sheet SHALL NOT show "51,51391, 7,47434"
