## ADDED Requirements

### Requirement: POI search covers all loaded maps with deterministic ordering
When multiple map databases are loaded, a POI search SHALL consider every loaded
(non-basemap) database whose type set contains the searched category, SHALL prefer
databases whose bounding box contains the search center over databases whose
bounding box does not, SHALL return each distinct object at most once even when
databases overlap geographically, and SHALL order the returned entries by distance
from the search center, ascending.

#### Scenario: All loaded maps contribute results
- **WHEN** two or more maps are loaded and a POI search runs
- **THEN** the result list may contain entries from every loaded map whose
  bounding box lies within the search radius, not only from the first map in
  database load order

#### Scenario: Bounding-box-containing map's results dominate
- **WHEN** a map whose bounding box contains the search center and a map whose
  bounding box does not both contain matching POIs
- **THEN** entries from the containing map appear in the results ahead of entries
  from the non-containing map

#### Scenario: Overlapping databases do not duplicate results
- **WHEN** two loaded maps overlap and both contain the same POI within the search
  radius
- **THEN** the object appears at most once in the result list

#### Scenario: Results sorted by distance ascending
- **WHEN** a POI search returns entries from one or more maps
- **THEN** the entries are ordered by distance from the search center, ascending

#### Scenario: Basemap still excluded
- **WHEN** a POI search runs and the low-zoom basemap is loaded
- **THEN** the basemap contributes no results

#### Scenario: Result limit still applied
- **WHEN** merged results across all loaded maps exceed the search limit
- **THEN** the returned list is truncated to the search limit, keeping the
  closest entries
