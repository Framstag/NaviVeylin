# Spec Delta

## MODIFIED Requirements

### Requirement: Search-as-you-type with debounce

The system SHALL perform location search as the user types, with a debounce of no more than 500ms after the user stops typing. A query shorter than the minimum length (2 characters, spec: `search-request-lifecycle`) SHALL NOT trigger a search. An empty query SHALL NOT trigger a search; it SHALL show the empty-query suggestions (mode rows and recent searches, see `auto-search-suggestions`) instead of search results.

#### Scenario: Search results update on input

- **WHEN** user types a query in the search field
- **THEN** search results update automatically after a brief debounce period

#### Scenario: Empty query shows no results

- **WHEN** the search field is empty
- **THEN** no search runs
- **AND** the empty-query suggestions (mode rows and recent searches) SHALL be shown instead of search results

#### Scenario: A one-character query runs nothing

- **WHEN** the driver has typed one character in the search field
- **THEN** no search SHALL run
- **AND** no results SHALL be shown

## ADDED Requirements

### Requirement: The car search screen stops its own search when it leaves its started period

When the car search screen stops being started, the screen SHALL abandon its running search
(spec: `search-request-lifecycle`) and SHALL make no host-facing call for its result
(spec: `auto/screen-observation` — a stopped screen performs no host work).

#### Scenario: Leaving the search screen abandons its search

- **WHEN** the search screen is left while a search is running
- **THEN** that search SHALL be aborted at its next abort check
- **AND** no template refresh SHALL be requested for its result

#### Scenario: No results arrive for a screen that is gone

- **WHEN** a search of a screen that has been left completes
- **THEN** no host call SHALL be made for it
