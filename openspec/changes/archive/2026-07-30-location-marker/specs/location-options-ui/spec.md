## Purpose

Provides a location options button on the map screen that opens a small dialog for toggling visual and behavioural aspects of the GPS location marker, starting with the "map follows position" follow-mode feature.

## ADDED Requirements

### Requirement: Location options button

The system SHALL display a small location options button on the map screen. The button SHALL be positioned near the zoom controls (bottom area of the screen).

#### Scenario: Button visible on map screen

- **WHEN** the map screen is displayed
- **THEN** a location options button SHALL be visible in the bottom area of the screen
- **THEN** the button SHALL be rendered on top of the map canvas

#### Scenario: Button opens options dialog

- **WHEN** the user taps the location options button
- **THEN** a small dialog/popup SHALL appear near the button
- **THEN** the dialog SHALL contain at least one toggle control

### Requirement: Map follows position toggle

The options dialog SHALL contain a toggle switch labeled "Map follows position". When enabled, the map viewport SHALL automatically re-center on the user's GPS position each time a location update is received. When disabled, the map SHALL remain at its current viewport regardless of GPS updates.

#### Scenario: Follow mode centers map on GPS

- **WHEN** "Map follows position" is enabled
- **WHEN** a new GPS location is received
- **THEN** the map center SHALL update to the new GPS position
- **THEN** the map SHALL re-render at the new center

#### Scenario: Follow mode disengages on manual pan

- **WHEN** "Map follows position" is enabled
- **WHEN** the user manually pans the map
- **THEN** follow mode SHALL disengage (toggle turns off)
- **THEN** the map SHALL stay at the panned position

#### Scenario: Follow mode disengages on manual zoom

- **WHEN** "Map follows position" is enabled
- **WHEN** the user manually zooms the map
- **THEN** follow mode SHALL disengage (toggle turns off)
- **THEN** the map SHALL stay at the zoomed viewport

#### Scenario: Follow mode re-enabled from dialog

- **WHEN** follow mode is off
- **WHEN** the user taps the toggle in the options dialog
- **THEN** follow mode SHALL activate
- **THEN** the map SHALL immediately center on the current GPS position

#### Scenario: Follow mode state persists across dialog open/close

- **WHEN** the user opens the options dialog and toggles follow mode
- **WHEN** the user closes and re-opens the dialog
- **THEN** the toggle SHALL reflect the current follow mode state
