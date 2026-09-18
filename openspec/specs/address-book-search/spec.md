# address-book-search Specification

## Purpose

Lets users find contacts from their device address book that have a postal address and navigate to the resolved map location, reachable from new menu entries on the phone app and on Android Auto.

## Requirements

### Requirement: Address book entry in the phone map menu

The phone map screen SHALL provide address-book person search as the Contacts mode of the unified search dialog. The Contacts mode SHALL be shown only while `READ_CONTACTS` is granted. The map menu SHALL NOT contain a separate "Address book" entry.

#### Scenario: Entry opens person search

- **WHEN** the user opens the unified search dialog
- **AND** `READ_CONTACTS` is granted
- **THEN** the Contacts mode SHALL be visible in the mode switch
- **AND** selecting it SHALL open the address-book person search

#### Scenario: Entry hidden without permission

- **WHEN** `READ_CONTACTS` is not granted
- **THEN** the Contacts mode SHALL NOT be shown in the unified search dialog

#### Scenario: No separate menu entry

- **WHEN** the user opens the map screen menu
- **THEN** no "Address book" entry SHALL be present

### Requirement: Address book entry on the Android Auto car screen

The car screen SHALL provide an "Address book" entry that opens the address-book person search, reachable from the root list and from the search template's empty-query suggestions. The entry SHALL be shown only while `READ_CONTACTS` is granted.

#### Scenario: Entry opens person search on car screen

- **WHEN** the user views the car screen root list
- **AND** `READ_CONTACTS` is granted
- **THEN** an "Address book" entry SHALL be visible
- **AND** selecting it SHALL open the address-book person search

#### Scenario: Entry hidden without permission on car screen

- **WHEN** `READ_CONTACTS` is not granted
- **THEN** the "Address book" entry SHALL NOT be shown on the car screen

#### Scenario: Entry opens person search from search template

- **WHEN** the user views the search template with an empty query
- **AND** `READ_CONTACTS` is granted
- **THEN** a "Search contacts" row SHALL be visible
- **AND** selecting it SHALL open the address-book person search

### Requirement: Searchable list of persons with addresses

The address-book person search SHALL display a list of contacts that have at least one postal address. The list SHALL be filterable by typing part of the person's name (case-insensitive); clearing the query SHALL show all contacts with addresses again. Contacts without any postal address SHALL NOT appear in the list.

#### Scenario: List shows only contacts with addresses

- **WHEN** the user opens the address-book person search
- **THEN** the list SHALL contain only contacts that have at least one postal address

#### Scenario: Filter by name

- **WHEN** the user types a name fragment in the search field
- **THEN** the list SHALL be filtered to contacts whose name contains the typed text (case-insensitive)

#### Scenario: Clearing the query restores the list

- **WHEN** the user clears the search field
- **THEN** all contacts with addresses SHALL be shown again

#### Scenario: No contacts with addresses

- **WHEN** the address book contains no contact with a postal address
- **THEN** the search SHALL show an appropriate empty state message

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

### Requirement: Details view for the resolved object

The resolved OSM object SHALL be shown in the same details view used for search results (full-screen details with the object name, an interactive mini map of the surroundings, the structured description, and the standard actions such as showing the object on the map, adding it to favorites, and starting navigation).

#### Scenario: Details shown after resolution

- **WHEN** the address resolution succeeds
- **THEN** the details view SHALL open for the resolved object
- **AND** it SHALL show the object name, mini map, structured description, and the standard action buttons

#### Scenario: Show on map from details

- **WHEN** the user selects "Show on map" in the details view of a resolved address-book object
- **THEN** the details view SHALL close
- **AND** the map SHALL center on the resolved object
