## REMOVED Requirements

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

**Reason**: Follow mode is now part of the explicit mode model (`map-modes` capability). The map follows position only in FREE_DRIVE and NAVIGATION modes, entered via the right-column mode toggle button — not via a config toggle. A config switch for follow mode would duplicate the mode control and allow inconsistent states (follow on in BROWSE).

**Migration**: Use the right-column mode toggle button to enter FREE_DRIVE (follow on, auto-zoom on, heading-up). The follow behavior is part of the FREE_DRIVE preset; the re-center button restores it when suspended.

## MODIFIED Requirements

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

## ADDED Requirements

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
