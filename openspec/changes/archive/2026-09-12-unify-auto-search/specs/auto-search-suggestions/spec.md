## Purpose

Lets drivers start any kind of search from the Android Auto search template: an empty query shows mode rows that branch to POI and contacts search plus recent searches, while typing takes over for places search.

## ADDED Requirements

### Requirement: Mode rows on empty query

The Android Auto search template SHALL show mode rows when the search field is empty: a "Search POIs near me" row and a "Search contacts" row. The "Search contacts" row SHALL be shown only while `READ_CONTACTS` is granted. Tapping a mode row SHALL open the corresponding search screen (POI category picker, address-book person search).

#### Scenario: POI mode row shown on empty query

- **WHEN** the search field is empty
- **THEN** a "Search POIs near me" row SHALL be visible
- **AND** tapping it SHALL open the POI category picker

#### Scenario: Contacts mode row shown with permission

- **WHEN** the search field is empty
- **AND** `READ_CONTACTS` is granted
- **THEN** a "Search contacts" row SHALL be visible
- **AND** tapping it SHALL open the address-book person search

#### Scenario: Contacts mode row hidden without permission

- **WHEN** the search field is empty
- **AND** `READ_CONTACTS` is not granted
- **THEN** the "Search contacts" row SHALL NOT be shown

### Requirement: Recent searches on empty query

The Android Auto search template SHALL show recent searches (from the shared search history store, which also records phone searches) as rows when the search field is empty. Tapping a recent-search row SHALL run the search for that query on the current search template.

#### Scenario: History rows shown on empty query

- **WHEN** the search field is empty
- **AND** the search history is not empty
- **THEN** the recent searches SHALL be shown as rows

#### Scenario: History tap runs the search

- **WHEN** the user taps a recent-search row
- **THEN** the search field SHALL be filled with that query
- **AND** the search SHALL run for that query

#### Scenario: No history

- **WHEN** the search field is empty
- **AND** the search history is empty
- **THEN** no history rows SHALL be shown and the mode rows SHALL remain visible

### Requirement: Typing replaces suggestions

Typing in the search field SHALL replace the empty-query suggestions with places search results; clearing the field SHALL restore the suggestions.

#### Scenario: Typing switches to places search

- **WHEN** the user types a query in the search field
- **THEN** the mode rows and history rows SHALL be replaced by places search results

#### Scenario: Clearing restores suggestions

- **WHEN** the user clears the search field
- **THEN** the mode rows and recent searches SHALL be shown again

### Requirement: No-results state keeps mode rows

When a query returns no places results, the search template SHALL show a "No results found" row followed by the mode rows, so the driver can pivot to POI or contacts search without clearing the field.

#### Scenario: Mode rows after no results

- **WHEN** a query returns no places results
- **THEN** a "No results found" row SHALL be shown
- **AND** the mode rows SHALL be shown below it
