## ADDED Requirements

### Requirement: Overspeed warning delta control

The options bottom sheet SHALL contain an overspeed warning threshold control: a slider over the integer range 0–30 km/h with 1 km/h precision (no coarser steps), showing the current value. The setting is global — changing it SHALL persist to the shared settings storage, apply immediately to the speed widget's warning state, and SHALL be visible on Android Auto with the same value. The default SHALL be 5 km/h. A delta of 0 warns the moment the current speed reaches the limit.

#### Scenario: Slider offers 1 km/h precision

- **WHEN** the options bottom sheet is open
- **THEN** an overspeed warning control is shown with a current value displayed in km/h
- **AND** every whole value from 0 to 30 km/h can be selected

#### Scenario: Slider value applies to the widget

- **GIVEN** the overspeed warning delta is set to 3 km/h and the max speed is 50 km/h
- **WHEN** the current speed reaches 53 km/h
- **THEN** the speed widget shows the warning state

#### Scenario: Slider change persists globally

- **WHEN** the user moves the slider to a new value (e.g. 10 km/h)
- **THEN** the value is persisted in the shared settings storage
- **AND** the Android Auto speed badge uses the same new value
