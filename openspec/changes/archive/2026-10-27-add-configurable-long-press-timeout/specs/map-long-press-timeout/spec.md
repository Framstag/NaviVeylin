# Spec Delta

## Purpose

Owns the contract between a configured long-press duration and the map canvas's long-press detection: the order in
which the map's timeout is resolved (the app's configured value, else the platform's), the persisted setting with
its default and range, and the consequence that a press shorter than the configured duration is not a long press.

## ADDED Requirements

### Requirement: The long-press timeout is a configuration, not a literal

The map canvas SHALL decide a long press against a configured duration, never against a literal compiled into the
gesture handler. The duration SHALL be the value the caller configures; when the caller configures none, it SHALL
be the platform's configured long-press timeout. A press held for less than the configured duration SHALL NOT be a
long press, and a press held past it SHALL be exactly one.

#### Scenario: The platform's configured timeout decides

- **WHEN** the platform's long-press timeout is configured to 900 ms and a finger is held on the map for 600 ms
- **THEN** no long press is delivered
- **AND** holding the same press past 900 ms delivers exactly one long press
- **Case**: `MapLongPressTimeoutComposeTest#longPressHonoursTheConfiguredPlatformTimeout`

#### Scenario: The app's configured timeout decides

- **WHEN** the map canvas is given a 900 ms long-press timeout and a finger is held on the map for 600 ms
- **THEN** no long press is delivered
- **AND** holding the same press past 900 ms delivers exactly one long press
- **Case**: `MapLongPressTimeoutComposeTest#longPressUsesTheConfiguredAppTimeout`

### Requirement: The long-press timeout is a persisted setting

The app SHALL persist the map's long-press duration in milliseconds. The default SHALL be 500 ms, the minimum
250 ms and the maximum 1000 ms (the options sheet offers them in 50 ms steps), and a value outside that range
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

- **WHEN** the long-press timeout is set to 900 ms from the options sheet
- **THEN** the map's canvas state carries 900 ms at once
- **AND** a fresh view model over the same settings file carries 900 ms
- **Case**: `MapCanvasViewModelLongPressTimeoutTest#longPressTimeoutAppliesAndPersists`
