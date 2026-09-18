# Android Auto Preferences (auto/preferences)

## Purpose

Lets drivers view and change shared navigation preferences from the Android Auto screen, persisted to the same settings storage the phone app uses.

## Requirements

### Requirement: Preferences screen accessible from root
The system SHALL provide a Preferences screen on Android Auto, reachable from the root screen, that shows the shared navigation preferences.

#### Scenario: Preferences entry on root screen
- **WHEN** the user is on the Android Auto root screen and not navigating
- **THEN** a "Preferences" option is available alongside Map, Search, Favorites, Diagnostics, and About

#### Scenario: Preferences screen opens
- **WHEN** the user selects "Preferences" from the root screen
- **THEN** a Preferences screen is displayed listing the shared navigation preferences

#### Scenario: Back returns to previous screen
- **WHEN** the user presses back on the Preferences screen
- **THEN** the car screen returns to the previous screen

### Requirement: Preferences displayed with current values
The system SHALL display each supported preference with its current value.

#### Scenario: Current values shown
- **WHEN** the Preferences screen is displayed
- **THEN** each supported preference row shows its current value (e.g., "Follow mode: On")

### Requirement: Preferences editable from car
The system SHALL let the driver change a preference from the car and persist the change.

#### Scenario: Toggle updates preference
- **WHEN** the driver selects a preference row
- **THEN** the preference value flips and the updated value is saved to shared settings storage

#### Scenario: Screen reflects saved value
- **WHEN** a preference is changed
- **THEN** the Preferences screen shows the new value

### Requirement: Preferences shared with phone app
The system SHALL persist preference changes to the same settings storage the phone app uses, so phone and car stay in sync.

#### Scenario: Car change visible on phone
- **WHEN** the driver changes a preference in the car
- **THEN** the phone app reads the updated value the next time it loads settings

#### Scenario: Phone change visible in car
- **WHEN** the phone app changes a preference
- **THEN** the Preferences screen shows the updated value the next time it loads settings

### Requirement: Phone-only preferences excluded
The system SHALL show only preferences that apply to car use.

#### Scenario: Keep-screen-on not shown
- **WHEN** the Preferences screen is displayed
- **THEN** the keep-screen-on preference is not shown

### Requirement: Settings screens never wedge on a loading placeholder
The system SHALL ensure the preferences screen and the picker screens opened from it (overspeed delta, vehicle position) never remain stuck on a loading placeholder: loaded content replaces the placeholder once available; a failed or dropped template refresh is recovered without user interaction; a settings load failure shows an explicit error with retry; and the screen re-renders its current state when the user returns to it.

#### Scenario: Content appears once loaded
- **WHEN** a settings screen opens and the settings load completes
- **THEN** the screen displays the preference rows with their current values instead of the loading placeholder

#### Scenario: Dropped template refresh is recovered
- **WHEN** the host fails or drops the first screen-refresh request
- **THEN** the screen automatically issues a further refresh and displays its content within a short recovery window, without any user interaction

#### Scenario: Settings load failure shows an error
- **WHEN** loading the settings fails on a settings screen
- **THEN** the screen shows an explicit error row offering retry, and the back navigation still works

#### Scenario: Screen re-renders on return
- **WHEN** the user leaves a settings screen and later returns to it
- **THEN** the screen renders its current state, not a stale loading placeholder

### Requirement: Dark mode preference applies to car rendering

When the driver sets the dark mode preference (**On**, **Off**, **Automatic**) on the car, the car's map and navigation presentation SHALL follow it: **On** renders dark presentation, **Off** renders light presentation, and **Automatic** follows the host day/night signal. A preference change or a host day/night change SHALL re-render the visible map without the driver leaving the screen or restarting the app.

#### Scenario: On forces dark

- **WHEN** the driver sets the dark mode preference to **On** and the host day/night signal is light
- **THEN** the car map and navigation screens render dark presentation

#### Scenario: Off forces light

- **WHEN** the driver sets the dark mode preference to **Off** and the host day/night signal is dark
- **THEN** the car map and navigation screens render light presentation

#### Scenario: Automatic follows host

- **WHEN** the driver sets the dark mode preference to **Automatic**
- **THEN** the car map and navigation screens follow the host day/night signal

#### Scenario: Preference change applies live

- **WHEN** the driver changes the dark mode preference on the car and returns to the map
- **THEN** the map renders the new presentation without restart

#### Scenario: Host night change applies live

- **WHEN** the dark mode preference is **Automatic** and the host day/night signal changes while the map or navigation screen is visible
- **THEN** the map re-renders the new presentation without user interaction
