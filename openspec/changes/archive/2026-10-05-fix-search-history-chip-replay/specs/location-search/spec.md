# Spec Delta

## ADDED Requirements

### Requirement: A repeated query text still runs the search

A search request whose text equals the query text of the previously requested search SHALL still run and publish its results. Replaying the same query — from a recent-search chip, a route field, or a shared location that opens the search dialog with text — SHALL NOT leave the results area empty or hold results from an earlier query.

#### Scenario: Re-requesting the previous query text

- **WHEN** the user searched "Bochum" and selected a result
- **AND** a search is requested again for the text "Bochum"
- **THEN** the search SHALL run for "Bochum"
- **AND** its results SHALL be published to the results list

#### Scenario: Same text arriving from another entry point

- **WHEN** the previous search request was for the text "Bochum"
- **AND** a shared location opens the search dialog with the query text "Bochum"
- **THEN** the search SHALL run for "Bochum"
- **AND** its results SHALL be published to the results list

#### Scenario: Empty query after a committed search

- **WHEN** the user selected a search result, which empties the search box
- **THEN** a subsequent search request for any text SHALL run, whatever the previously requested text was
