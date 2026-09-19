# Spec Delta

## Purpose

Defines how location search results are classified into match tiers and ordered, so that a query naming a place exactly returns that place first, and so that phone and Android Auto show the same order for the same query.

## ADDED Requirements

### Requirement: Query attributes classified as criteria or context

The system SHALL determine which attributes the user's query names and treat each of them as a criterion. Admin region and postal area SHALL be context attributes: they SHALL be ignored when the query does not name them, and when the query does name them they SHALL be required to match. Street/location name, house number and POI name SHALL be criterion attributes: an entry carrying such an attribute that the query did not name SHALL NOT qualify as a perfect match. Attribute comparison SHALL fold letter case and transliteration the same way the search itself does, so `Erbstollenstrasse` and `Erbstollenstraße` count as the same name.

#### Scenario: City query against a street inside that city

- **WHEN** the user types a city name (e.g. "Waltrop")
- **AND** the result list contains a street whose name starts with that city name (e.g. "Waltroper Straße") because the street lies inside the city
- **THEN** the street SHALL NOT qualify as a perfect match, because its name matched only partially
- **AND** the entry for the city itself SHALL qualify as a perfect match, because its name matched exactly and it carries no criterion attribute the query did not name

#### Scenario: Street query against a house-level entry

- **WHEN** the user types a street name without a house number (e.g. "Erbstollenstraße")
- **AND** the result list contains the house-level entry for that street (e.g. "Erbstollenstraße 10")
- **THEN** the house-level entry SHALL NOT qualify as a perfect match, because it carries a house number the query did not name
- **AND** the street-level entry for the same street SHALL qualify as a perfect match

#### Scenario: City and street both named

- **WHEN** the user types a street name together with its city
- **AND** a result entry matches both attributes exactly
- **THEN** that entry SHALL qualify as a perfect match

#### Scenario: Postal code named but not matching

- **WHEN** the user types a postal code in the query
- **AND** a result entry carries a different postal area
- **THEN** that entry SHALL NOT qualify as a perfect match

#### Scenario: Postal code present but not named

- **WHEN** the query does not name a postal code
- **AND** a result entry carries a postal area
- **THEN** the postal area SHALL NOT prevent the entry from qualifying as a perfect match

#### Scenario: Admin region present but not named

- **WHEN** the query does not name an admin region
- **AND** a result entry carries an admin region (including a region supplied as the GPS-derived search scope)
- **THEN** the admin region SHALL NOT prevent the entry from qualifying as a perfect match
- **AND** the entry's admin region matching quality SHALL NOT be treated as evidence that the query named it

#### Scenario: Transliterated spelling counts as the same name

- **WHEN** the user types a name spelled with `ss` (e.g. "Erbstollenstrasse")
- **AND** a result entry spells the same name with `ß` (e.g. "Erbstollenstraße")
- **THEN** the entry SHALL qualify as a perfect match, exactly as if the spellings were identical

### Requirement: Perfect match classification uses native per-attribute quality

A result SHALL be a perfect match when every criterion named by the query is present on the entry with match quality `match`, and the entry carries no extra criterion attribute. A result that is not a perfect match SHALL be a close match. The classification SHALL use the per-attribute match quality reported by the search backend for each attribute (admin region, postal area, location name, house number, POI name), not a single collapsed quality value.

#### Scenario: Exact attribute match classifies as perfect

- **WHEN** a query criterion matches a result attribute exactly
- **THEN** that criterion SHALL count as matched for the perfect-match classification
- **AND** the criterion SHALL count as unmatched when the attribute matched only partially (e.g. a prefix of the entry name)

#### Scenario: Missing criterion excludes perfect match

- **WHEN** the query names an attribute that a result entry does not carry
- **THEN** that entry SHALL NOT be a perfect match

#### Scenario: Partially matched criterion excludes perfect match

- **WHEN** a result entry carries the queried attribute but the attribute matched only partially
- **THEN** that entry SHALL NOT be a perfect match

### Requirement: Result ordering by tier then distance

The displayed result list SHALL be ordered as follows: perfect matches first, ascending by distance; then close matches, ordered by match quality (better quality first) and ascending by distance within equal quality. Entries that are equal under those keys SHALL be ordered deterministically by label so the same query produces a stable list.

#### Scenario: Exact place ranked above prefix matches

- **WHEN** the user types a place name that exactly matches one entry
- **AND** other entries match the same query only as a name prefix (e.g. streets named after that place)
- **THEN** the exactly matching entry SHALL appear before all prefix-matching entries

#### Scenario: Perfect matches ordered by distance

- **WHEN** two or more perfect matches are in the result list
- **THEN** the nearer perfect match SHALL appear first

