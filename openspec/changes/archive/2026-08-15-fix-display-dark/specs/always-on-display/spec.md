# always-on-display Specification (Delta)

## MODIFIED Requirements

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

## UNCHANGED Requirements

The following requirements of `always-on-display` are unchanged: the "Keep screen on" setting SHALL be persisted to device storage and survive app restart; the setting SHALL default to enabled on first launch; the location options bottom sheet SHALL display the "Keep screen on" toggle at all times and reflect its current state.
