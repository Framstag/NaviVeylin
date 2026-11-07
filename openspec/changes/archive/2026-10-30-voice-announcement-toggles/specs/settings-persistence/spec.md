# Spec Delta — settings-persistence

## ADDED Requirements

### Requirement: The announcement fields declare per-surface and shared ownership

A setting that governs one surface SHALL have one stored field per surface, and a write from a surface
SHALL change only the field it owns. A setting that governs both surfaces SHALL have one stored field,
so a write from either surface changes the single value the other reads.

#### Scenario: A car write leaves the phone's voice value alone

- **WHEN** the car saves its voice switch while the phone's voice switch has a non-default value
- **THEN** the phone's voice value keeps its value after the write
- **Case**: `SettingsStorageVoiceSwitchTest#aCarWriteLeavesThePhoneVoiceValueAlone`

#### Scenario: The shared GPS value is one field

- **WHEN** the phone saves the GPS switch and the car saves it afterwards
- **THEN** the second write applies to the first one's result and the persisted file holds one GPS value
- **Case**: `SettingsStorageVoiceSwitchTest#theSharedGpsValueIsOneField`

#### Scenario: A settings file written before the fields existed decodes to the defaults

- **WHEN** settings persisted without the announcement fields are loaded
- **THEN** both voice switches read on and the shared GPS switch reads off, with no error and no
  migration step
- **Case**: `SettingsStorageVoiceSwitchTest#olderSettingsDecodeToTheDefaults`
