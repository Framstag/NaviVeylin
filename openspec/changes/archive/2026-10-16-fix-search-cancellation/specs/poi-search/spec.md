# Spec Delta

## ADDED Requirements

### Requirement: A POI search keeps its own slot and is abandoned when its view is left

A POI search SHALL run in the POI surface's own search slot (spec: `search-request-lifecycle`), so a places or
car search never aborts it, and leaving the POI search view SHALL abandon the running POI search instead of
letting it finish.

#### Scenario: A places search does not stop the POI search

- **WHEN** a POI search is running and a places search starts
- **THEN** the POI search SHALL keep running
- **AND** its results SHALL still be displayed when it completes

#### Scenario: Leaving the POI view abandons its search

- **WHEN** the POI search view is closed while a POI search is running
- **THEN** that search SHALL be aborted at its next abort check
- **AND** no result of it SHALL be displayed

#### Scenario: A newer POI search supersedes the running one

- **WHEN** the user runs a POI search for another category while one is running
- **THEN** the first SHALL be aborted
- **AND** only the new category's results SHALL be displayed
