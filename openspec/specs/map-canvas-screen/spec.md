# Map Canvas Screen

## Purpose

Initial full-screen composable with empty map canvas placeholder, top-right overflow menu, and popup navigation to the map manager screen.

## Requirements

### Requirement: Full-screen map canvas placeholder
The system SHALL display a full-screen composable that fills the available space, showing a subtle grid pattern and centered placeholder text indicating where the map will render.

#### Scenario: Grid pattern visible on launch
- **WHEN** app launches and MainScreen is displayed
- **THEN** a subtle grid pattern fills the screen background
- **AND** centered text reads "Map will render here"

### Requirement: Top-right overflow menu
The system SHALL display the toaster menu button (hamburger icon) at the top-left corner of the screen, positioned below the status bar via system window insets. Tapping it SHALL open the animated Material 3 menu defined by the `map-menu` capability.

#### Scenario: Overflow menu opens popup
- **WHEN** the user taps the toaster menu button
- **THEN** the animated Material 3 map menu appears (fade/scale in)

#### Scenario: Menu button at top-left
- **WHEN** the map screen is displayed
- **THEN** the toaster menu button SHALL be positioned at the top-left of the screen below the status bar

### Requirement: Menu contains Download Maps entry
The system SHALL include a "Download Maps" entry in the overflow menu that navigates to the MapManagerScreen.

#### Scenario: Download Maps navigates to manager
- **WHEN** user taps "Download Maps" in the overflow menu
- **THEN** the app navigates to the MapManagerScreen route

### Requirement: Menu extensible for future settings
The system SHALL support adding future menu entries without structural changes.

#### Scenario: Placeholder entry exists
- **WHEN** overflow menu is open
- **THEN** a disabled or placeholder entry for "Settings" is visible (or the menu structure supports easy addition)

### Requirement: Top-right overlay column

The system SHALL split the portrait overlay controls into two columns: a left action column and a right view column. The left action column SHALL be positioned at the top-left below the status bar and SHALL contain, in order from top to bottom: menu button, search button, favorites button. The right view column SHALL be positioned at the bottom-right above the navigation bar and SHALL contain, in order from top to bottom: compass button, speed widget, location options button, and zoom controls — with the zoom controls at the bottom below all other controls. The re-center (MyLocation) button SHALL be positioned at the bottom-left when visible.

In landscape orientation, the system SHALL use the landscape layout arrangement defined by the `landscape-layout` capability instead.

#### Scenario: Portrait shows vertical column at top-right

- **WHEN** the device is in portrait orientation
- **THEN** the right view column SHALL show the compass button at the top
- **AND** the speed widget SHALL appear directly below the compass button
- **AND** the location options button SHALL appear below the speed widget
- **AND** the zoom controls SHALL appear at the bottom, below all other controls

#### Scenario: Portrait shows vertical column at bottom-right

- **WHEN** the device is in portrait orientation
- **THEN** the right view column SHALL be positioned at the bottom-right above the navigation bar
- **AND** the compass button SHALL be at the top of the column
- **AND** the zoom controls SHALL appear at the bottom, below all other controls

#### Scenario: Portrait shows action column at top-left

- **WHEN** the device is in portrait orientation
- **THEN** the left action column SHALL show the menu button at the top
- **AND** the search button SHALL appear directly below the menu button
- **AND** the favorites button SHALL appear directly below the search button

#### Scenario: Landscape uses landscape-layout arrangement

- **WHEN** the device is in landscape orientation
- **THEN** the left/right overlay columns SHALL follow the landscape-layout capability arrangement

#### Scenario: Drive button toggles mode

- **WHEN** the map mode is BROWSE
- **AND** the user taps the drive mode toggle button
- **THEN** the map mode SHALL become FREE_DRIVE
- **WHEN** the map mode is FREE_DRIVE
- **AND** the user taps the drive mode toggle button
- **THEN** the map mode SHALL become BROWSE

#### Scenario: Drive button hidden during navigation

- **WHEN** the map mode is NAVIGATION
- **THEN** the drive mode toggle button SHALL NOT be visible

#### Scenario: Re-center button hidden at start

- **WHEN** the app starts in BROWSE mode
- **AND** the viewport has not drifted from the GPS position
- **THEN** the re-center button SHALL NOT be visible at the bottom-left

