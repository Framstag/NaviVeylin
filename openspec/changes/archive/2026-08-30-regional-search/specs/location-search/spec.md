## MODIFIED Requirements

### Requirement: Search scoped by current admin region
When a usable GPS fix is available, the map screen search panel SHALL resolve the admin region containing the current position and pass it as the default admin region to the native search call. The search scope SHALL be the highest ancestor of the resolved region at or finer than the maximum region level cap (walking up the parent chain while each parent is at or finer than the cap), so addresses and POIs match when the user omits the region qualifier even if they lie in a neighboring subregion of the same scope (the scope's recursive search covers all its subregions in one pass). When the resolved region has no parent, or every ancestor is coarser than the cap, the scope SHALL be the resolved region alone. The cap SHALL default to level 5 (Regierungsbezirk/district on the OSM admin_level scale: 2=country, 4=state, 5=Regierungsbezirk, 6=county, 8=municipality), so a kreisfreie Stadt (level 6) and its surrounding towns share a scope. The search SHALL still match fully qualified queries regardless of the default region. Without a usable GPS fix (no fix or poor accuracy), the search SHALL run unconstrained, exactly as before this change. The last known position SHALL remain valid for region scoping regardless of fix age; the region SHALL be re-resolved when a fresh fix shows movement beyond the movement threshold. Search initiated from the route panel SHALL remain unconstrained.

#### Scenario: Incomplete address matches with GPS fix
- **WHEN** the user has a usable GPS fix inside a known admin region
- **AND** the user types an address or POI name without the region qualifier (e.g. "Hauptstraße 12" while located in Dortmund)
- **THEN** the search results SHALL include matches from the current admin region

#### Scenario: Match in subregion of expanded scope
- **WHEN** the user has a usable GPS fix inside a known admin region
- **AND** the highest ancestor at or finer than the level cap exists
- **AND** the user types an address or POI name without the region qualifier
- **AND** a matching object exists in a subregion of the scope other than the current region (e.g. a neighboring town under the same county)
- **THEN** the search results SHALL include the match from that subregion

#### Scenario: Kreisfreie Stadt finds neighboring town
- **WHEN** the user has a usable GPS fix in a Stadtteil of a kreisfreie Stadt (e.g. Eving in Dortmund, level 6, whose parent is a Regierungsbezirk at level 5)
- **AND** the user types an address or POI name without the region qualifier
- **AND** a matching object exists in a town under a neighboring county of the same Regierungsbezirk (e.g. Bergkamen under Kreis Unna)
- **THEN** the search scope SHALL be the Regierungsbezirk (the highest ancestor at or finer than the cap)
- **AND** the search results SHALL include the match from that town

#### Scenario: Expansion bounded by region level cap
- **WHEN** every ancestor of the current admin region is coarser than the maximum region level cap (e.g. the parent is a state at level 4 or a country at level 2)
- **AND** the user types an address or POI name without the region qualifier
- **THEN** the search scope SHALL be limited to the current admin region
- **AND** matches from regions beyond the cap SHALL NOT be included

#### Scenario: Current region always in scope
- **WHEN** the search scope is expanded to the parent region
- **THEN** matches from the current admin region SHALL remain included

#### Scenario: Fully qualified query still matches
- **WHEN** the user types a fully qualified query (e.g. "Hauptstraße 12 Dortmund")
- **THEN** the results SHALL match as before, independent of the current admin region

#### Scenario: No GPS fix falls back to unconstrained search
- **WHEN** the user has no GPS fix or a fix with accuracy worse than the accuracy threshold
- **THEN** the search SHALL run without a default admin region
- **AND** the results SHALL be identical to the pre-change behavior

#### Scenario: Old fix still scopes the search
- **WHEN** the user has a fix with acceptable accuracy that is old (e.g. at home all day, last fix hours ago)
- **AND** the user types an address or POI name without the region qualifier
- **THEN** the search SHALL be scoped to the region containing the last known position
- **AND** the region SHALL be re-resolved when a fresh fix shows movement beyond the movement threshold

#### Scenario: Default region is fallback only
- **WHEN** the query contains an explicit admin region that differs from the current position's region
- **THEN** the explicit region in the query SHALL take precedence over the default region

### Requirement: Search scope region name shown in search panel
When an admin region has been resolved for the current GPS position, the search panel SHALL display the name of the search scope region above the search input: the parent region when the scope is expanded, else the resolved region itself. The displayed name SHALL follow the currently resolved region: it SHALL appear when resolution succeeds, update when the region is re-resolved after movement, and disappear when no usable GPS fix exists or resolution fails.

#### Scenario: Scope region name shown above search field
- **WHEN** an admin region is resolved for the current position
- **AND** the search scope is expanded to the parent region
- **THEN** the parent region's name SHALL be displayed above the search input field

#### Scenario: Resolved region name shown without expansion
- **WHEN** an admin region is resolved for the current position
- **AND** the search scope is the resolved region alone (no expansion)
- **THEN** the resolved region's name SHALL be displayed above the search input field

#### Scenario: No name without resolved region
- **WHEN** no usable GPS fix exists or region resolution failed
- **AND** the search panel is open
- **THEN** no region name SHALL be displayed above the search input field

#### Scenario: Name follows re-resolution
- **WHEN** the user moves beyond the movement threshold
- **AND** a new admin region is resolved
- **THEN** the displayed name SHALL update to the newly resolved scope region's name
