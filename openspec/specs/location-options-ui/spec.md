# location-options-ui Specification

## Purpose

Provides a location options button on the map screen that opens a full-width Material 3 bottom sheet for toggling map behaviour: follow mode, orientation, and auto-zoom.

## Requirements

### Requirement: Location options button

The system SHALL display a location options button on the map screen. The button SHALL be positioned near the zoom controls (bottom area of the screen). Tapping the button SHALL open a full-width Material 3 quick settings sheet whose content is bounded by `settings-surface`.

#### Scenario: Button visible on map screen

- **WHEN** the map screen is displayed
- **THEN** a location options button SHALL be visible in the bottom area of the screen
- **THEN** the button SHALL be rendered on top of the map canvas
- **Case**: none — the quick-sheet rows exist, and their case is written when the sheet shrinks (tasks.md group 6)

#### Scenario: Button opens bottom sheet

- **WHEN** the user taps the location options button
- **THEN** a full-width Material 3 bottom sheet SHALL slide up from the bottom of the screen
- **THEN** the bottom sheet SHALL contain at least one toggle control
- **Case**: none — the quick-sheet rows exist, and their case is written when the sheet shrinks (tasks.md group 6)

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

### Requirement: The car exposes no maximum speed control

Android Auto and AAOS SHALL NOT offer an edit control for a vehicle maximum speed; the car SHALL read the
value the phone stores. The phone's settings surface is the only editor, so this is a deliberate deviation
from cross-variant UI parity (`guidelines/UI.md` §1) rather than an omission.

#### Scenario: No car-side editor

- **WHEN** the car settings or preferences surface is opened
- **THEN** it SHALL NOT contain a maximum speed control
- **AND** car routing still uses the phone's stored car value
- **Case**: none — the quick-sheet rows exist, and their case is written when the sheet shrinks (tasks.md group 6)
