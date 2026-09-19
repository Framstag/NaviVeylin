# Spec Delta

## MODIFIED Requirements

### Requirement: Suggestions-while-type

As the user types, the system SHALL query libosmscout's location search and display matching results below the input. Queries SHALL be debounced by 300ms from the last keystroke. The search SHALL request a candidate set larger than the number of results displayed (at least twice the displayed maximum), and the displayed list SHALL be the best-ranked prefix of that candidate set, ordered by match tier and distance (spec: search-result-ranking).

#### Scenario: Results appear while typing

- **WHEN** user types "Dort" in the search field
- **AND** the debounce interval of 300ms has elapsed
- **THEN** the system SHALL call `OSMScoutClient.searchLocations("Dort", <candidate count>)`
- **AND** the candidate count SHALL be at least twice the number of results displayed (at least 40 when 20 results are displayed)
- **AND** matching results SHALL appear in a list below the input

#### Scenario: Ranked order applied to displayed results

- **WHEN** the search returns candidates that include a perfect match for the query
- **THEN** the perfect match SHALL be displayed before the close matches
- **AND** at most 20 results SHALL be displayed

#### Scenario: Empty query clears results

- **WHEN** the search field is empty
- **THEN** the results list SHALL be hidden

#### Scenario: Loading indicator during search

- **WHEN** a search query is in progress
- **THEN** a loading indicator SHALL be shown in the results area

#### Scenario: No results state

- **WHEN** the search returns zero results
- **THEN** the system SHALL display "No results found" in the results area

### Requirement: Result item display

Each search result item SHALL display the location label and its region/admin hierarchy. When multiple results share the same label, each item SHALL additionally show distinguishing fields (`objectTypeName`, `postalArea`, region tail) to help users differentiate. A result that is a perfect match for the query SHALL carry the perfect-match marking (spec: search-result-ranking), which SHALL be combinable with the favorite marking.

#### Scenario: Result shows label and region

- **WHEN** search results are displayed
- **THEN** each result SHALL show the location name (`label`) and the region hierarchy (`adminRegionHierarchy` or `region` array joined)

#### Scenario: Duplicate results show disambiguation fields

- **WHEN** search results are displayed
- **AND** two or more results share the same `label`
- **THEN** each result in the duplicate group SHALL additionally show `objectTypeName`, `postalArea`, and the most specific `region` component
- **AND** these fields SHALL be formatted as a single detail line (e.g., "restaurant · 44139 · Dortmund")

#### Scenario: Perfect match is marked

- **WHEN** a displayed result is a perfect match for the query
- **THEN** its row SHALL carry the perfect-match marking
- **AND** a row that is also a favorite SHALL show the favorite marking as well, not instead

#### Scenario: Result item is tappable

- **WHEN** user taps a result item
- **THEN** the search panel SHALL dismiss
- **AND** the map SHALL center on the selected location's coordinates
- **AND** a marker SHALL be rendered at the selected location

### Requirement: Result distance display

Each search result entry SHALL display the straight-line (haversine) distance from the last known GPS fix to the result location, or from the fallback reference when no fix has been received (the map center on the phone), in kilometers, right-aligned in a smaller font than the entry's primary label text. The same reference SHALL be used for the result ordering, so the displayed numbers never contradict the displayed order (spec: search-result-ranking).

#### Scenario: Distance shown for each result

- **WHEN** search results are displayed in the result list
- **THEN** each entry SHALL show the distance from the reference point to the result location in kilometers
- **AND** the distance SHALL be right-aligned within the entry
- **AND** the distance SHALL be rendered in a smaller font than the entry's primary label text

#### Scenario: Distance shown in route panel search

- **WHEN** search results are displayed in the route panel start or destination search
- **THEN** each entry SHALL show the distance from the same reference point, formatted and positioned identically to the map search panel

#### Scenario: Distance formatting

- **WHEN** a result is less than 10 km from the reference point
- **THEN** the distance SHALL be shown with one decimal place (e.g. "0.5 km")
- **AND** the unit "km" SHALL be included in the displayed value
- **AND** when a result is 10 km or farther, the distance SHALL be shown as whole kilometers (e.g. "12 km")

#### Scenario: Distance follows current map center

- **WHEN** a new GPS fix arrives, or the user pans or recenters the map while no fix exists
- **THEN** the displayed distances SHALL be computed against the current reference point at display time (the fix when one exists, else the map center)
- **AND** the displayed order SHALL be recomputed against the same reference point

#### Scenario: No distance without a reference point

- **WHEN** neither a GPS fix nor a map center is available
- **THEN** the entries SHALL NOT show a distance
- **AND** the entries SHALL still be displayed in tier and quality order

### Requirement: Structured matches above free-text for address queries

For queries that tokenize like an address (street/house number/postal code/city), structured location-index matches SHALL rank above free-text matches. Under the tier rule (spec: search-result-ranking) this follows from the classification: the structured street or address entry is a perfect match when every queried attribute matches, while a free-text object whose name merely contains a city token is a close match at best and can never be a perfect match, because free-text hits report no per-attribute match quality.

#### Scenario: Free-text noise does not outrank structured match

- **WHEN** the user types a query that matches a structured street or address
- **AND** free-text matches exist for the same query (e.g. a bus stop named after the city)
- **THEN** the structured street/address match SHALL appear above the free-text matches

#### Scenario: Free-text still available for non-address queries

- **WHEN** the user types a query that does not tokenize like an address (e.g. "cafe central")
- **THEN** free-text POI matches SHALL continue to be returned as before
- **AND** they SHALL be ordered as close matches by quality and distance
