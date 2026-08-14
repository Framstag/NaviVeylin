## ADDED Requirements

### Requirement: Free-text results without garbage entries
Free-text search results SHALL NOT contain empty or invalid entries. Entries whose text index reference cannot be resolved to a real object, entries whose object resolves to invalid (0,0) coordinates, and entries introduced by result-list padding SHALL be omitted from the result list.

#### Scenario: Unresolvable index entry omitted
- **WHEN** the free-text search returns an entry whose object reference cannot be resolved (e.g. a stale or mismatched text index key)
- **THEN** the entry SHALL be omitted from the results
- **AND** the remaining valid results SHALL be returned normally

#### Scenario: Zero-coordinate entry omitted
- **WHEN** a free-text search entry resolves to an object with (0,0) coordinates
- **THEN** the entry SHALL be omitted from the results

#### Scenario: Invalid string data entry omitted
- **WHEN** a search entry carries string data that is not valid UTF-8 (e.g. garbage bytes from a corrupt text index or database entry)
- **THEN** the entry SHALL be omitted from the results
- **AND** the search SHALL complete without crashing

#### Scenario: No padding entries below limit
- **WHEN** the number of valid free-text results is smaller than the remaining space in the result list
- **THEN** the result list SHALL NOT be padded with empty entries
- **AND** the result list SHALL contain at most the requested limit of entries, all of which have a non-empty label and valid coordinates
