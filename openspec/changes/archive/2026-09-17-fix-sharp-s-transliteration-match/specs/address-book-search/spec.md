## MODIFIED Requirements

### Requirement: Address resolution search
The system SHALL resolve the selected postal address into a map location using the existing offline location search backend (structured and free-text search over the OSM database). Resolution SHALL be precise when the map's location index contains the house number, SHALL fall back to the best available structured result (street or region) when it does not, and SHALL NOT select a free-text result whose label lacks the address's street tokens. Full addresses with the postal code inside the street field (e.g. "Erbstollenstraße 10 58454") SHALL be parsed correctly: the postal code SHALL NOT be treated as the house number. A street field written with `ss` where the map's location index spells the street with the sharp s `ß` (the transliteration Google Contacts applies to `ß` when it syncs an address) SHALL resolve to that street; the resolution SHALL NOT report the address as not found solely because of the `ß`/`ss` spelling difference. The result SHALL be an OSM object with coordinates. When resolution fails entirely, the system SHALL inform the user and SHALL keep the contact list reachable so the user can continue searching without restarting the search.

#### Scenario: Address found
- **WHEN** the user selects a postal address
- **AND** the address is found in the OSM database
- **THEN** the system SHALL produce the resolved OSM object with coordinates

#### Scenario: Sharp-s street with transliterated contact spelling resolves
- **WHEN** the user selects a contact whose street field spells the street with `ss` (e.g. street "10 Erbstollenstrasse", postal code "58454", city "Witten")
- **AND** the map's location index contains that street spelled with `ß` (e.g. "Erbstollenstraße")
- **THEN** the system SHALL produce the resolved street or house-level OSM object with coordinates
- **AND** the system SHALL NOT report the address as not found
- **AND** the system SHALL NOT resolve to an administrative-region or postal-area entry as the final result

#### Scenario: House number missing falls back to street
- **WHEN** the user selects a postal address
- **AND** the house number is not in the OSM location index
- **AND** the street is in the OSM location index
- **THEN** the system SHALL resolve to the street-level result
- **AND** the resolved object SHALL be the street object, not an unrelated free-text object

#### Scenario: Postal code in street field parses correctly
- **WHEN** the user selects a contact whose street field contains the postal code (e.g. "Erbstollenstraße 10 58454")
- **AND** the postal code is also present in the separate postal-code field
- **THEN** the system SHALL resolve the address "Erbstollenstraße 10"
- **AND** SHALL NOT treat "58454" as the house number

#### Scenario: Wrong-location free-text result not selected
- **WHEN** the user selects a postal address
- **AND** no structured result matches the address's street tokens
- **AND** free-text results exist whose labels share tokens with the query (e.g. a bus stop named after the city)
- **THEN** the system SHALL NOT auto-select a free-text result as the resolved location
- **AND** the resolution SHALL report the address as not found

#### Scenario: Address not found
- **WHEN** the user selects a postal address
- **AND** the address is not found in the OSM database
- **THEN** the system SHALL inform the user that no location could be resolved for the address
- **AND** the system SHALL NOT crash or show an error dialog

#### Scenario: Contact list stays reachable after failure
- **WHEN** the user selects a postal address
- **AND** the address is not found in the OSM database
- **THEN** the not-found message SHALL NOT permanently replace the contact list
- **AND** the user SHALL be able to select another contact or address without restarting the search

#### Scenario: Editing the query clears the not-found state
- **WHEN** a resolution has failed and the user edits the search query
- **THEN** the not-found indication SHALL be cleared
- **AND** the filtered contact list SHALL be shown again

#### Scenario: Reopening the search clears the not-found state
- **WHEN** a resolution has failed and the user re-enters the address-book search (or leaves and returns to it)
- **THEN** the contact list SHALL be shown without the not-found indication
