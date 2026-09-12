# share/location-receiving Specification

## Purpose
The phone app receives shared geo information (a raw coordinate or an address) from other applications and turns it into a map interaction: a raw coordinate is disambiguated through the candidate picker, an address triggers search, and the result opens the standard details flow with Show / Fav / Route actions.

## Requirements

### Requirement: Shared location received while app running
The system SHALL process a shared geo intent that arrives while the phone app is already running, without losing the request.

#### Scenario: Share while app in foreground
- **WHEN** the user shares a location to NaviVeylin while the app is running
- **THEN** the app processes the shared location without restarting the activity

#### Scenario: Share while app in background
- **WHEN** the user shares a location to NaviVeylin while the app is in the background
- **THEN** the app brings the map to the foreground and processes the shared location

#### Scenario: Share before any map is loaded
- **WHEN** the user shares a location while no map is installed (the app shows the map-manager entry screen)
- **THEN** the shared location is kept pending and processed when the map screen becomes available

#### Scenario: Multiple shares in sequence
- **WHEN** the user shares a second location before the first is resolved
- **THEN** the second share replaces the first as the pending request

### Requirement: Shared coordinate disambiguated through candidate picker
The system SHALL present the objects near a shared coordinate in the candidate picker so the user can choose what was meant.

#### Scenario: Candidates found at shared coordinate
- **WHEN** the shared input yields a coordinate and the map database contains objects near it
- **THEN** the candidate picker lists the objects at that coordinate, ranked by the map database

#### Scenario: Candidate selected
- **WHEN** the user selects a candidate from the picker
- **THEN** the details sheet opens for that object with Show / Fav / Route actions

#### Scenario: No candidates at shared coordinate
- **WHEN** the shared input yields a coordinate and the map database contains no objects near it
- **THEN** the details sheet opens directly for the raw coordinate, labeled with the share subject when present, otherwise with the coordinate pair

### Requirement: Shared address triggers search
The system SHALL run the existing location search when the shared input contains no coordinate.

#### Scenario: Address text shared
- **WHEN** the shared input is an address or place name without coordinates
- **THEN** the app runs a location search for the text and shows the search results

#### Scenario: Unparseable shared text
- **WHEN** the shared input cannot be parsed as a coordinate or a known map URL
- **THEN** the app runs a location search for the raw text

### Requirement: Map centers on shared location
The system SHALL center the map on the shared coordinate so the user sees the location immediately.

#### Scenario: Map centers on shared coordinate
- **WHEN** the app processes a shared coordinate
- **THEN** the map viewport centers on that coordinate
