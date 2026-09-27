## MODIFIED Requirements

### Requirement: Re-center button appears when follow mode is inactive or auto-zoom suspended

The system SHALL display a re-center button on the map overlay when the viewport's automatic framing is suspended in a driving mode and a GPS fix is available: in FREE_DRIVE when a manual pan, zoom or rotate has suspended the drive preset, or during NAVIGATION when auto-zoom is suspended (follow mode still on) or follow mode has been disengaged by a manual pan. BROWSE mode is NOT covered by this requirement — its visibility rule is defined by `map-modes` ("Browse re-center"). When no GPS fix is available the button SHALL be hidden in every mode.

#### Scenario: Follow mode disengaged by pan

- **WHEN** the map mode is FREE_DRIVE
- **AND** the user pans the map
- **THEN** the drive preset is suspended (follow and auto-zoom off)
- **AND** the re-center button appears on the map

#### Scenario: Follow mode disengaged by zoom

- **WHEN** the map mode is FREE_DRIVE
- **AND** the user zooms the map
- **THEN** the drive preset is suspended
- **AND** the re-center button appears on the map

#### Scenario: Manual rotate suspends the free-drive preset

- **WHEN** the map mode is FREE_DRIVE
- **AND** the user rotates the map
- **THEN** the drive preset is suspended
- **AND** the re-center button appears on the map

#### Scenario: No GPS fix

- **WHEN** the automatic framing is suspended in a driving mode and no GPS fix is available
- **THEN** the re-center button is hidden

#### Scenario: Follow mode re-enabled via button

- **WHEN** the map mode is FREE_DRIVE, the drive preset is suspended, and the user taps the re-center button
- **THEN** follow mode is re-enabled
- **AND** auto-zoom is re-enabled
- **AND** the orientation returns to heading-up with the speed-based driving zoom
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
