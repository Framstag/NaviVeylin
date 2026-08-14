## ADDED Requirements

### Requirement: Search resilient to inconsistent map data
The system SHALL NOT crash when a map's search index references objects that cannot be read from the map data (e.g., stale or mismatched index entries after a partial download or a dataset/version update). Entries whose object cannot be resolved SHALL be omitted from the results, the remaining entries SHALL be returned normally, and the app SHALL continue running.

#### Scenario: Unreadable result entry is skipped
- **WHEN** a search query returns an entry whose object cannot be read from the map database
- **THEN** the entry SHALL be omitted from the results
- **AND** the remaining results SHALL be returned normally

#### Scenario: No crash on corrupt index data
- **WHEN** a search query runs against a map whose search index references an object type outside the database's type configuration
- **THEN** the app SHALL NOT terminate or crash
- **AND** the search SHALL complete with whatever valid results are available

### Requirement: Convenience entries on empty query
When the search field is empty, the search panel SHALL show "Current Location" (if GPS is available) and "Select Favorite" entries above the results area. Typing a query SHALL hide both entries and show location search results only; clearing the field SHALL restore both entries immediately.

#### Scenario: Empty query shows convenience entries
- **WHEN** the search panel opens with an empty query
- **THEN** a "Current Location" entry SHALL be visible (if GPS is available)
- **AND** a "Select Favorite" entry SHALL be visible

#### Scenario: Typing hides convenience entries
- **WHEN** the user types a query
- **THEN** the "Current Location" and "Select Favorite" entries SHALL be hidden
- **AND** only location search results SHALL be listed

#### Scenario: Clearing restores convenience entries
- **WHEN** the user clears the query
- **THEN** the "Current Location" and "Select Favorite" entries SHALL reappear immediately

#### Scenario: Current location entry hidden without GPS
- **WHEN** GPS location is not available
- **THEN** the "Current Location" entry SHALL be hidden
- **AND** the "Select Favorite" entry SHALL remain visible

### Requirement: Follow mode deactivated on search result selection
When the user selects a search result while follow-location mode is active, the system SHALL deactivate follow mode before centering the map, so the selected location stays visible and is not overridden by subsequent GPS position updates.

#### Scenario: Follow mode off when result selected
- **WHEN** follow mode is active
- **AND** user selects a search result
- **THEN** follow mode SHALL be deactivated
- **AND** the map SHALL center on the selected location
- **AND** subsequent GPS position updates SHALL NOT re-center the map on the current position
