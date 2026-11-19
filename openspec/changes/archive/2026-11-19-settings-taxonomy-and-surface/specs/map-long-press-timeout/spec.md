# Spec Delta — map-long-press-timeout

## MODIFIED Requirements

### Requirement: The long-press timeout is a persisted setting

The app SHALL persist the map's long-press duration in milliseconds. The default SHALL be 500 ms, the minimum
250 ms and the maximum 1000 ms (the settings surface offers them in 50 ms steps), and a value outside that range
SHALL be clamped to it rather than
installed. A settings file written before this field existed SHALL decode to the default rather than fail or clear.

#### Scenario: A settings file from before this change decodes to the default

- **WHEN** a settings file that contains no long-press timeout field is loaded
- **THEN** loading succeeds
- **AND** the long-press timeout is 500 ms
- **Case**: `AppSettingsTest#longPressTimeoutDefaultsOnAnOlderSettingsFile`

#### Scenario: The value round-trips through the settings file

- **WHEN** the long-press timeout is set to 900 ms and the settings JSON is written and read back
- **THEN** the loaded value is 900 ms
- **Case**: `AppSettingsTest#longPressTimeoutSurvivesJsonRoundTrip`

#### Scenario: A value outside the offered range is clamped

- **WHEN** the loaded settings carry a long-press timeout of 40 ms, or one of 4000 ms
- **THEN** the effective timeout is 250 ms, or 1000 ms respectively
- **Case**: `AppSettingsTest#longPressTimeoutOutsideTheRangeIsClamped`

#### Scenario: A change applies immediately and persists

- **WHEN** the long-press timeout is set to 900 ms from the settings surface
- **THEN** the map's canvas state carries 900 ms at once
- **AND** a fresh view model over the same settings file carries 900 ms
- **Case**: `MapCanvasViewModelLongPressTimeoutTest#longPressTimeoutAppliesAndPersists`
