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

### Requirement: Orientation controls in bottom sheet

The options bottom sheet SHALL display orientation controls for the current map mode. When in free-form mode, the sheet SHALL show the free-form orientation setting. When in navigation mode, the sheet SHALL show the navigation orientation setting.

- The orientation control SHALL be a pair of radio buttons or segmented buttons: "North up" and "Follow direction"
- Only one option SHALL be selectable at a time
- Changing the orientation SHALL take effect immediately

- In BROWSE, the orientation control SHALL be a pair of radio buttons or segmented buttons: "North up" and "Free rotation"
- In FREE_DRIVE or NAVIGATION, the orientation control SHALL be a pair of radio buttons or segmented buttons: "Follow direction" and "North up"
- Only one option SHALL be selectable at a time
- Changing the orientation SHALL take effect immediately

#### Scenario: Free-form orientation controls visible

- **WHEN** the user is in free-form mode
- **WHEN** the options bottom sheet is open
- **THEN** the sheet SHALL display "North up" and "Follow direction" options
- **THEN** the currently active option SHALL be visually selected
- **WHEN** the map state is BROWSE

#### Scenario: Navigation orientation controls visible

- **WHEN** navigation is active
- **WHEN** the options bottom sheet is open
- **THEN** the sheet SHALL display "North up" and "Follow direction" options
- **THEN** the currently active option SHALL be visually selected
- **WHEN** the map state is FREE_DRIVE or NAVIGATION

#### Scenario: Changing orientation takes effect immediately

- **WHEN** the options bottom sheet is open
- **WHEN** the user selects "Follow direction"
- **THEN** the map SHALL immediately rotate to the current bearing
- **WHEN** the user selects "North up"
- **THEN** the map SHALL immediately snap to 0° rotation
- **WHEN** the user selects "Follow direction" (driving state) or "Free rotation" (browse state)

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

#### Scenario: Auto-zoom visible in free drive

- **WHEN** the map state is FREE_DRIVE
- **WHEN** the options bottom sheet is open
- **THEN** an "Auto zoom" toggle SHALL be visible in the sheet

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
- **AND** each picker marks the value configured for its own surface

#### Scenario: Rows work in every mode, including navigation

- **WHEN** the map is in navigation mode and the user opens the location options sheet
- **THEN** the "Vehicle position" rows SHALL be wired to the phone's anchor state
- **AND** picking a preset SHALL change the applied anchor and update the row label immediately

#### Scenario: Selection persists for the phone

- **WHEN** the user picks an anchor position in the picker
- **THEN** the change persists through the shared settings storage as the phone's value
- **AND** the new anchor applies to vehicle positioning on the phone
- **AND** the Android Auto anchor value is left unchanged

### Requirement: Mode header without mode switch

The options bottom sheet SHALL display a header naming the current map state ("Browse", "Free drive", or "Navigation"). The sheet SHALL NOT contain any control that switches the map mode — mode switching is performed exclusively through the right-column mode toggle button.

#### Scenario: Sheet header shows current state

- **WHEN** the options bottom sheet is open
- **THEN** the sheet SHALL display a header naming the current map state

#### Scenario: No mode switch in sheet

- **WHEN** the options bottom sheet is open
- **THEN** the sheet SHALL NOT contain a control that changes the map mode

### Requirement: Sheet reachable during navigation

The location options button SHALL be available while the map state is NAVIGATION, so the driving configuration (auto-zoom, orientation) can be adjusted mid-route.

#### Scenario: Location options open during navigation

- **WHEN** the map state is NAVIGATION
- **THEN** the location options button SHALL be visible in the navigation right-side widget column
- **AND** tapping it SHALL open the options bottom sheet

#### Scenario: Driving options adjustable mid-route

- **WHEN** the map state is NAVIGATION
- **AND** the options bottom sheet is open
- **THEN** the user SHALL be able to change the auto-zoom and orientation settings
- **AND** the changes SHALL take effect immediately

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
