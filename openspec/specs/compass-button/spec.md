# compass-button Specification

## Purpose

Provides a visual compass widget on the map screen that shows north direction, indicates GPS fix quality via the button fill color, and lets users toggle map orientation mode or re-center on their location.
## Requirements
### Requirement: Compass shows north direction

The system SHALL display an animated compass widget that rotates to indicate the current direction of north relative to the map's current rotation angle.

#### Scenario: Compass points north when map is north-up

- **WHEN** the map rotation is 0° (north-up)
- **THEN** the compass needle SHALL point straight up (12 o'clock position)

#### Scenario: Compass rotates with map

- **WHEN** the map rotates to follow direction (e.g., bearing 90° east)
- **THEN** the compass needle SHALL rotate by the same angle relative to the screen
- **AND** the needle SHALL continue to point toward geographic north

#### Scenario: Compass rotation is animated

- **WHEN** the map rotation angle changes
- **THEN** the compass needle SHALL animate smoothly to the new angle over a short duration (≤300ms)

### Requirement: North pointer points at rendered north

The north pointer (the phone compass needle in "always north" mode; the Android Auto compass rose in free driving and navigation) SHALL point at the screen position where north actually renders on the map, using the same rotation convention as the map projection: north's screen direction is `screenBearing(0°, mapAngle)`, i.e. the viewport angle measured clockwise from screen-up (0° = straight up). In heading-up follow mode (map angle = −bearing), the north pointer SHALL sit at `360 − bearing` degrees (mod 360), so the pointer and the map agree in every rotation state — a 180° sign flip is a defect. In the phone's follow-direction mode no north needle is drawn (the travel-direction triangle replaces it, see "Follow-direction triangle shows travel direction"); the north direction stays visible there only on Android Auto via the compass rose.

#### Scenario: North-up mode keeps needle up

- **WHEN** "always north" orientation is active on the phone (map angle = 0)
- **THEN** the compass needle SHALL point straight up (0°)

#### Scenario: Westbound heading, north on the driver's right

- **WHEN** a north pointer is visible (Android Auto compass rose) with heading-up rotation
- **AND** the vehicle heading is 270° (driving west), so the map is rotated heading-up (map angle = −270° ≡ +90°)
- **THEN** the north pointer SHALL point 90° clockwise from screen-up — to the driver's right, where true north is
- **AND** the pointer SHALL NOT point 180° from that position (south)

#### Scenario: Eastbound heading, north on the driver's left

- **WHEN** a north pointer is visible (Android Auto compass rose) with heading-up rotation
- **AND** the vehicle heading is 90° (driving east), map angle = −90° ≡ +270°
- **THEN** the north pointer SHALL point 270° clockwise from screen-up — to the driver's left, where true north is

### Requirement: Follow-direction triangle shows travel direction

In "follow direction" mode the phone compass triangle SHALL point at the screen direction of travel, computed as `screenBearing(bearing, mapAngle)` — straight up while heading-up follow is active, and following the travel direction after the user manually rotates the map. It SHALL be independent of the north pointer's rotation sign.

#### Scenario: Triangle points up while heading-up

- **WHEN** follow-direction mode is active with heading-up rotation
- **THEN** the compass triangle SHALL point straight up (0° on screen), the same screen direction as the travel direction on the rotated map

#### Scenario: Triangle follows travel direction after manual rotation

- **WHEN** the user manually rotates the map in follow-direction mode so the map angle is no longer −bearing
- **THEN** the compass triangle SHALL point at the travel direction's screen position (`bearing + mapAngle`, mod 360)

### Requirement: GPS fix status fill color

The system SHALL display GPS fix quality using the compass button's fill (background) color with light colors. The fill color SHALL be clearly visible at a glance.

#### Scenario: No GPS fix shows light red fill

- **WHEN** no GPS location fix is available
- **THEN** the compass button fill SHALL display a light red color

#### Scenario: Poor GPS accuracy shows light yellow fill

- **WHEN** a GPS fix is available with accuracy worse than 50 meters
- **THEN** the compass button fill SHALL display a light yellow color

#### Scenario: Good GPS fix shows light green fill

- **WHEN** a GPS fix is available with accuracy ≤50 meters
- **THEN** the compass button fill SHALL display a light green color

### Requirement: Short press re-centers on location

The system SHALL re-center the map on the user's current GPS location when the compass button is short-pressed (tap).

#### Scenario: Short press centers map

- **WHEN** the user short-presses the compass button
- **AND** a GPS location is available
- **THEN** the map SHALL center on the current GPS location
- **AND** follow mode SHALL be enabled

#### Scenario: Short press with no GPS fix shows snackbar

- **WHEN** the user short-presses the compass button
- **AND** no GPS location is available
- **THEN** the system SHALL show a snackbar message indicating no location fix

### Requirement: Long press toggles orientation mode

The system SHALL toggle between "Always north" and "Follow direction" orientation modes when the compass button is long-pressed.

#### Scenario: Long press switches to follow direction

- **WHEN** the current orientation is "Always north" (north-up)
- **AND** the user long-presses the compass button
- **THEN** the orientation SHALL switch to "Follow direction"
- **AND** the map SHALL rotate to match the GPS bearing (if follow mode is active)

#### Scenario: Long press switches to north-up

- **WHEN** the current orientation is "Follow direction"
- **AND** the user long-presses the compass button
- **THEN** the orientation SHALL switch to "Always north" (north-up)
- **AND** the map SHALL rotate to 0°

### Requirement: Compass mode syncs with orientation settings

The compass button's mode SHALL reflect and update the same orientation settings (`freeFormNorthUp`/`navNorthUp`) used by the location options bottom sheet.

#### Scenario: Compass and bottom sheet stay in sync

- **WHEN** the user toggles orientation via the compass button long press
- **THEN** the location options bottom sheet SHALL show the updated orientation selection
- **WHEN** the user changes orientation via the location options bottom sheet
- **THEN** the compass button SHALL reflect the updated mode

### Requirement: Compass positioned at top of right view column

The system SHALL position the compass button at the top of the right view column. The right view column SHALL be bottom-anchored: at the bottom-right of the screen in the standard (free-form) view, and above the routing status bar during navigation. The menu, search, and favorites buttons move to the left action column (see `map-canvas-screen`), so the compass is no longer between the menu and search buttons.

#### Scenario: Compass at top of right view column

- **WHEN** the map screen is displayed
- **THEN** the compass button SHALL be visible at the top of the right view column
- **AND** the right view column SHALL be bottom-anchored
- **AND** the menu (toaster) button SHALL be on the left side of the screen
- **AND** it SHALL appear above the search (🔍) button

### Requirement: Compass visually differentiates orientation modes

The compass needle SHALL have a visually distinct appearance between "always north" (north-up) and "follow direction" modes, so the user can tell at a glance which mode is active. The button body color reflects GPS fix quality (see "GPS fix status fill color") and is not mode-dependent.

#### Scenario: Always north mode shows fixed north indicator

- **WHEN** the orientation mode is "always north" (north-up)
- **THEN** the compass SHALL display a prominent north indicator (e.g., a red "N" or arrow)

#### Scenario: Follow direction mode shows directional indicator

- **WHEN** the orientation mode is "follow direction"
- **THEN** the compass SHALL display a compass-needle-like triangle pointing in the travel direction
- **AND** the triangle's base line SHALL be smaller than its height

### Requirement: Compass button matches overlay button sizing

The compass button SHALL be larger than the other map overlay buttons (menu, search, location options): 56dp layout / 48dp visual vs the 48dp layout / 40dp visual of the other buttons, so it reads at a glance while driving. It SHALL use the same shadow as those buttons. The follow-direction triangle needle SHALL be sized to about 70% of the button.

#### Scenario: Compass button same size as other overlay buttons

- **WHEN** the map screen is displayed
- **THEN** the compass button SHALL be 56dp layout / 48dp visual
- **AND** the other overlay buttons (menu, search, location options) SHALL remain 48dp layout / 40dp visual
- **AND** the compass button SHALL use the same shadow as the other overlay buttons
- **AND** the follow-direction triangle SHALL be sized to about 70% of the button
