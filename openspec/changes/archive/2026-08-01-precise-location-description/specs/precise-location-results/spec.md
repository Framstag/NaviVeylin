## Purpose

Disambiguate search results that share identical address labels by showing additional distinguishing fields inline, so users can pick the correct location.

## ADDED Requirements

### Requirement: Duplicate-label detection
The system SHALL detect when multiple search results share the same `label` value and flag them as a duplicate group.

#### Scenario: Same label detected
- **WHEN** search returns two results both with `label = "Hauptstraße"`
- **THEN** the system SHALL mark both as belonging to a duplicate group

#### Scenario: Unique label no grouping
- **WHEN** search returns one result with `label = "Hauptstraße"`
- **THEN** the system SHALL NOT apply duplicate grouping

### Requirement: Disambiguation fields on duplicate results
When a search result belongs to a duplicate group, the result item SHALL display additional fields to distinguish it from other group members.

#### Scenario: Name shown for duplicate
- **WHEN** a result is in a duplicate group
- **AND** the result has `name = "Aldi"`
- **THEN** the result item SHALL display "Aldi" as a distinguishing detail

#### Scenario: Object type shown for duplicate
- **WHEN** a result is in a duplicate group
- **AND** the result has `objectTypeName = "restaurant"`
- **THEN** the result item SHALL display "restaurant" as a distinguishing detail

#### Scenario: Postal area shown for duplicate
- **WHEN** a result is in a duplicate group
- **AND** the result has `postalArea = "44139"`
- **THEN** the result item SHALL display "44139" as a distinguishing detail

#### Scenario: Region tail shown for duplicate
- **WHEN** a result is in a duplicate group
- **AND** the result has `region = ["Dortmund", "NRW", "DE"]`
- **THEN** the result item SHALL display the most specific region component ("Dortmund") as a distinguishing detail

#### Scenario: Multiple fields combined
- **WHEN** a result is in a duplicate group
- **AND** the result has `name = "Aldi"`, `objectTypeName = "restaurant"`, `postalArea = "44139"`, and `region = ["Dortmund", "NRW", "DE"]`
- **THEN** the result item SHALL display all available distinguishing fields, formatted as: "Aldi · restaurant · 44139 · Dortmund"

### Requirement: Single-result items unchanged
Search results that are NOT part of a duplicate group SHALL display exactly as before (label + adminRegionHierarchy only).

#### Scenario: Single result no extra fields
- **WHEN** search returns a single result with unique label
- **THEN** the result item SHALL show only `label` and `adminRegionHierarchy`
- **AND** SHALL NOT show `objectTypeName`, `postalArea`, or `region` details

### Requirement: Disambiguation fields use existing data only
All disambiguation fields SHALL be sourced from the existing `LocationEntry` fields — no new JNI or native code.

#### Scenario: No new native calls
- **WHEN** a search result is displayed with disambiguation fields
- **THEN** all displayed data SHALL come from `LocationEntry.name`, `LocationEntry.label`, `LocationEntry.objectTypeName`, `LocationEntry.postalArea`, and `LocationEntry.region`
- **AND** no new `OSMScoutClient` methods SHALL be added
