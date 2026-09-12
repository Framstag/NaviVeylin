## Purpose

Lets users find their saved favorite locations by typing in the search surface — on the phone search dialog and the Android Auto search template — with favorite hits prioritized above native results and marked with a heart.

## ADDED Requirements

### Requirement: Favorites searchable by name
The search surface SHALL match favorites by case-insensitive substring of the favorite name when the user types a query of at least 2 characters, in addition to native location search.

#### Scenario: Favorite matches query
- **WHEN** the user types a query that is a substring of a favorite's name
- **THEN** the favorite SHALL appear in the results

#### Scenario: Short query does not search favorites
- **WHEN** the query is shorter than 2 characters
- **THEN** favorites SHALL NOT be searched

#### Scenario: Case-insensitive match
- **WHEN** the query differs from the favorite name only in letter case
- **THEN** the favorite SHALL still match

### Requirement: Favorite hits prioritized and marked
Favorite hits SHALL be listed above native location results and SHALL be visually marked as favorites with a heart icon. Native results whose coordinates match an existing favorite SHALL also be marked with a heart icon.

#### Scenario: Favorite hit on top
- **WHEN** a query matches both a favorite and native locations
- **THEN** the favorite SHALL be listed first, marked with a heart icon

#### Scenario: Native result marked as favorite
- **WHEN** a native result's coordinates match an existing favorite within ~11 m
- **THEN** the native result SHALL be marked with a heart icon

### Requirement: Deduplication of identical objects
When a favorite hit and a native result refer to the same object (coordinates within ~11 m), only the favorite hit SHALL be shown. Different objects SHALL both be shown.

#### Scenario: Identical favorite and native result
- **WHEN** a favorite hit and a native result have the same coordinates
- **THEN** only the favorite hit SHALL be shown

#### Scenario: Different objects both shown
- **WHEN** a favorite hit and a native result are different objects at different coordinates
- **THEN** both SHALL be shown

### Requirement: Favorite hit selection
Selecting a favorite hit SHALL behave like selecting a native search result: it SHALL record the query in search history and open the location details.

#### Scenario: Selecting favorite hit records history
- **WHEN** the user selects a favorite hit
- **THEN** the query SHALL be recorded in search history
- **AND** the details sheet SHALL open for the favorite's location

### Requirement: Phone and Android Auto parity
The phone search dialog and the Android Auto search template SHALL apply the same favorite-search behavior: same matching, prioritization, marking, and deduplication.

#### Scenario: Same behavior on both variants
- **WHEN** the same query is typed on the phone search dialog and the Android Auto search template
- **THEN** both SHALL show the same favorite hits, prioritized and marked identically
