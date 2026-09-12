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

The car screen root list SHALL contain an "Address book" entry that opens the address-book person search. The entry SHALL be shown only while `READ_CONTACTS` is granted.

#### Scenario: Entry opens person search on car screen

- **WHEN** the user views the car screen root list
- **AND** `READ_CONTACTS` is granted
- **THEN** an "Address book" entry SHALL be visible
- **AND** selecting it SHALL open the address-book person search

#### Scenario: Entry hidden without permission on car screen

- **WHEN** `READ_CONTACTS` is not granted
- **THEN** the "Address book" entry SHALL NOT be shown on the car screen

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

When the selected contact has more than one postal address, the system SHALL let the user choose which address to resolve before performing the address resolution.

#### Scenario: Multiple addresses offered

- **WHEN** the user selects a contact with more than one postal address
- **THEN** the system SHALL show the contact's postal addresses
- **AND** address resolution SHALL only start after the user picks one

#### Scenario: Single address resolves directly

- **WHEN** the user selects a contact with exactly one postal address
- **THEN** the system SHALL resolve that address without an extra selection step

### Requirement: Address resolution search

The system SHALL resolve the selected postal address into a map location using the existing offline location search backend (structured and free-text search over the OSM database). The result SHALL be an OSM object with coordinates.

#### Scenario: Address found

- **WHEN** the user selects a postal address
- **AND** the address is found in the OSM database
- **THEN** the system SHALL produce the resolved OSM object with coordinates

#### Scenario: Address not found

- **WHEN** the user selects a postal address
- **AND** the address is not found in the OSM database
- **THEN** the system SHALL inform the user that no location could be resolved for the address
- **AND** the system SHALL NOT crash or show an error dialog

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
