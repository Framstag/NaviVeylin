# address-book-search delta

## MODIFIED Requirements

### Requirement: Address resolution search
The system SHALL resolve the selected postal address into a map location using the existing offline location search backend (structured form search and free-text string search over the OSM database). The resolution SHALL attempt the address's candidate queries in a defined order — structured form search first, then progressively looser string queries — and SHALL continue to the next candidate whenever the current candidate yields no result with street evidence. A result set whose entries all lack street evidence (for example an administrative-region or postal-area fallback entry returned for a partially matching query) SHALL be treated as a miss, not as a successful resolution. The resolution SHALL only report the address as not found after every candidate has been tried and none produced a street-evidenced result. Resolution SHALL be precise when the map's location index contains the house number and SHALL fall back to the best available structured street result when it does not. The system SHALL NOT select a free-text result whose label lacks the address's street tokens. A street field written with `ss` where the map's location index spells the street with the sharp s `ß` (the transliteration Google Contacts applies to `ß` when it syncs an address) SHALL resolve to that street; the resolution SHALL NOT report the address as not found solely because of the `ß`/`ss` spelling difference.

Address components SHALL be derived from the contact's structured postal fields (`STREET`, `POSTCODE`, `CITY`, `REGION`) and, when those do not supply a usable street or city, from `ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS`. A contact whose structured components are empty or incomplete SHALL still be resolvable from its formatted address. Full addresses with the postal code inside the street field (e.g. "Erbstollenstraße 10 58454") SHALL be parsed correctly: the postal code SHALL NOT be treated as the house number. When resolution fails entirely, the system SHALL inform the user and SHALL keep the contact list reachable so the user can continue searching without restarting the search.

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

#### Scenario: Street-less result does not abort the candidate chain
- **WHEN** the user selects a postal address
- **AND** a candidate search returns only entries without street evidence (administrative-region or postal-area fallback)
- **THEN** the system SHALL treat that candidate as a miss
- **AND** SHALL continue with the remaining candidate queries
- **AND** SHALL resolve the address when a later candidate yields a street-evidenced result

#### Scenario: Not found requires the whole chain to fail
- **WHEN** the user selects a postal address
- **AND** every candidate query returns either no results or only entries without street evidence
- **THEN** the system SHALL report the address as not found

#### Scenario: Resolution from formatted address only
- **WHEN** the user selects a contact whose structured components are empty
- **AND** the contact carries a formatted postal address
- **THEN** the system SHALL derive the address components from the formatted address
- **AND** SHALL resolve the address as if those components had been stored separately

#### Scenario: Formatted address completes partial components
- **WHEN** the user selects a contact whose street or city component is missing
- **AND** the contact carries a formatted postal address containing that component
- **THEN** the system SHALL use the formatted address to complete the missing component

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

#### Scenario: Search box and contact resolution agree
- **WHEN** an address resolves to a map object through the unified search dialog's place search
- **AND** the same address is stored on a contact
- **THEN** selecting that contact SHALL resolve to an object of the same kind (house, street, or place) at the same location
- **AND** SHALL NOT report the address as not found

#### Scenario: Auto parity for resolution
- **WHEN** the same contact address is resolved from the Android Auto address book screen
- **THEN** the resolution outcome SHALL be identical to the phone outcome

### Requirement: Selecting a person with multiple addresses asks for the address
When the selected contact has more than one distinct postal address, the system SHALL let the user choose which address to resolve before performing the address resolution. Postal addresses that are identical after normalization (trimmed components compared case-insensitively; the formatted address compared when components are absent) SHALL be presented as a single address. The address selection list SHALL render each distinct address exactly once and SHALL NOT fail on duplicate entries.

#### Scenario: Multiple addresses offered
- **WHEN** the user selects a contact with more than one distinct postal address
- **THEN** the system SHALL show the contact's postal addresses
- **AND** address resolution SHALL only start after the user picks one

#### Scenario: Single address resolves directly
- **WHEN** the user selects a contact with exactly one postal address
- **THEN** the system SHALL resolve that address without an extra selection step

#### Scenario: Identical addresses from two accounts collapse
- **WHEN** a contact holds the same postal address twice (e.g. the person exists in two synchronized address books)
- **THEN** the person's address SHALL be presented as a single address
- **AND** resolving it SHALL NOT require an extra selection step
- **AND** the contact list row SHALL show that address once

#### Scenario: Distinct addresses still deduplicated correctly
- **WHEN** a contact holds two different postal addresses
- **THEN** both SHALL remain selectable
- **AND** the list of addresses SHALL NOT crash
