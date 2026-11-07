# Spec Delta — auto/preferences

## ADDED Requirements

### Requirement: The car offers the announcement switches

The car preferences screen SHALL offer two additional rows: the car's own voice-guidance switch and the
shared GPS-announcement switch. Each row SHALL show the current value, and selecting it SHALL flip and
persist that value in the shared settings storage.

#### Scenario: Both rows are shown with their values

- **WHEN** the Preferences screen is displayed
- **THEN** it shows a "Voice guidance" row with the car's own value and a "GPS announcements" row with
  the shared value, each as On or Off
- **Case**: `PreferencesScreenMapperTest#announcementRowsCarryTheirValues`

#### Scenario: The car voice row flips only the car's value

- **WHEN** the driver selects the "Voice guidance" row
- **THEN** the car's voice switch flips and is persisted, and the phone's voice switch keeps its value
- **Case**: `PreferencesScreenMapperTest#theVoiceRowFlipsOnlyTheCarValue`

#### Scenario: The GPS row flips the shared value

- **WHEN** the driver selects the "GPS announcements" row
- **THEN** the shared GPS switch flips and is persisted, so the phone reads the new value
- **Case**: `PreferencesScreenMapperTest#theGpsRowFlipsTheSharedValue`

#### Scenario: The phone's own switch is not editable from the car

- **WHEN** the Preferences screen is displayed
- **THEN** no row edits the phone's voice switch, because that switch is not one this surface speaks by
- **Case**: `PreferencesScreenMapperTest#thePhoneVoiceValueHasNoCarRow`
