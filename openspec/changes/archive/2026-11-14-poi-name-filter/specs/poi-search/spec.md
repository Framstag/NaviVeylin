## ADDED Requirements

### Requirement: A name filter narrows a POI result list

The POI search SHALL let the user narrow the result list with a name filter. A term SHALL match when it is a substring of the entry's name, operator or brand compared under the search's own folding, so `rewe`, `REWE` and `REWÉ` agree. An empty filter SHALL show the unfiltered list. A filter change SHALL narrow the current candidates without running a new search, while a category or radius change still requires the explicit search button.

#### Scenario: A term matches the name

- **WHEN** a grocery search returns a shop whose name contains `REWE`
- **AND** the user types `rewe` into the filter
- **THEN** that shop remains in the list
- **Case**: `core: PoiNameFilterTest.aTermMatchesTheName`

#### Scenario: A term matches the operator

- **WHEN** a grocery search returns a shop named `Supermarkt` whose operator is `REWE Markt GmbH`
- **AND** the user types `rewe` into the filter
- **THEN** that shop remains in the list even though its name does not contain the term
- **Case**: `core: PoiNameFilterTest.aTermMatchesTheOperator`

#### Scenario: A term matches the brand

- **WHEN** a fuel search returns a station whose brand is `Shell`
- **AND** the user types `shell` into the filter
- **THEN** that station remains in the list
- **Case**: `core: PoiNameFilterTest.aTermMatchesTheBrand`

#### Scenario: Folding makes a query match a diacritic spelling

- **WHEN** an entry's operator is `Müller` and the user types `muller`
- **THEN** the entry remains in the list
- **Case**: `core: PoiNameFilterTest.foldingMakesAQueryMatchADiacriticSpelling`

#### Scenario: An empty filter shows every candidate

- **WHEN** the filter field is empty
- **THEN** every candidate of the current search is displayed, as before the filter existed
- **Case**: `app: PoiSearchPanelComposeTest.anEmptyFilterShowsEveryCandidate`

#### Scenario: A filter change runs no search

- **WHEN** the user types into the filter field after a search has completed
- **THEN** no new search SHALL run
- **Case**: `app: PoiSearchViewModelTest.aFilterChangeRunsNoSearch`

#### Scenario: A category change still needs the search button

- **WHEN** the user changes the category after a search has completed
- **THEN** no search SHALL run until the search button is tapped
- **Case**: `app: PoiSearchViewModelTest.aCategoryChangeStillNeedsTheSearchButton`

#### Scenario: A filter that matches nothing says so

- **WHEN** the candidates are non-empty and the typed term matches none of them
- **THEN** the sheet SHALL state that no entry matches that term
- **AND** it SHALL NOT show the no-POIs-in-range message
- **Case**: `app: PoiSearchPanelComposeTest.aFilterWithNoMatchSaysSo`

#### Scenario: No POIs in range reads differently from no match

- **WHEN** a search returns no candidates at all
- **THEN** the sheet SHALL show the no-POIs-in-range message, with or without a filter
- **Case**: `app: PoiSearchPanelComposeTest.noPoisInRangeReadsDifferentlyFromNoMatch`

#### Scenario: The first non-empty filter widens the fetch once

- **WHEN** the user types the first character of a filter after a search that returned the display limit
- **THEN** the POI walk SHALL be asked once for the candidate cap
- **Case**: `app: PoiSearchViewModelTest.theFirstNonEmptyFilterWidensTheFetchOnce`

#### Scenario: Later filter keystrokes ask the backend for nothing

- **WHEN** the candidate set has already been widened for the current category, radius and centre
- **AND** the user keeps typing in the filter field
- **THEN** no further POI walk SHALL run
- **Case**: `app: PoiSearchViewModelTest.laterFilterKeystrokesAskTheBackendForNothing`

#### Scenario: A new search narrows the fetch again

- **WHEN** the user taps the search button again
- **THEN** the walk SHALL be asked for the display limit unless a filter is in effect at that moment
- **Case**: `app: PoiSearchViewModelTest.aNewSearchNarrowsTheFetchAgain`

#### Scenario: A failed widen keeps the rows on screen

- **WHEN** the widened fetch fails
- **THEN** the previous candidates SHALL stay displayed, the failure SHALL be reported, and the sheet SHALL stay usable
- **Case**: `app: PoiSearchViewModelTest.aFailedWidenKeepsTheRowsOnScreen`

#### Scenario: A filter without a category runs nothing

- **WHEN** no category is selected and the user types into the filter field
- **THEN** no search SHALL run
- **Case**: `app: PoiSearchViewModelTest.aFilterWithoutACategoryRunsNothing`

### Requirement: A filtered search asks for a candidate set, not a page

When a name filter is in effect the POI walk SHALL be asked for the candidate cap rather than the display limit, and the displayed list SHALL be the nearest display-max matches of the filtered set. The cap SHALL be at least twice the display maximum. A filter SHALL never be applied to a list the display limit has already truncated.

#### Scenario: A filtered search asks for the candidate cap

- **WHEN** a search runs with a non-empty filter
- **THEN** the POI walk SHALL be asked for the candidate cap
- **Case**: `app: PoiSearchViewModelTest.aFilteredSearchAsksForTheCandidateCap`

#### Scenario: The displayed list is the nearest matches

- **WHEN** the filtered candidate set holds more entries than the display maximum
- **THEN** the displayed list SHALL be the nearest display-max entries of that filtered set
- **Case**: `app: PoiSearchViewModelTest.theDisplayedListIsTheNearestMatches`

#### Scenario: A match past the display limit is found

- **WHEN** the only entry matching the term is further from the centre than the display limit's worth of non-matching entries
- **THEN** that entry SHALL be displayed
- **Case**: `app: PoiSearchViewModelTest.aMatchPastTheDisplayLimitIsFound`

#### Scenario: An unfiltered search asks for the display limit

- **WHEN** a search runs with an empty filter
- **THEN** the POI walk SHALL be asked for the display limit
- **Case**: `app: PoiSearchViewModelTest.anUnfilteredSearchAsksForTheDisplayLimit`
