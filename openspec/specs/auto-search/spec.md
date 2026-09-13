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
The system SHALL display search results as a scrollable list in the `SearchTemplate`, showing location name and address/description.

#### Scenario: Results list shown
- **WHEN** search results are available
- **THEN** they are displayed as a scrollable list with location name and description

#### Scenario: No results state
- **WHEN** search returns no results
- **THEN** the screen shows a "No results found" message

### Requirement: Search result limit
The system SHALL limit search results to a maximum of 20 items.

#### Scenario: Results capped at 20
- **WHEN** a search query matches more than 20 locations
- **THEN** only the top 20 results are displayed

### Requirement: Search result selection triggers destination picker
The system SHALL allow the user to select a search result, which opens the details screen for that location (the destination picker flow); navigation starts only from the details screen's "Navigate here" action.

#### Scenario: Select search result
- **WHEN** user taps a search result
- **THEN** the system opens the details screen with that location as the target

#### Scenario: Navigation starts from details screen
- **WHEN** user taps "Navigate here" on the details screen
- **THEN** the system starts navigation to the selected location

### Requirement: Favorite hits in search results
The AA search template SHALL include favorites matching the query in its results, listed above native results and marked with a heart icon, deduplicated against identical native results (see favorite-search).

#### Scenario: Favorite hit on top
- **WHEN** the user types a query matching a favorite
- **THEN** the favorite SHALL appear above native results, marked with a heart icon

#### Scenario: Identical native result suppressed
- **WHEN** a native result has the same coordinates as a favorite hit
- **THEN** only the favorite hit SHALL be shown

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
