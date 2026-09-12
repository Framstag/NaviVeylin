## MODIFIED Requirements

### Requirement: Search-as-you-type with debounce

The system SHALL perform location search as the user types, with a debounce of no more than 500ms after the user stops typing. An empty query SHALL NOT trigger a search; it SHALL show the empty-query suggestions (mode rows and recent searches, see `auto-search-suggestions`) instead of search results.

#### Scenario: Search results update on input

- **WHEN** user types a query in the search field
- **THEN** search results update automatically after a brief debounce period

#### Scenario: Empty query shows no results

- **WHEN** the search field is empty
- **THEN** no search runs
- **AND** the empty-query suggestions (mode rows and recent searches) SHALL be shown instead of search results
