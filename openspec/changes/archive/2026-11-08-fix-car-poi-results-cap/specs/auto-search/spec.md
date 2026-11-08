# Spec Delta — auto-search

## MODIFIED Requirements

### Requirement: Search result limit

The system SHALL obtain more candidates from the search backend than it displays (at least twice the displayed maximum). The car search template SHALL display at most 20 results, as the best-ranked prefix of that candidate set under the tier rule (spec: search-result-ranking). A car POI category result list SHALL take both numbers from the connected host: it SHALL present at most the row capacity the host reports for a list (spec: `car-list-row-limit`), and its POI walk SHALL be asked for at least twice that capacity, so the rows past the page stay reachable through its page row without a further search.

#### Scenario: Results capped at 20

- **WHEN** a search query matches more than 20 locations
- **THEN** only the 20 best-ranked results are displayed

#### Scenario: Perfect match beyond the backend's own ordering reaches the list

- **WHEN** the backend's own ordering places a perfect match outside the first 20 candidates
- **THEN** that perfect match SHALL still be displayed in the 20-row list

#### Scenario: The POI walk is asked for twice the rows the host shows

- **WHEN** the car POI results screen searches a category and the connected host reports a row capacity
- **THEN** the POI walk SHALL be asked for at least twice that capacity
- **Case**: `auto: PoiResultsScreenTest.thePoiSearchAsksForTwiceTheHostsCapacity`
