## ADDED Requirements

### Requirement: Favorite hits in search results
The AA search template SHALL include favorites matching the query in its results, listed above native results and marked with a heart icon, deduplicated against identical native results (see favorite-search).

#### Scenario: Favorite hit on top
- **WHEN** the user types a query matching a favorite
- **THEN** the favorite SHALL appear above native results, marked with a heart icon

#### Scenario: Identical native result suppressed
- **WHEN** a native result has the same coordinates as a favorite hit
- **THEN** only the favorite hit SHALL be shown
