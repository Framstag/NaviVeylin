# address-book-search Delta

## MODIFIED Requirements

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
