# address-book-search Delta Spec

## MODIFIED Requirements

### Requirement: Address resolution search

The system SHALL resolve the selected postal address into a map location using the existing offline location search backend (structured and free-text search over the OSM database). The result SHALL be an OSM object with coordinates. When resolution fails, the system SHALL inform the user and SHALL keep the contact list reachable so the user can continue searching without restarting the search.

#### Scenario: Address found

- **WHEN** the user selects a postal address
- **AND** the address is found in the OSM database
- **THEN** the system SHALL produce the resolved OSM object with coordinates

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
