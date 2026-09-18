# location-options-ui Specification

## Purpose

Provides a location options button on the map screen that opens a full-width Material 3 bottom sheet for toggling map behaviour: follow mode, orientation, and auto-zoom.

## Requirements

### Requirement: Location options button

The system SHALL display a location options button on the map screen. The button SHALL be positioned near the zoom controls (bottom area of the screen). Tapping the button SHALL open a full-width Material 3 bottom sheet.

#### Scenario: Button visible on map screen

- **WHEN** the map screen is displayed
- **THEN** a location options button SHALL be visible in the bottom area of the screen
- **THEN** the button SHALL be rendered on top of the map canvas

#### Scenario: Button opens bottom sheet

- **WHEN** the user taps the location options button
- **THEN** a full-width Material 3 bottom sheet SHALL slide up from the bottom of the screen
- **THEN** the bottom sheet SHALL contain at least one toggle control

### Requirement: Map follows position toggle

The options bottom sheet SHALL contain a toggle switch labeled "Map follows position". When enabled, the map viewport SHALL automatically re-center on the user's GPS position each time a location update is received. When disabled, the map SHALL remain at its current viewport regardless of GPS updates.

#### Scenario: Follow mode centers map on GPS

- **WHEN** "Map follows position" is enabled
- **WHEN** a new GPS location is received
- **THEN** the map center SHALL update to the new GPS position
- **THEN** the map SHALL re-render at the new center

#### Scenario: Follow mode disengages on manual pan

- **WHEN** "Map follows position" is enabled
- **WHEN** the user manually pans the map
- **THEN** follow mode SHALL disengage (toggle turns off)
- **THEN** the map SHALL stay at the panned position

#### Scenario: Follow mode disengages on manual zoom

- **WHEN** "Map follows position" is enabled
- **WHEN** the user manually zooms the map
- **THEN** follow mode SHALL disengage (toggle turns off)
- **THEN** the map SHALL stay at the zoomed viewport

#### Scenario: Follow mode re-enabled from bottom sheet

- **WHEN** follow mode is off
- **WHEN** the user taps the toggle in the options bottom sheet
- **THEN** follow mode SHALL activate
- **THEN** the map SHALL immediately center on the current GPS position

#### Scenario: Follow mode state persists across sheet open/close

- **WHEN** the user opens the options bottom sheet and toggles follow mode
- **WHEN** the user closes and re-opens the bottom sheet
- **THEN** the toggle SHALL reflect the current follow mode state

### Requirement: Orientation controls in bottom sheet

The options bottom sheet SHALL display orientation controls for the current map mode. When in free-form mode, the sheet SHALL show the free-form orientation setting. When in navigation mode, the sheet SHALL show the navigation orientation setting.

- The orientation control SHALL be a pair of radio buttons or segmented buttons: "North up" and "Follow direction"
- Only one option SHALL be selectable at a time
- Changing the orientation SHALL take effect immediately

#### Scenario: Free-form orientation controls visible

- **WHEN** the user is in free-form mode
- **WHEN** the options bottom sheet is open
- **THEN** the sheet SHALL display "North up" and "Follow direction" options
- **THEN** the currently active option SHALL be visually selected

#### Scenario: Navigation orientation controls visible

- **WHEN** navigation is active
- **WHEN** the options bottom sheet is open
- **THEN** the sheet SHALL display "North up" and "Follow direction" options
- **THEN** the currently active option SHALL be visually selected

#### Scenario: Changing orientation takes effect immediately

- **WHEN** the options bottom sheet is open
- **WHEN** the user selects "Follow direction"
- **THEN** the map SHALL immediately rotate to the current bearing
- **WHEN** the user selects "North up"
- **THEN** the map SHALL immediately snap to 0° rotation

### Requirement: Auto-zoom toggle in bottom sheet

The options bottom sheet SHALL contain an "Auto zoom" toggle that is visible only during active navigation.

#### Scenario: Auto-zoom visible during navigation

- **WHEN** navigation is active
- **WHEN** the options bottom sheet is open
- **THEN** an "Auto zoom" toggle SHALL be visible in the sheet

#### Scenario: Auto-zoom hidden in free-form mode

- **WHEN** the user is in free-form mode
- **WHEN** the options bottom sheet is open
- **THEN** the "Auto zoom" toggle SHALL NOT be visible

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

### Requirement: Vehicle anchor position controls

The location options bottom sheet SHALL contain a "Vehicle position" control with an entry for routing and one for free driving. Selecting an entry SHALL open a 5×3 position picker (horizontal 10/30/50/70/90% of the screen width, vertical 10/50/90% of the screen height, 15 presets total) with the currently configured anchor marked. The control labels SHALL match the Android Auto settings dialog labels, and changes SHALL persist globally so both surfaces share the same value.

#### Scenario: Anchor rows visible in the sheet

- **WHEN** the location options bottom sheet is open
- **THEN** it shows a "Vehicle position" row for routing and one for free driving
- **AND** each row displays the currently configured anchor

#### Scenario: Picker offers the 5×3 grid

- **WHEN** the user selects a vehicle position row
- **THEN** a position picker opens offering the 15 presets of the 5×3 grid
- **AND** the currently configured anchor is marked

#### Scenario: Selection persists globally

- **WHEN** the user picks an anchor position in the picker
- **THEN** the change persists through the shared settings storage
- **AND** the new anchor applies to vehicle positioning on the phone and on Android Auto

#### Scenario: Picker labels match Android Auto

- **WHEN** the user compares the phone anchor picker with the Android Auto anchor picker
- **THEN** the two pickers use the same position labels and hierarchy
