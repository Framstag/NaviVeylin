## MODIFIED Requirements

### Requirement: Speed-limit indicator during navigation

The system SHALL display the current speed and the current speed limit on the navigation display in the right visualisation region: a speed badge with the current speed, the speed limit as a round sign below the badge, and a warning state consisting of a red badge background with white text when the current speed equals or exceeds the speed limit plus the overspeed warning delta (`current >= max + delta`). The delta is the single global setting shared with the phone app: a whole number of km/h between 0 and 30 inclusive, default 5, precise to 1 km/h, and it applies identically on Android Auto and on the phone. The round speed-limit sign SHALL be at least 56dp in diameter with a red ring of at least 7dp and digits of at least 24sp bold.

#### Scenario: Limit badge shown during navigation

- **WHEN** navigation is active and current-speed data is available
- **THEN** the speed badge shows the current speed on the right side of the navigation display inside the stable region

#### Scenario: Speed-limit sign shown

- **WHEN** navigation is active and speed-limit data is available
- **THEN** the current speed limit is shown as a round sign below the speed badge

#### Scenario: Limit exceeded warning

- **WHEN** current speed equals or exceeds the displayed speed limit plus the configured overspeed warning delta
- **THEN** the speed badge shows a red background with white text (warning state)

#### Scenario: Warning below the delta

- **GIVEN** the overspeed warning delta is 5 km/h and the limit is 50 km/h
- **WHEN** the current speed is 54 km/h
- **THEN** the speed badge shows the normal colors (no warning state)

#### Scenario: Warning background keeps semi-transparency

- **WHEN** the speed badge is in the warning state
- **THEN** the red background retains the same semi-transparent alpha as the normal badge background

#### Scenario: No sign without a limit

- **WHEN** no speed-limit data is available
- **THEN** no speed-limit sign is drawn

#### Scenario: Sign at least 56dp

- **WHEN** the speed-limit sign is shown
- **THEN** the sign circle is at least 56dp in diameter with a red ring of at least 7dp

#### Scenario: Sign digits at least 24sp

- **WHEN** the speed-limit sign is shown
- **THEN** the digit text uses a font size of 24sp or larger with bold weight

### Requirement: Settings dialog reachable while driving

The system SHALL provide a settings action on the Android Auto map display that opens a settings dialog whose content mirrors the phone's location-options dialog: follow mode, browse orientation, navigation orientation, auto-zoom, dark mode, lane hints, render mode, and the overspeed warning delta. The dialog SHALL be reachable while the vehicle is moving.

#### Scenario: Settings dialog opens while driving

- **WHEN** the user taps the settings action on the map display while the vehicle is moving
- **THEN** a settings dialog opens without requiring the vehicle to be parked

#### Scenario: Settings content matches phone dialog

- **WHEN** the settings dialog is open
- **THEN** it presents the same settings as the phone's location-options dialog: follow mode, browse orientation, navigation orientation, auto-zoom, dark mode, lane hints, render mode, and the overspeed warning delta

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
