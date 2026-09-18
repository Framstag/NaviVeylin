## MODIFIED Requirements

### Requirement: Settings dialog reachable while driving

The system SHALL provide a settings action on the Android Auto map display that opens a settings dialog whose content mirrors the phone's location-options dialog: follow mode, browse orientation, navigation orientation, auto-zoom, dark mode, lane hints, render mode, the overspeed warning delta, and the vehicle anchor positions for routing and free driving. The dialog SHALL be reachable while the vehicle is moving.

#### Scenario: Settings dialog opens while driving

- **WHEN** the user taps the settings action on the map display while the vehicle is moving
- **THEN** a settings dialog opens without requiring the vehicle to be parked

#### Scenario: Settings content matches phone dialog

- **WHEN** the settings dialog is open
- **THEN** it presents the same settings as the phone's location-options dialog: follow mode, browse orientation, navigation orientation, auto-zoom, dark mode, lane hints, render mode, the overspeed warning delta, and the vehicle anchor positions

#### Scenario: Overspeed delta presented as a value picker

- **WHEN** the user selects the overspeed warning delta row in the settings dialog
- **THEN** a value picker opens offering every whole km/h value from 0 to 30 inclusive
- **AND** the currently configured delta is marked

#### Scenario: Delta selection persisted globally

- **WHEN** the user picks a delta value in the picker
- **THEN** the change persists through the shared settings storage
- **AND** the new value applies to the speed badge on Android Auto and on the phone

#### Scenario: Setting changes apply to map

- **WHEN** the user changes a setting in the dialog
- **THEN** the change takes effect on the map display immediately and persists for future sessions

#### Scenario: Keep-screen-on remains phone-only

- **WHEN** the settings dialog is open
- **THEN** it does not expose the phone-only keep-screen-on option

#### Scenario: Anchor rows shown for both modes

- **WHEN** the settings dialog is open
- **THEN** it shows a "Vehicle position" row for routing and one for free driving
- **AND** each row displays the currently configured anchor

#### Scenario: Anchor picker shows the 5×3 grid

- **WHEN** the user selects a vehicle position row in the settings dialog
- **THEN** a position picker opens offering the 15 presets of the 5×3 grid (horizontal 10/30/50/70/90% of the surface width, vertical 10/50/90% of the surface height)
- **AND** the currently configured anchor is marked

#### Scenario: Anchor selection persisted globally

- **WHEN** the driver picks an anchor position in the picker
- **THEN** the change persists through the shared settings storage
- **AND** the new anchor applies to the vehicle positioning on Android Auto and on the phone
