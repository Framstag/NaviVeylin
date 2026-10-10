# Spec Delta

## ADDED Requirements

### Requirement: Search scoped by the car position's admin region

The car search SHALL resolve the admin region containing the car's current position and pass it as the default admin region of the location search, so a query naming a location without its region qualifier resolves in the car as it does on the phone (spec: `location-search` — Search scoped by current admin region). A failed resolution SHALL leave the search unconstrained instead of failing it, and a handle no longer in use SHALL be released.

#### Scenario: POI named without its region resolves on the car
- **WHEN** the car has a usable position fix inside a known admin region
- **AND** the driver types a query that names a POI without the city or region (e.g. "Hilpert Theater")
- **AND** the map's location index contains that POI
- **THEN** the results list SHALL include that POI
- **AND** the result SHALL show the same label and region hierarchy as the phone search shows for the same query and position

#### Scenario: Fully qualified query stays resolvable
- **WHEN** the driver types a query carrying an explicit region or postal code that lies outside the current admin region
- **THEN** the search SHALL still match it, as on the phone
- **AND** the current admin region SHALL NOT suppress the match

#### Scenario: Resolution failure leaves the search unconstrained
- **WHEN** the region lookup for the current position fails (no region at that position, or the lookup errors)
- **THEN** the search SHALL run without a default admin region
- **AND** the results list SHALL be shown as before this change

### Requirement: Car region scope follows the car's movement

The car search SHALL apply the same region-usability, reuse and movement rule as the phone: a fix must be accurate enough to scope with, the resolved region SHALL be reused while the car has not moved significantly, and it SHALL be re-resolved once the car has moved beyond the movement threshold. Without a usable fix the search SHALL run unconstrained, exactly as before this change.

#### Scenario: Region reused while the car stays in place
- **WHEN** the car has already resolved a region
- **AND** the car has not moved significantly since that resolution
- **THEN** subsequent car queries SHALL be scoped with the region already resolved
- **AND** no new region lookup SHALL be required for them

#### Scenario: Region re-resolved after significant movement
- **WHEN** the car's position has moved beyond the movement threshold since the last resolution
- **THEN** the region containing the new position SHALL be resolved
- **AND** that region SHALL scope the next query

#### Scenario: No usable fix runs the search unconstrained
- **WHEN** the car has no position fix, or the last fix is not accurate enough
- **THEN** the car search SHALL run without a default admin region
- **AND** the search SHALL return the results it returned before this change

#### Scenario: Region released when the fix becomes unusable
- **WHEN** a previously resolved region exists
- **AND** the latest fix is missing or not accurate enough
- **THEN** the resolved region SHALL be released
- **AND** the next query SHALL be unscoped

### Requirement: Car and phone region scoping parity

Region scoping on the car SHALL behave exactly as the phone's for the same position and query: there SHALL be no car-specific difference in which locations a query matches. One platform deviation is mandated: the car SHALL NOT display the name of the resolved scope region, because a car `SearchTemplate` offers no free-text label row above the input (spec: `location-search` — Search scope region name shown in search panel remains phone-only).

#### Scenario: Same query matches the same locations on both surfaces
- **WHEN** the phone and the car both have a usable fix inside the same admin region
- **AND** the same query is issued on both surfaces against the same map database
- **THEN** the locations matched by the search SHALL be the same on both
- **AND** no car-specific filtering SHALL drop a match the phone returns

#### Scenario: Car template shows no scope-region label
- **WHEN** the car has resolved an admin region
- **THEN** the car search template SHALL NOT render a scope-region name row
- **AND** the phone search panel SHALL keep showing it
