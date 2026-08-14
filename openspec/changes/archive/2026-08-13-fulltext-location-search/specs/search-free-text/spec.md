## Purpose

Free-text search over the MARISA text index finds POIs, locations, regions and other named objects that structured location search misses, and merges them into the existing search results.

## ADDED Requirements

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

Free-text results and structured location results SHALL be merged into a single result list, deduplicated by object reference, and truncated to the requested limit.

#### Scenario: Same object found by both searches appears once

- **WHEN** an object matches both the structured search and the free-text search
- **AND** the user searches for the object's name
- **THEN** the object appears exactly once in the result list

#### Scenario: Result list respects limit

- **WHEN** a search would return more results than the requested limit
- **AND** the user searches with a limit of N
- **THEN** the result list contains at most N entries

### Requirement: Free-text search excludes basemap

The basemap database SHALL NOT be searched, because it is a low-zoom background map whose index files may be absent or incomplete.

#### Scenario: Basemap never searched

- **WHEN** the user searches while the basemap is loaded
- **THEN** no results are returned from the basemap database
