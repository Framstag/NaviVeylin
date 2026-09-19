# Spec Delta

## MODIFIED Requirements

### Requirement: Favorite hits prioritized and marked

Favorite hits SHALL be listed above native location results, regardless of match tier, and SHALL be visually marked as favorites with a heart icon. Native results whose coordinates match an existing favorite SHALL also be marked with a heart icon, and a native result that is additionally a perfect match for the query SHALL keep its perfect-match marking as well (spec: search-result-ranking) instead of one marking replacing the other.

#### Scenario: Favorite hit on top

- **WHEN** a query matches both a favorite and native locations
- **THEN** the favorite SHALL be listed first, marked with a heart icon

#### Scenario: Native result marked as favorite

- **WHEN** a native result's coordinates match an existing favorite within ~11 m
- **THEN** the native result SHALL be marked with a heart icon

#### Scenario: Favorite hit listed above a perfect native match

- **WHEN** a query matches a favorite and also a native location that is a perfect match for the query
- **THEN** the favorite SHALL still be listed above the native results

#### Scenario: Native result that is a favorite and a perfect match

- **WHEN** a native result matches an existing favorite within ~11 m
- **AND** the result is a perfect match for the query
- **THEN** the row SHALL show the favorite marking and the perfect-match marking together
