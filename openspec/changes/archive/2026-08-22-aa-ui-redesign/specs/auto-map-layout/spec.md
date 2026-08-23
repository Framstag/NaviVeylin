## Purpose

Defines the layout and interaction model for the Android Auto map and navigation displays: the app menu lives in the template content slot, host action strips carry the map controls (search, settings, zoom), and navigation shows left-oriented hints plus right-edge visualisation indicators (compass rose, speed limit).

## ADDED Requirements

### Requirement: Visualisation controls

The system SHALL display the zoom buttons together with Search and Settings in the template action strip (right edge) of the Android Auto map display — host-rendered, always tappable (the AAOS template host does not forward surface gestures, so surface-drawn interactive controls cannot be used). The rotating compass rose appears only during navigation.

#### Scenario: Zoom, search and settings on the right

- **WHEN** the Android Auto map display is visible
- **THEN** the zoom in/out, search and settings actions are shown in the template action strip on the right edge of the display

#### Scenario: Buttons tappable without surface gestures

- **WHEN** the map display is shown on any host (projection or AAOS)
- **THEN** the action buttons are host-rendered and always tappable, independent of map surface gesture delivery

#### Scenario: No compass while browsing

- **WHEN** the browse map is displayed and the user is not navigating
- **THEN** no compass rose is drawn

#### Scenario: Compass rose during navigation

- **WHEN** navigation is active
- **THEN** a compass rose on the navigation display rotates so its north pointer faces true north
- **AND** the rose is right-aligned to the display edge, sized like the strip buttons (48 dp)

### Requirement: Content box acts as the app menu

The system SHALL use the map template's required content slot as the app menu, headed by the app icon and name, with entries for free driving, starred favorites, all favorites, POI search, search history, diagnostics, and about.

#### Scenario: Menu header shows app identity

- **WHEN** the map display is visible
- **THEN** the content menu header shows the app icon and the app name

#### Scenario: Menu entries open their screens

- **WHEN** the user taps "Free driving"
- **THEN** active navigation stops and the map re-centers on the current GPS position
- **WHEN** the user taps "Starred favorites"
- **THEN** a favorites screen filtered to starred favorites opens
- **WHEN** the user taps "All favorites"
- **THEN** the full favorites screen opens
- **WHEN** the user taps "Search for POIs"
- **THEN** the POI search screen opens
- **WHEN** the user taps "Search history"
- **THEN** the search history screen opens
- **WHEN** the user taps "Diagnostics"
- **THEN** the diagnostics screen opens
- **WHEN** the user taps "About"
- **THEN** the about screen opens

#### Scenario: History entry re-runs the search

- **WHEN** the user taps a search history entry
- **THEN** the search screen opens prefilled with that query and runs the search

### Requirement: Speed-limit indicator during navigation

The system SHALL display the current speed-limit badge on the navigation display, positioned in the right visualisation region, turning red when the current speed exceeds the limit.

#### Scenario: Limit badge shown during navigation

- **WHEN** navigation is active and speed-limit data is available
- **THEN** a speed-limit badge is shown on the right side of the navigation display inside the stable region

#### Scenario: Limit exceeded warning

- **WHEN** current speed exceeds the displayed speed limit
- **THEN** the badge changes to a warning color

### Requirement: Settings dialog reachable while driving

The system SHALL provide a settings action on the Android Auto map display that opens a settings dialog whose content mirrors the phone's location-options dialog: follow mode, browse orientation, navigation orientation, auto-zoom, dark mode, lane hints, and render mode. The dialog SHALL be reachable while the vehicle is moving.

#### Scenario: Settings dialog opens while driving

- **WHEN** the user taps the settings action on the map display while the vehicle is moving
- **THEN** a settings dialog opens without requiring the vehicle to be parked

#### Scenario: Settings content matches phone dialog

- **WHEN** the settings dialog is open
- **THEN** it presents the same settings as the phone's location-options dialog: follow mode, browse orientation, navigation orientation, auto-zoom, dark mode, lane hints, and render mode

#### Scenario: Setting changes apply to map

- **WHEN** the user changes a setting in the dialog
- **THEN** the change takes effect on the map display immediately and persists for future sessions

#### Scenario: Keep-screen-on remains phone-only

- **WHEN** the settings dialog is open
- **THEN** it does not expose the phone-only keep-screen-on option

### Requirement: Navigation hints left-oriented

During navigation, the system SHALL display the navigation hints (next-turn instruction, distance, lane guidance) left-aligned on the display, positioned immediately to the right of the action strip, and SHALL NOT let the hints overlap the visualisation strip on the right.

#### Scenario: Hints positioned right of action strip

- **WHEN** navigation is active
- **THEN** the navigation hints appear left-aligned, to the right of the action strip

#### Scenario: Hints do not cover right visualisation strip

- **WHEN** navigation is active and both strips are visible
- **THEN** the navigation hints do not extend into the horizontal region occupied by the right visualisation strip

#### Scenario: Hints update on approach

- **WHEN** the vehicle approaches the next turn
- **THEN** the hint text and distance update in place without changing position

#### Scenario: Hints not clipped by host chrome

- **WHEN** the navigation display is shown on an Android unit with horizontal chrome
- **THEN** the hint panel stays inside the host-reported stable region of the surface
