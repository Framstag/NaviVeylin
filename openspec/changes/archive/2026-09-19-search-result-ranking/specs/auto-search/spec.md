# Spec Delta

## MODIFIED Requirements

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