#### Scenario: Close matches ordered by quality then distance

- **WHEN** two close matches are in the result list
- **THEN** the match with the better match quality SHALL appear first
- **AND** when both have equal quality, the nearer one SHALL appear first

#### Scenario: Stable order for equal results

- **WHEN** two results are equal in tier, quality and distance
- **THEN** their relative order SHALL be determined by label, producing the same list for repeated identical queries

### Requirement: Candidate set larger than displayed list

The search SHALL obtain more candidate results from the backend than it displays, at least twice the displayed maximum, and the displayed list SHALL be the best-ranked prefix of that candidate set. The displayed list SHALL NOT exceed the surface's maximum result count.

#### Scenario: Perfect match outside the backend's first page reaches the list

- **WHEN** a search returns a perfect match that the backend's own ordering places beyond the surface's maximum result count
- **AND** the candidate set is larger than that maximum
- **THEN** the perfect match SHALL appear in the displayed list

#### Scenario: Displayed list capped

- **WHEN** a search matches more entries than the surface's maximum result count
- **THEN** at most that maximum number of entries SHALL be displayed

### Requirement: Distance reference for ordering and display

Distances used for ordering and shown in result rows SHALL be computed from the last known GPS fix. When no fix has ever been received, the fallback reference SHALL be the phone map center on the phone surfaces and the current car map viewport center on the car surface. When neither a fix nor a fallback center exists, results SHALL be ordered by tier and match quality only, and no distance SHALL be displayed.

#### Scenario: Fix available while the map is panned elsewhere

- **WHEN** a GPS fix is available
- **AND** the user has panned the map away from their position
- **THEN** both the displayed distances and the ordering SHALL be computed from the fix, not from the panned map center

#### Scenario: No fix uses the fallback center

- **WHEN** no GPS fix has been received
- **AND** a fallback center is available
- **THEN** ordering and displayed distances SHALL be computed from the fallback center

#### Scenario: No reference available

- **WHEN** neither a GPS fix nor a fallback center is available
- **THEN** the results SHALL still be ordered by tier and match quality
- **AND** no distance SHALL be displayed

### Requirement: Coordinate result answers a coordinate query exactly

A result of the coordinate type SHALL be classified as a perfect match, because the coordinate it carries *is* the query: such an entry fills no attribute and its coordinates are the user's own input. It therefore ranks in the perfect tier, ordered by distance like every other perfect match, and its classification SHALL NOT rest on a fabricated per-attribute quality — every component quality is reported as `none` for it.

#### Scenario: Coordinate query keeps its result in the perfect tier

- **WHEN** the user types a coordinate (e.g. "51.5136, 7.4653")
- **AND** the search returns the coordinate result together with other candidates
- **THEN** the coordinate result SHALL be classified as a perfect match
- **AND** the tier order SHALL place it before the close matches

#### Scenario: Coordinate result carries no fabricated attribute quality

- **WHEN** a coordinate result is returned
- **THEN** each of its per-attribute match qualities SHALL be `none`
- **AND** the classification SHALL follow from its type alone

### Requirement: Degradation without per-attribute quality

When the native search backend does not report per-attribute match quality, results SHALL be treated as close matches ordered by distance, no result SHALL be marked as a perfect match, and the search SHALL complete without crashing.

#### Scenario: Missing quality data

- **WHEN** a search result carries no per-attribute match quality
- **THEN** no result in that list SHALL be marked as a perfect match
- **AND** the list SHALL still be displayed in a deterministic order

### Requirement: Perfect-match marking and cross-surface parity

Perfect matches SHALL be visually marked in the result row on the phone search dialog, the route-panel location picker and the Android Auto search template. The perfect-match marking SHALL be combinable with the favorite marking, so a row that is both a favorite and a perfect match shows both facts rather than one replacing the other. Phone and car surfaces SHALL apply the same tiering, the same ordering inputs and the same reference rule, so the same query produces the same order on both.

#### Scenario: Phone row that is a favorite and a perfect match

- **WHEN** a result is both a favorite hit and a perfect match
- **THEN** its row SHALL show both facts
- **AND** the row's accessibility label SHALL state both facts

#### Scenario: Perfect match on the car surface

- **WHEN** a result is a perfect match on the Android Auto search template
- **THEN** its row SHALL carry the same perfect-match marking as on the phone
- **AND** the marking SHALL be glyph-only, because a car `Row` provides a single image slot and at most two text lines and carries no accessibility text channel beyond its title and text lines

#### Scenario: Same query on both surfaces

- **WHEN** the same query is typed on the phone search dialog and on the Android Auto search template
- **AND** both surfaces have a GPS fix or both fall back to their map center
- **THEN** both SHALL display the same entries in the same order, with the same markings
