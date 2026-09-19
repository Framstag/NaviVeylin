# Auto Search (auto-search)

## Purpose

Lets drivers search for locations on the Android Auto car screen using `SearchTemplate`, backed by the existing `OSMScoutClient.searchLocations()` backend, so they can find destinations without touching the phone.

## Requirements

### Requirement: SearchTemplate displayed on car screen
The system SHALL display a `SearchTemplate` on the Android Auto screen when the user is not actively navigating and selects the search option.

#### Scenario: SearchTemplate shown
- **WHEN** user selects search from the car screen
- **THEN** a `SearchTemplate` is displayed with a text input field

#### Scenario: SearchTemplate hidden on navigation start
- **WHEN** user starts navigation from a search result
- **THEN** the `SearchTemplate` is replaced by the `NavigationTemplate`

### Requirement: Search-as-you-type with debounce

The system SHALL perform location search as the user types, with a debounce of no more than 500ms after the user stops typing. An empty query SHALL NOT trigger a search; it SHALL show the empty-query suggestions (mode rows and recent searches, see `auto-search-suggestions`) instead of search results.

#### Scenario: Search results update on input
- **WHEN** user types a query in the search field
- **THEN** search results update automatically after a brief debounce period

#### Scenario: Empty query shows no results
- **WHEN** the search field is empty
- **THEN** no search runs
- **AND** the empty-query suggestions (mode rows and recent searches) SHALL be shown instead of search results

### Requirement: Search results displayed as list
The system SHALL display search results as a scrollable list in the `SearchTemplate`, showing location name, address/description, the distance from the reference point, and the perfect-match marking for results that are perfect matches. Ordering, marking and the distance reference SHALL be identical to the phone search dialog (spec: search-result-ranking), with one platform constraint: a car `Row` provides a single image slot (already used by the favorite heart) and at most two text lines, so the perfect-match marking is glyph-only on the car and carries no accessible text; the distance is shown as text.

#### Scenario: Results list shown
- **WHEN** search results are available
- **THEN** they are displayed as a scrollable list with location name, description and distance

#### Scenario: Perfect match marked on the car
- **WHEN** a displayed result is a perfect match for the query
- **THEN** its row SHALL carry the perfect-match glyph
- **AND** a row that is also a favorite SHALL show both facts

#### Scenario: Car uses the same reference rule as the phone
- **WHEN** the car surface has a GPS fix
- **THEN** the distances and the ordering SHALL be computed from the fix
- **AND** when no fix has been received, the current car map viewport center SHALL be used as the fallback reference

#### Scenario: No results state
- **WHEN** search returns no results
- **THEN** the screen shows a "No results found" message

### Requirement: Search result limit
The system SHALL obtain more candidates from the search backend than it displays (at least twice the displayed maximum) and SHALL display at most 20 results, as the best-ranked prefix of that candidate set under the tier rule (spec: search-result-ranking).

#### Scenario: Results capped at 20
- **WHEN** a search query matches more than 20 locations
- **THEN** only the 20 best-ranked results are displayed

#### Scenario: Perfect match beyond the backend's own ordering reaches the list
- **WHEN** the backend's own ordering places a perfect match outside the first 20 candidates
- **THEN** that perfect match SHALL still be displayed in the 20-row list

### Requirement: Search result selection triggers destination picker
The system SHALL allow the user to select a search result, which opens the details screen for that location (the destination picker flow); navigation starts only from the details screen's "Navigate here" action.

#### Scenario: Select search result
- **WHEN** user taps a search result
- **THEN** the system opens the details screen with that location as the target

#### Scenario: Navigation starts from details screen
- **WHEN** user taps "Navigate here" on the details screen
- **THEN** the system starts navigation to the selected location

### Requirement: Favorite hits in search results
The AA search template SHALL include favorites matching the query in its results, listed above native results regardless of match tier, marked with a heart icon, deduplicated against identical native results (see favorite-search), and marked with the perfect-match glyph as well when the result is a perfect match.

#### Scenario: Favorite hit on top
- **WHEN** the user types a query matching a favorite
- **THEN** the favorite SHALL appear above native results, marked with a heart icon

#### Scenario: Identical native result suppressed
- **WHEN** a native result has the same coordinates as a favorite hit
- **THEN** only the favorite hit SHALL be shown

#### Scenario: Native result that is a favorite and a perfect match
- **WHEN** a native result matches a favorite and is a perfect match for the query
- **THEN** its row SHALL show the favorite glyph and the perfect-match glyph together

### Requirement: Full formatted address resolution on car screen
When the driver types a full formatted address (street with house number, postal code, and city in one query, e.g. "Erbstollenstraße 10, 58454 Witten") in the car search template, the system SHALL resolve it using the same structured search used on the phone: the postal code inside the query SHALL NOT cause an empty result, and structured street/address matches SHALL rank above free-text matches. Free-text POI matches SHALL remain available for queries that do not tokenize like an address.

#### Scenario: Full address with postal code resolves on car screen
- **WHEN** the driver types "Erbstollenstraße 10, 58454 Witten" in the car search template
- **AND** the map's location index contains house number 10 on that street
- **THEN** the results SHALL contain the house-level entry for the address
- **AND** it SHALL be ranked above any street-, region-, or free-text result for the same query

#### Scenario: Postal code inside query does not block
- **WHEN** the driver types an address with the postal code between the house number and the city (e.g. "Erbstollenstraße 10 58454 Witten")
- **THEN** the search SHALL return the matching structured result(s)
- **AND** the result SHALL NOT be empty solely because of the extra postal-code tokens

#### Scenario: Free-text noise does not outrank structured match
- **WHEN** the driver types a query that matches a structured street or address
- **AND** free-text matches exist for the same query
- **THEN** the structured street/address match SHALL appear above the free-text matches

#### Scenario: Free-text still available for non-address queries
- **WHEN** the driver types a query that does not tokenize like an address (e.g. "cafe central")
- **THEN** free-text POI matches SHALL continue to be returned as before

### Requirement: Transliterated name matching parity with phone search

The car search template SHALL match transliterated name spellings exactly as the phone search does, because both use the same location search backend. No platform constraint forces a deviation: there SHALL be no car-specific difference in which names a query matches. A query spelling a sharp-s street with `ss` SHALL return the same results in the car template as on the phone, with the same labels and region hierarchy.

#### Scenario: ss-spelled query finds sharp-s street in the car

- **WHEN** the driver types a street name spelled with `ss` (e.g. "Erbstollenstrasse") in the car search template
- **AND** the map's location index contains that street spelled with `ß` (e.g. "Erbstollenstraße")
- **THEN** the car results list SHALL include that street
- **AND** the result SHALL show the same label and region hierarchy as the phone search shows for the same query

#### Scenario: Car and phone result sets agree

- **WHEN** the same transliterated query is issued on the phone and in the car search template against the same map database
- **THEN** the matching entries SHALL be the same on both screens
- **AND** no car-specific result filtering SHALL remove transliterated matches

#### Scenario: Fully qualified address in the car resolves

- **WHEN** the driver types a full formatted address whose street is spelled with `ss` (e.g. "Erbstollenstrasse 10, 58454 Witten") in the car search template
- **THEN** the structured search SHALL resolve it as on the phone
- **AND** the results SHALL include the matching street or house-level entry
