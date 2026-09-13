# location-search delta

## ADDED Requirements

### Requirement: Full formatted address resolution
When the user types a full formatted address (street with house number, postal code, and city in one query, e.g. "Erbstollenstraße 10, 58454 Witten"), the system SHALL resolve it to the house-level location when the map's location index contains the address, and to the best available structured result (street or region) when it does not. The postal code inside the query SHALL NOT cause the search to return no results.

#### Scenario: Full address with postal code resolves
- **WHEN** the user types "Erbstollenstraße 10, 58454 Witten" in the search field
- **AND** the map's location index contains house number 10 on that street
- **THEN** the results SHALL contain the house-level entry for the address
- **AND** it SHALL be ranked above any street- or region-level result for the same query

#### Scenario: House number missing from index falls back to street
- **WHEN** the user types a full address whose house number is not in the map's location index, but whose street is
- **THEN** the search SHALL return the street-level result instead of reporting no results

#### Scenario: Postal code inside query does not block
- **WHEN** the user types an address with the postal code between the house number and the city (e.g. "Erbstollenstraße 10 58454 Witten")
- **THEN** the search SHALL return the matching structured result(s)
- **AND** the result SHALL NOT be empty solely because of the extra postal-code tokens

### Requirement: Structured matches above free-text for address queries
For queries that tokenize like an address (street/house number/postal code/city), structured location-index matches SHALL be ranked above free-text matches, so unrelated free-text objects (e.g. bus stops whose name merely contains a city token) cannot outrank the matching street or address.

#### Scenario: Free-text noise does not outrank structured match
- **WHEN** the user types a query that matches a structured street or address
- **AND** free-text matches exist for the same query (e.g. a bus stop named after the city)
- **THEN** the structured street/address match SHALL appear above the free-text matches

#### Scenario: Free-text still available for non-address queries
- **WHEN** the user types a query that does not tokenize like an address (e.g. "cafe central")
- **THEN** free-text POI matches SHALL continue to be returned as before

## MODIFIED Requirements

### Requirement: Search scoped by current admin region
When a usable GPS fix is available, the map screen search panel SHALL resolve the admin region containing the current position and pass it as the default admin region to the native search call. The search scope SHALL be the highest ancestor of the resolved region at or finer than the maximum region level cap (walking up the parent chain while each parent is at or finer than the cap), so addresses and POIs match when the user omits the region qualifier even if they lie in a neighboring subregion of the same scope (the scope's recursive search covers all its subregions in one pass). When the resolved region has no parent, or every ancestor is coarser than the cap, the scope SHALL be the resolved region alone. The cap SHALL default to level 5 (Regierungsbezirk/district on the OSM admin_level scale: 2=country, 4=state, 5=Regierungsbezirk, 6=county, 8=municipality), so a kreisfreie Stadt (level 6) and its surrounding towns share a scope. The search SHALL still match fully qualified queries regardless of the default region, including queries that carry a postal code. Without a usable GPS fix (no fix or poor accuracy), the search SHALL run unconstrained, exactly as before this change. The last known position SHALL remain valid for region scoping regardless of fix age; the region SHALL be re-resolved when a fresh fix shows movement beyond the movement threshold. Search initiated from the route panel SHALL remain unconstrained.

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

#### Scenario: Fully qualified query with postal code still matches
- **WHEN** the user types a fully qualified query with a postal code (e.g. "Hauptstraße 12 58454 Witten")
- **THEN** the results SHALL include the matching street or address
- **AND** the result SHALL NOT be empty solely because of the postal-code tokens

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
