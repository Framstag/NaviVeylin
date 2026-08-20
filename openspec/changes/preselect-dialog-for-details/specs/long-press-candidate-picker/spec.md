## Purpose

Lets users choose which overlapping object at a long-pressed map coordinate to inspect, by presenting the candidate objects in the same description format used in search results.

## ADDED Requirements

### Requirement: Long press shows candidate list

The system SHALL display a candidate picker dialog listing all reasonable objects found at the long-pressed coordinate, formatted like search results.

#### Scenario: Multiple candidates at long-pressed location
- **GIVEN** the map is loaded with OSM data
- **WHEN** the user long-presses at a location with multiple candidate objects that have description data
- **THEN** the system SHALL show a candidate picker dialog
- **AND** the dialog SHALL list each candidate as a selectable entry

#### Scenario: Candidate entries use search result description format
- **GIVEN** a candidate picker dialog is shown
- **WHEN** the dialog renders the candidate entries
- **THEN** each entry SHALL use the same description format as entries in search results
- **AND** each entry SHALL show the object's name and description as presented in search results

#### Scenario: Candidates ordered by ranking
- **GIVEN** a candidate picker dialog is shown with multiple candidates
- **WHEN** the dialog lists the candidates
- **THEN** the entries SHALL appear in ranking order (has description data, visible at current zoom, proximity to the pressed coordinate)

### Requirement: User selects a candidate

The system SHALL open the details for the object the user selects from the candidate list.

#### Scenario: Selecting a candidate opens its details
- **GIVEN** a candidate picker dialog is shown
- **WHEN** the user selects one candidate entry
- **THEN** the candidate picker SHALL close
- **AND** the system SHALL open the details sheet for the selected object
- **AND** the details sheet SHALL display the full `ObjectDescription` of the selected object

#### Scenario: Marker placed at object center
- **WHEN** a candidate is selected and its ObjectDescription has valid coordinates
- **THEN** the marker SHALL be rendered at `objectLat`/`objectLon` from the description
- **AND** the map SHALL center on those coordinates

#### Scenario: Fallback to press point
- **WHEN** the selected candidate's ObjectDescription has NaN coordinates
- **THEN** the marker SHALL be rendered at the original press point

### Requirement: Close candidate picker without selection

The system SHALL allow dismissing the candidate picker without showing any details.

#### Scenario: Dismiss closes picker
- **GIVEN** the candidate picker dialog is open
- **WHEN** the user dismisses it (tap outside or back)
- **THEN** the dialog SHALL close
- **AND** no details SHALL be shown