### Requirement: System back dismisses topmost overlay
The map canvas screen SHALL respond to the system back gesture/button by dismissing the topmost open overlay (sheet, panel, or dialog) instead of exiting the application.

#### Scenario: Back dismisses search panel
- **WHEN** the unified search dialog is open on the map canvas
- **AND** user performs the system back gesture or presses the back button
- **THEN** the search dialog SHALL close
- **AND** the map canvas SHALL remain visible

#### Scenario: Back dismisses favorites sheet
- **WHEN** the favorites sheet is open on the map canvas
- **AND** user performs the system back gesture or presses the back button
- **THEN** the favorites sheet SHALL close
- **AND** the map canvas SHALL remain visible

#### Scenario: Back on base map keeps default behavior
- **WHEN** no overlay is open on the map canvas
- **AND** user performs the system back gesture or presses the back button
- **THEN** the app SHALL follow default system back behavior

### Requirement: Navigation right column includes location options

During NAVIGATION, the right-side widget column SHALL include the location options button in addition to the compass, speed widget, and zoom controls, so the options bottom sheet is reachable mid-route.

#### Scenario: Location options button in navigation column

- **WHEN** the map state is NAVIGATION
- **THEN** the right-side widget column SHALL show the location options button
- **AND** tapping it SHALL open the options bottom sheet

### Requirement: The phone map canvas is suspended while a car session is active

While a car session is active in the process and the user has not overridden it, the phone SHALL
present a car-session surface instead of the map canvas: the map canvas SHALL NOT be composed, the
phone surface SHALL request no map renders, and the phone-owned render storage (its bitmap tile cache
and its pooled render targets) SHALL be released. The shared native tile-data cache SHALL NOT be
released, because the car surface renders from it.

#### Scenario: A car session suspends the phone map

- **WHEN** a car session becomes active in the process while the phone map canvas is displayed
- **THEN** the map canvas SHALL stop being composed
- **AND** the phone SHALL request no further map renders
- **AND** the car-session surface SHALL be displayed in its place

#### Scenario: Phone-owned storage is released, the shared cache is not

- **WHEN** the phone map canvas is suspended by a car session
- **THEN** the phone's bitmap tile cache and its pooled render targets SHALL be released
- **AND** the shared native tile-data cache SHALL remain configured and populated for the car surface

#### Scenario: The car keeps rendering while the phone is suspended

- **WHEN** the phone map canvas is suspended and the car surface draws the navigation
- **THEN** the car frames SHALL continue to render without a surface or lock failure

#### Scenario: Ending the session returns the map

- **WHEN** the car session ends
- **THEN** the phone map canvas SHALL be displayed again
- **AND** the phone's mode, viewport and magnification SHALL be the ones it had before the suspension
- **AND** no error state SHALL be shown

#### Scenario: Suspension never loses the navigation state

- **WHEN** the phone map is suspended and the car session continues navigating
- **THEN** the phone's resumed map SHALL show the navigation state the shared engine currently holds

### Requirement: The car-session surface is informative and offers the map back

The car-session surface SHALL state that guidance is being shown on the car display and SHALL present
the current guidance summary from the shared navigation state, with the same labels the car surface
uses for the same state. It SHALL offer an explicit action that returns the phone to the map for the
rest of the session.

#### Scenario: The surface identifies the session and the guidance

- **WHEN** the car-session surface is displayed during an active navigation
- **THEN** it SHALL state that guidance is on the car display
- **AND** it SHALL show the current guidance summary using the labels the car surface shows
- **AND** the text SHALL be available in German and English

#### Scenario: The user brings the map back

- **WHEN** the user activates the surface's map action
- **THEN** the phone map canvas SHALL be displayed again for the rest of the session
- **AND** the phone SHALL render as it does without a car session

#### Scenario: The override does not outlive the session

- **WHEN** the user overrode the suspension and the car session then ends
- **THEN** the override SHALL be cleared
- **AND** a later car session SHALL suspend the phone map again by default

#### Scenario: The override is diagnosable

- **WHEN** the suspension is applied, overridden, or lifted
- **THEN** each transition SHALL be recorded on the diagnostics stream with the session presence it
  followed
