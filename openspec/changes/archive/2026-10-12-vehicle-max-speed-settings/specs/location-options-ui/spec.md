# Spec Delta

## ADDED Requirements

### Requirement: Vehicle maximum speed sliders in the options sheet

The options bottom sheet SHALL contain one maximum speed slider per routing vehicle — car, bicycle and
pedestrian — each showing its current value in km/h. It SHALL be visible in every map state, next to the
overspeed warning control.

#### Scenario: Three sliders are offered

- **WHEN** the options bottom sheet is open
- **THEN** one maximum speed slider is shown for the car, one for the bicycle and one for the pedestrian
- **AND** each shows its current value in km/h

#### Scenario: Values are readable from the sheet

- **WHEN** the options bottom sheet is open on a fresh install
- **THEN** the car slider shows 120 km/h
- **AND** the bicycle slider shows 15 km/h
- **AND** the pedestrian slider shows 5 km/h

#### Scenario: Controls are reachable in every map state

- **WHEN** the map state is BROWSE, FREE_DRIVE or NAVIGATION
- **AND** the options bottom sheet is opened from that state
- **THEN** the three maximum speed sliders SHALL be present and operable

#### Scenario: Each slider covers its vehicle's full range

- **WHEN** the options bottom sheet is open
- **THEN** the car slider offers every whole value from 30 to 160 km/h
- **AND** the bicycle slider offers every whole value from 5 to 40 km/h
- **AND** the pedestrian slider offers every whole value from 2 to 15 km/h

### Requirement: A slider change persists immediately

A slider change SHALL be written to the shared settings storage as the user moves it, and the sheet
SHALL show the changed value.

#### Scenario: A slider change persists

- **WHEN** the user moves the bicycle slider to 20 km/h
- **THEN** the value is persisted in the shared settings storage
- **AND** reopening the sheet shows 20 km/h

#### Scenario: A slider change survives a restart

- **WHEN** the user moves the car slider to 90 km/h and the app is restarted
- **THEN** the car slider shows 90 km/h

### Requirement: The car exposes no maximum speed control

Android Auto and AAOS SHALL NOT offer an edit control for a vehicle maximum speed; the car SHALL read the
value the phone stores. The sheet is a phone surface, so this is a deliberate deviation from cross-variant
UI parity (`guidelines/UI.md` §1) rather than an omission.

#### Scenario: No car-side editor

- **WHEN** the car settings or preferences surface is opened
- **THEN** it SHALL NOT contain a maximum speed control
- **AND** car routing still uses the phone's stored car value
