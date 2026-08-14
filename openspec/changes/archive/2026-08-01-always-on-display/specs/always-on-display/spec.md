## Purpose

Keeps the device screen on during active turn-by-turn navigation so the driver can see guidance without tapping the screen, with a persistent user toggle to disable the behavior.

## ADDED Requirements

### Requirement: Screen stays on during navigation

The system SHALL keep the device screen on while turn-by-turn navigation is active and the "Keep screen on" setting is enabled. The screen SHALL be allowed to turn off when navigation is not active, or when the setting is disabled.

#### Scenario: Screen stays on when navigation starts

- **WHEN** navigation is started
- **AND** the "Keep screen on" setting is enabled (default)
- **THEN** the device screen SHALL remain on for the duration of navigation
- **THEN** the screen SHALL NOT time out or dim due to inactivity

#### Scenario: Screen turns off when navigation stops

- **WHEN** navigation is active
- **AND** the "Keep screen on" setting is enabled
- **WHEN** navigation is stopped
- **THEN** the device screen SHALL return to normal power management behavior
- **THEN** the screen MAY time out according to the device's system settings

#### Scenario: Screen turns off when setting is disabled during navigation

- **WHEN** navigation is active
- **AND** the "Keep screen on" setting is enabled
- **WHEN** the user disables the "Keep screen on" toggle
- **THEN** the device screen SHALL return to normal power management behavior immediately
- **THEN** the screen MAY time out according to the device's system settings

#### Scenario: Screen stays on when setting is enabled during navigation

- **WHEN** navigation is active
- **AND** the "Keep screen on" setting is disabled
- **WHEN** the user enables the "Keep screen on" toggle
- **THEN** the device screen SHALL stay on from that point forward

### Requirement: Keep screen on setting persisted

The "Keep screen on" setting SHALL be persisted to device storage so it survives app restart.

#### Scenario: Setting survives app restart

- **WHEN** the user enables the "Keep screen on" setting
- **WHEN** the app is killed and restarted
- **THEN** the "Keep screen on" setting SHALL still be enabled
- **THEN** the screen SHALL stay on during subsequent navigation sessions

#### Scenario: Default is enabled

- **WHEN** the app is launched for the first time
- **THEN** the "Keep screen on" setting SHALL default to enabled

### Requirement: Keep screen on toggle in location options

The location options bottom sheet SHALL display a "Keep screen on" toggle switch at all times.

#### Scenario: Toggle always visible

- **WHEN** the user opens the location options bottom sheet
- **THEN** a "Keep screen on" toggle SHALL be visible in the sheet

#### Scenario: Toggle reflects current state

- **WHEN** the user opens the location options bottom sheet
- **THEN** the "Keep screen on" toggle SHALL reflect the current setting state
- **WHEN** the user toggles the setting
- **WHEN** the user closes and re-opens the sheet
- **THEN** the toggle SHALL reflect the updated state
