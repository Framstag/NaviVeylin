# Spec Delta

## MODIFIED Requirements

### Requirement: Free-text results merged with structured results

Free-text results and structured location results SHALL be merged into a single candidate set, deduplicated by object reference, ranked by the tier rule (spec: search-result-ranking), and truncated to the surface's displayed maximum as the best-ranked prefix of that candidate set. Each source SHALL contribute up to the requested candidate count, so a full page of structured results SHALL NOT remove the free-text candidates before ranking. Free-text hits SHALL be close matches: because the text index reports no per-attribute match quality, a free-text hit SHALL NOT be marked as a perfect match even when its name equals the query exactly.

#### Scenario: Same object found by both searches appears once

- **WHEN** an object matches both the structured search and the free-text search
- **AND** the user searches for the object's name
- **THEN** the object appears exactly once in the result list

#### Scenario: Result list respects limit

- **WHEN** a search would return more results than the requested limit
- **AND** the user searches with a limit of N
- **THEN** the result list contains at most N entries
- **AND** the displayed entries are the best-ranked ones under the tier rule, chosen from a candidate set larger than N

#### Scenario: Free-text hit competes with structured candidates

- **WHEN** a query produces both structured candidates and free-text hits
- **THEN** the free-text hits SHALL NOT be treated as a tail that is appended only after every structured result
- **AND** they SHALL be ranked together with the structured candidates by tier, quality and distance
- **AND** a full page of structured candidates SHALL NOT remove the free-text hits from the candidate set

#### Scenario: Exact free-text hit is a close match

- **WHEN** a free-text hit's name equals the query exactly
- **THEN** the hit SHALL be displayed as a close match and SHALL NOT carry the perfect-match marking
