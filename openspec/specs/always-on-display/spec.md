# always-on-display Specification

## Purpose

Keeps the device screen on during active turn-by-turn navigation so the driver can see guidance without tapping the screen, with a persistent user toggle to disable the behavior.

## Requirements

### Requirement: Screen stays on while app is in the foreground

The system SHALL keep the device screen on while the app is in the foreground and the "Keep screen on" setting is enabled. The screen SHALL be allowed to turn off when the app is not in the foreground (e.g., backgrounded by switching to another app), or when the setting is disabled. The keep-screen-on state SHALL be re-applied whenever the app returns to the foreground.

- The keep-screen-on flag SHALL be active for the whole time the app is resumed, not only during turn-by-turn navigation
- Returning from another app (or from the lock screen) SHALL restore keep-screen-on without any user action
- Going to the background SHALL release keep-screen-on so normal power management resumes
- Disabling the setting SHALL release keep-screen-on immediately

#### Scenario: Screen stays on while app is running

- **WHEN** the app is in the foreground with the map visible (no active turn-by-turn navigation)
- **AND** the "Keep screen on" setting is enabled (default)
- **THEN** the device screen SHALL remain on and SHALL NOT time out or dim due to inactivity

#### Scenario: Screen stays on after switching back from another app

- **WHEN** the app is in the foreground with keep-screen-on active
- **WHEN** the user switches to another app and then back to NaviVeylin
- **THEN** the device screen SHALL remain on after returning
- **THEN** keep-screen-on SHALL be re-applied on resume without any user action

#### Scenario: Screen turns off when app goes to background

- **WHEN** keep-screen-on is active
- **WHEN** the user switches to another app (app goes to background)
- **THEN** the device screen SHALL return to normal power management behavior while the app is not visible
- **THEN** the screen MAY time out according to the device's system settings

#### Scenario: Screen turns off when setting is disabled while app is running

- **WHEN** the app is in the foreground
- **AND** the "Keep screen on" setting is enabled
- **WHEN** the user disables the "Keep screen on" toggle
- **THEN** the device screen SHALL return to normal power management behavior immediately
- **THEN** the screen MAY time out according to the device's system settings

#### Scenario: Screen stays on when setting is enabled while app is running

- **WHEN** the app is in the foreground
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
