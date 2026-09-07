# routing-summary Specification

## Purpose

Reusable route summary component showing the calculated route's key statistics (total distance, estimated duration) and its turn-by-turn step list, embeddable in the route panel or shown as an overlay.

## Requirements

### Requirement: Route summary component
The route summary SHALL be a reusable composable component (`RouteSummary`) that displays the total distance, the estimated travel duration, and a scrollable list of turn-by-turn steps for a calculated route.

#### Scenario: Distance and duration shown
- **WHEN** the route summary component is displayed
- **THEN** the total route distance SHALL be shown (e.g., "12.4 km")
- **AND** the estimated travel duration SHALL be shown (e.g., "1h 20min")

#### Scenario: Steps listed in order
- **WHEN** the route summary component is displayed
- **THEN** each step SHALL be listed in order from start to destination
- **AND** each step SHALL show a turn icon, the distance for the step, the time for the step, and the instruction text

#### Scenario: Step list scrollable
- **WHEN** the step list exceeds the visible area
- **THEN** the step list SHALL be scrollable

### Requirement: Active step highlighting
The route summary component SHALL visually highlight the current navigation step when an active step index is provided.

#### Scenario: Current step highlighted
- **WHEN** the route summary component is displayed with an active step index
- **THEN** the step at that index SHALL be visually highlighted (e.g., primary container background, bold text)

#### Scenario: No highlight without active index
- **WHEN** the route summary component is displayed without an active step index
- **THEN** no step SHALL be highlighted
