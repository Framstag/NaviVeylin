# Spec Delta

## ADDED Requirements

### Requirement: Long-press timeout control

The location-options bottom sheet SHALL contain a long-press timeout control: a slider over the integer range
250–1000 ms in 50 ms steps, showing the current value in milliseconds. The control SHALL render in every mode and
persist every change to the shared settings storage, so the next long press on the map uses the new duration. The
phone SHALL be its only editor — the car surfaces have no long-press gesture and read nothing from this field.

#### Scenario: Slider shows the configured timeout and writes a change

- **WHEN** the sheet is opened with a configured long-press timeout of 500 ms and the slider is moved to 900 ms
- **THEN** the row shows the current value in milliseconds
- **AND** the sheet reports 900 ms to its caller, which persists it
- **Case**: `LocationOptionsOverlayComposeTest#longPressTimeoutSliderShowsAndWritesTheValue`

#### Scenario: The control renders in browse

- **WHEN** the map state is BROWSE and the options bottom sheet is opened
- **THEN** the long-press timeout slider SHALL be present and displayed
- **Case**: `LocationOptionsOverlayComposeTest#longPressTimeoutSliderIsReachableInBrowse`

#### Scenario: The control renders in free drive

- **WHEN** the map state is FREE_DRIVE and the options bottom sheet is opened
- **THEN** the long-press timeout slider SHALL be present and displayed
- **Case**: `LocationOptionsOverlayComposeTest#longPressTimeoutSliderIsReachableInFreeDrive`

#### Scenario: The control renders in navigation

- **WHEN** the map state is NAVIGATION and the options bottom sheet is opened
- **THEN** the long-press timeout slider SHALL be present and displayed
- **Case**: `LocationOptionsOverlayComposeTest#longPressTimeoutSliderIsReachableInNavigation`
