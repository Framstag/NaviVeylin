# compass-button Specification

## Purpose

Provides a visual compass widget on the map screen that shows north direction, indicates GPS fix quality via the button fill color, and lets users toggle map orientation mode or re-center on their location.

## Requirements

### Requirement: Compass shows north direction

The system SHALL display an animated compass widget that rotates to indicate the current direction of north relative to the map's current rotation angle. The needle SHALL indicate north in **every** orientation mode; the vehicle's direction of travel SHALL NOT influence it.

#### Scenario: Compass points north when map is north-up

- **WHEN** the map rotation is 0° (north-up)
- **THEN** the compass needle SHALL point straight up (12 o'clock position)

#### Scenario: Compass rotates with map

- **WHEN** the map rotates to follow direction (e.g., bearing 90° east)
- **THEN** the compass needle SHALL rotate by the same angle relative to the screen
- **AND** the needle SHALL continue to point toward geographic north

#### Scenario: Compass points north while heading-up

- **WHEN** "follow direction" orientation is active and the map is rotated heading-up (map angle = −bearing)
- **AND** the vehicle drives south (bearing 180°, map angle ≡ 180°)
- **THEN** the compass needle SHALL point down (180° on screen) — north is behind the vehicle
- **AND** the needle SHALL NOT point up or in the direction of travel

#### Scenario: Compass rotation is animated

- **WHEN** the map rotation angle changes
- **THEN** the compass needle SHALL animate smoothly to the new angle over a short duration (≤300ms)

#### Scenario: Needle unaffected by an unstable vehicle bearing

- **WHEN** the vehicle is stationary and the reported GPS bearing is noisy, absent or changes randomly
- **AND** the map rotation does not change
- **THEN** the compass needle SHALL NOT move
- **AND** the needle SHALL keep pointing at north for the current map rotation

### Requirement: North pointer points at rendered north

The north pointer SHALL point at the screen position where north actually renders on the map, using the same rotation convention as the map projection: north's screen direction is the viewport angle measured clockwise from screen-up (0° = straight up). On the phone the compass needle IS the north pointer and SHALL be drawn in every orientation mode, including heading-up follow mode, where it SHALL sit at `360 − bearing` degrees (mod 360) so the pointer and the map agree in every rotation state — a 180° sign flip is a defect. Android Auto shows the same north direction through its compass rose.

#### Scenario: North-up mode keeps needle up

- **WHEN** "always north" orientation is active on the phone (map angle = 0)
- **THEN** the compass needle SHALL point straight up (0°)

#### Scenario: Heading-up keeps the north pointer

- **WHEN** "follow direction" orientation is active on the phone and the map is rotated heading-up
- **THEN** the compass needle SHALL be drawn as the north pointer (no travel-direction indicator replaces it)

#### Scenario: Westbound heading, north on the driver's right

- **WHEN** a north pointer is visible (phone compass needle or Android Auto compass rose) with heading-up rotation
- **AND** the vehicle heading is 270° (driving west), so the map is rotated heading-up (map angle = −270° ≡ +90°)
- **THEN** the north pointer SHALL point 90° clockwise from screen-up — to the driver's right, where true north is
- **AND** the pointer SHALL NOT point 180° from that position (south)

#### Scenario: Eastbound heading, north on the driver's left

- **WHEN** a north pointer is visible (phone compass needle or Android Auto compass rose) with heading-up rotation
- **AND** the vehicle heading is 90° (driving east), map angle = −90° ≡ +270°
- **THEN** the north pointer SHALL point 270° clockwise from screen-up — to the driver's left, where true north is

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

### Requirement: Compass button matches overlay button sizing

The compass button SHALL be larger than the other map overlay buttons (menu, search, location options): 56dp layout / 48dp visual vs the 48dp layout / 40dp visual of the other buttons, so it reads at a glance while driving. It SHALL use the same shadow as those buttons.

#### Scenario: Compass button same size as other overlay buttons

- **WHEN** the map screen is displayed
- **THEN** the compass button SHALL be 56dp layout / 48dp visual
- **AND** the other overlay buttons (menu, search, location options) SHALL remain 48dp layout / 40dp visual
- **AND** the compass button SHALL use the same shadow as the other overlay buttons
