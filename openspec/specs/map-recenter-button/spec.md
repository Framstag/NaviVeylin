# map-recenter-button

## Purpose

Lets users re-enable follow-location mode with a single tap after manual pan or zoom disengages it, without opening the settings sheet.

## Requirements

### Requirement: Re-center button appears when follow mode is inactive
The system SHALL display a re-center button on the map overlay when follow mode is off and a GPS fix is available.

#### Scenario: Follow mode disengaged by pan
- **WHEN** the user pans the map while follow mode is active
- **THEN** follow mode is suspended
- **AND** a re-center button appears on the right side of the map

#### Scenario: Follow mode disengaged by zoom
- **WHEN** the user zooms the map while follow mode is active
- **THEN** follow mode is suspended
- **AND** a re-center button appears on the right side of the map

#### Scenario: No GPS fix
- **WHEN** follow mode is off and no GPS fix is available
- **THEN** the re-center button is hidden

#### Scenario: Follow mode re-enabled via button
- **WHEN** the user taps the re-center button
- **THEN** follow mode is re-enabled
- **AND** the map re-centers on the current GPS position
- **AND** the re-center button is hidden

### Requirement: Re-center button has a clear icon
The system SHALL use a recognizable crosshair or my-location icon for the re-center button.

#### Scenario: Icon visible
- **WHEN** the re-center button is displayed
- **THEN** it shows a crosshair/my-location icon with a "Re-center" content description
