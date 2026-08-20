## Purpose

Lets drivers view and change shared navigation preferences from the Android Auto screen, persisted to the same settings storage the phone app uses.

## ADDED Requirements

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
