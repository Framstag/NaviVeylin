## ADDED Requirements

### Requirement: Transliterated name matching parity with phone search

The car search template SHALL match transliterated name spellings exactly as the phone search does, because both use the same location search backend. No platform constraint forces a deviation: there SHALL be no car-specific difference in which names a query matches. A query spelling a sharp-s street with `ss` SHALL return the same results in the car template as on the phone, with the same labels and region hierarchy.

#### Scenario: ss-spelled query finds sharp-s street in the car

- **WHEN** the driver types a street name spelled with `ss` (e.g. "Erbstollenstrasse") in the car search template
- **AND** the map's location index contains that street spelled with `ß` (e.g. "Erbstollenstraße")
- **THEN** the car results list SHALL include that street
- **AND** the result SHALL show the same label and region hierarchy as the phone search shows for the same query

#### Scenario: Car and phone result sets agree

- **WHEN** the same transliterated query is issued on the phone and in the car search template against the same map database
- **THEN** the matching entries SHALL be the same on both screens
- **AND** no car-specific result filtering SHALL remove transliterated matches

#### Scenario: Fully qualified address in the car resolves

- **WHEN** the driver types a full formatted address whose street is spelled with `ss` (e.g. "Erbstollenstrasse 10, 58454 Witten") in the car search template
- **THEN** the structured search SHALL resolve it as on the phone
- **AND** the results SHALL include the matching street or house-level entry
