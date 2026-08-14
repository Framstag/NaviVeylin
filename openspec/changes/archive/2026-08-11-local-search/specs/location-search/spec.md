## ADDED Requirements

### Requirement: Search scoped by current admin region
When a usable GPS fix is available, the map screen search panel SHALL resolve the admin region containing the current position and pass it as the default admin region to the native search call. Addresses and POIs SHALL then match when the user omits the region qualifier. The search SHALL still match fully qualified queries regardless of the default region. Without a usable GPS fix (no fix, stale fix, or poor accuracy), the search SHALL run unconstrained, exactly as before this change. Search initiated from the route panel SHALL remain unconstrained.

#### Scenario: Incomplete address matches with GPS fix
- **WHEN** the user has a usable GPS fix inside a known admin region
- **AND** the user types an address or POI name without the region qualifier (e.g. "Hauptstraße 12" while located in Dortmund)
- **THEN** the search results SHALL include matches from the current admin region

#### Scenario: Fully qualified query still matches
- **WHEN** the user types a fully qualified query (e.g. "Hauptstraße 12 Dortmund")
- **THEN** the results SHALL match as before, independent of the current admin region

#### Scenario: No GPS fix falls back to unconstrained search
- **WHEN** the user has no GPS fix, a stale fix (older than the freshness threshold), or a fix with accuracy worse than the accuracy threshold
- **THEN** the search SHALL run without a default admin region
- **AND** the results SHALL be identical to the pre-change behavior

#### Scenario: Default region is fallback only
- **WHEN** the query contains an explicit admin region that differs from the current position's region
- **THEN** the explicit region in the query SHALL take precedence over the default region

### Requirement: Admin region follows user movement
The resolved admin region used for search SHALL track the user's position. Once resolved, the region SHALL be reused for subsequent queries without re-resolution. The system SHALL re-resolve when the position has moved beyond a movement threshold since the last resolution.

#### Scenario: Region re-resolved after significant movement
- **WHEN** the user has moved more than the movement threshold since the last admin region resolution
- **AND** a new search query is submitted
- **THEN** the system SHALL resolve the admin region at the new position and use it for that query

#### Scenario: No re-resolution on minor movement
- **WHEN** the user's position changes by less than the movement threshold since the last resolution
- **AND** a new search query is submitted
- **THEN** the system SHALL reuse the previously resolved admin region without re-resolving

#### Scenario: Stable region during a typing session
- **WHEN** the user is typing a query and results are refreshing per keystroke
- **THEN** the admin region SHALL remain constant for all queries of that session unless the position moved beyond the movement threshold

### Requirement: Resolved region name shown in search panel
When an admin region has been resolved for the current GPS position, the search panel SHALL display the region's name above the search input. The displayed name SHALL follow the currently resolved region: it SHALL appear when resolution succeeds, update when the region is re-resolved after movement, and disappear when no usable GPS fix exists or resolution fails.

#### Scenario: Region name shown above search field
- **WHEN** an admin region is resolved for the current position
- **AND** the search panel is open
- **THEN** the region's name SHALL be displayed above the search input field

#### Scenario: No name without resolved region
- **WHEN** no usable GPS fix exists or region resolution failed
- **AND** the search panel is open
- **THEN** no region name SHALL be displayed above the search input field

#### Scenario: Name follows re-resolution
- **WHEN** the user moves beyond the movement threshold
- **AND** a new admin region is resolved
- **THEN** the displayed name SHALL update to the newly resolved region's name
