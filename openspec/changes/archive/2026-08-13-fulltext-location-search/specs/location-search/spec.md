## ADDED Requirements

### Requirement: Free-text matches in search suggestions

The search panel SHALL surface free-text matches (e.g., POI or business names) in addition to structured location results, so queries that do not match an address or location name still find named places.

#### Scenario: POI found while typing

- **WHEN** the user types "cafe central" in the search field
- **AND** the debounce interval of 300ms has elapsed
- **THEN** the results list contains the POI "Café Central" found via free-text search

#### Scenario: Suggestions without text index unchanged

- **WHEN** the current map database has no text index
- **AND** the user types a query
- **THEN** the results list contains only structured location results
- **AND** the app continues to function normally
