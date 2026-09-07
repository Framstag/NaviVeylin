# map-recenter-button — Delta for fix-route-refresh-and-auto-zoom-reengage

## MODIFIED Requirements

### Requirement: Re-center button appears when follow mode is inactive or auto-zoom suspended
The system SHALL display a re-center button on the map overlay when the viewport is no longer driven automatically and a GPS fix is available: specifically when follow mode is off, OR while navigating with auto-zoom suspended (follow mode still on).

#### Scenario: Follow mode disengaged by pan
- **WHEN** the user pans the map while follow mode is active
- **THEN** follow mode is suspended
- **AND** a re-center button appears on the map

#### Scenario: Follow mode disengaged by zoom
- **WHEN** the user zooms the map while follow mode is active
- **THEN** follow mode is suspended
- **AND** a re-center button appears on the map

#### Scenario: No GPS fix
- **WHEN** follow mode is off and no GPS fix is available
- **THEN** the re-center button is hidden

#### Scenario: Follow mode re-enabled via button
- **WHEN** the user taps the re-center button
- **THEN** follow mode is re-enabled
- **AND** the map re-centers on the current GPS position
- **AND** the re-center button is hidden

#### Scenario: Auto-zoom suspended while navigating (follow still on)
- **WHEN** the user zooms (pinch or button) during active navigation, which suspends auto-zoom while leaving follow mode on
- **THEN** the re-center button appears on the map
- **AND** tapping it re-enables follow mode and unsuspends auto-zoom
- **AND** the map re-centers on the current GPS position
- **AND** the button hides again

#### Scenario: Follow mode off during navigation
- **WHEN** the user pans during active navigation (follow mode disengaged)
- **THEN** the re-center button appears
- **AND** tapping it re-enables follow mode and auto zoom

#### Scenario: No suspension, following normally
- **WHEN** navigation is active, follow mode is on, and auto-zoom is active (not suspended)
- **THEN** the re-center button is hidden

### Requirement: Re-center button has a clear icon
The system SHALL use a recognizable crosshair or my-location icon for the re-center button.

#### Scenario: Icon visible
- **WHEN** the re-center button is displayed (any of the visibility scenarios above)
- **THEN** it shows a crosshair/my-location icon with a "Re-center on location" content description
