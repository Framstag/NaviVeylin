# Spec Delta — i18n-l10n

## MODIFIED Requirements

### Requirement: Locale-aware number formatting
User-facing numeric values (distances, speeds, sizes) SHALL be formatted with the device locale, producing locale-correct decimal separators (e.g. "1,5 km" in German, "1.5 km" in English). No user-facing numeric formatting SHALL use `Locale.ROOT` or a fixed locale.

Coordinate strings are exempt from device-locale formatting: a latitude/longitude string is data, not display text. Every user-facing coordinate string SHALL use one locale-stable format — a `.` decimal separator, 5 decimal places, and the same separator between the two values on both surfaces — so a coordinate pair is never ambiguous when the device locale uses a comma as decimal separator. A coordinate input field SHALL still accept a typed `,` as decimal separator, so a user of a comma-decimal locale is not forced to retype a value in a foreign form.

#### Scenario: German decimal separator
- **WHEN** the device locale is German and a distance of 1.5 km is displayed
- **THEN** the text SHALL read "1,5 km"

#### Scenario: English decimal separator
- **WHEN** the device locale is English and a distance of 1.5 km is displayed
- **THEN** the text SHALL read "1.5 km"

#### Scenario: Coordinate string is locale-stable
- **WHEN** the device locale is German and a coordinate pair (latitude 51.51391, longitude 7.47434) is displayed
- **THEN** the text SHALL read "51.51391, 7.47434"
- **AND** the text SHALL NOT read "51,51391, 7,47434"

#### Scenario: Coordinate input accepts the locale decimal separator
- **WHEN** the device locale is German and the user types "51,51391" into a latitude field
- **THEN** the value SHALL be accepted as the latitude 51.51391
