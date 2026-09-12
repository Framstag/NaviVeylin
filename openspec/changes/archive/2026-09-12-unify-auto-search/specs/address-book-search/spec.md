## MODIFIED Requirements

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
