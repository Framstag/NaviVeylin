# Free-Text Search (search-free-text)

## Purpose

Free-text search over the MARISA text index finds POIs, locations, regions and other named objects that structured location search misses, and merges them into the existing search results.

## Requirements

### Requirement: Free-text search over text index

The search API SHALL search the text search index (MARISA) for POIs, locations, regions and other objects in addition to the structured location search. The search SHALL use transliteration, matching the `TextSearchIndex::Search` behavior used by OSMScout2.

#### Scenario: Search finds POI by name

- **WHEN** the user searches for "cafe central"
- **AND** the database's text index contains a POI named "Café Central"
- **THEN** the result list contains the POI "Café Central" with its coordinates

#### Scenario: Search covers all object groups

- **WHEN** the user searches for a term
- **AND** the database has a text index
- **THEN** POIs, locations, regions and other objects are all searched

#### Scenario: Search falls back without text index

- **WHEN** the user searches for a location
- **AND** the database has no text index
- **THEN** the search still returns structured location results without failing

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

### Requirement: Free-text search excludes basemap

The basemap database SHALL NOT be searched, because it is a low-zoom background map whose index files may be absent or incomplete.

#### Scenario: Basemap never searched

- **WHEN** the user searches while the basemap is loaded
- **THEN** no results are returned from the basemap database

### Requirement: Free-text results without garbage entries

Free-text search results SHALL NOT contain empty or invalid entries. Entries whose text index reference cannot be resolved to a real object, entries whose object resolves to invalid (0,0) coordinates, and entries introduced by result-list padding SHALL be omitted from the result list.

#### Scenario: Unresolvable index entry omitted

- **WHEN** the free-text search returns an entry whose object reference cannot be resolved (e.g. a stale or mismatched text index key)
- **THEN** the entry SHALL be omitted from the results
- **AND** the remaining valid results SHALL be returned normally

#### Scenario: Zero-coordinate entry omitted

- **WHEN** a free-text search entry resolves to an object with (0,0) coordinates
- **THEN** the entry SHALL be omitted from the results

#### Scenario: Invalid string data entry omitted

- **WHEN** a search entry carries string data that is not valid UTF-8 (e.g. garbage bytes from a corrupt text index or database entry)
- **THEN** the entry SHALL be omitted from the results
- **AND** the search SHALL complete without crashing

#### Scenario: No padding entries below limit

- **WHEN** the number of valid free-text results is smaller than the remaining space in the result list
- **THEN** the result list SHALL NOT be padded with empty entries
- **AND** the result list SHALL contain at most the requested limit of entries, all of which have a non-empty label and valid coordinates

### Requirement: Free-text candidate budget counts kept candidates

The per-source free-text budget SHALL count the candidates the search keeps, not every hit it inspects. A hit
the active scope refuses SHALL NOT spend the budget, so a scoped search SHALL deliver the requested candidate
count when the text index reports that many in-scope hits. The budget SHALL NOT be read as bounding the
index's traversal: the index hands a query's complete hit set back in one call.

#### Scenario: A scoped walk delivers the requested candidates past refused hits

- **WHEN** a search runs with an active scope and a candidate limit of 30
- **AND** the text index reports 60 hits, the first 30 of which lie outside the scope's extent
- **THEN** the search inspects all 60 hits and keeps 30 in-scope candidates
- **Case**: `Tests/src/SearchScopeTest.cpp` "A scoped free-text walk collects the requested candidates, not
  fewer" — red with the HEAD rule (kept 0 for a limit of 30), green with this change

#### Scenario: A refused hit does not spend the budget

- **WHEN** the budget allows one candidate and the first hit offered lies outside the scope's extent
- **THEN** that hit is refused and the budget is still unspent
- **AND** the next in-scope hit is kept and spends it
- **Case**: `Tests/src/SearchScopeTest.cpp` "A refused hit does not spend the budget"

#### Scenario: Without a scope every hit spends the budget

- **WHEN** no scope is active (an unset extent) and the limit is 5
- **THEN** the fifth hit offered spends the budget, because an unset extent admits every position
- **Case**: `Tests/src/SearchScopeTest.cpp` "Without a scope every hit spends the budget"
