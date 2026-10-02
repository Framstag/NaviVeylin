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

The system SHALL display GPS fix quality using the compass button's fill (background) color, with one hue family per quality — red for no fix, yellow for poor fix, green for good fix — and a presentation-specific tone of that hue: the light tone while light presentation is active, a dark dimmed tone while dark presentation is active. The hue family assigned to a quality SHALL NOT change with presentation. The fill SHALL be clearly visible at a glance in light presentation, and in dark presentation it SHALL be visible without being the brightest element on the screen. The quality SHALL be the app's shared fix quality (spec: `gps-fix-quality`), so a fix that aged out without a new fix arriving, and a device whose location services are switched off, are both shown in the no-fix hue family.

#### Scenario: No GPS fix shows light red fill

- **WHEN** no GPS location fix is available
- **THEN** the compass button fill SHALL display a light red color in light presentation

#### Scenario: Poor GPS accuracy shows light yellow fill

- **WHEN** a GPS fix is available with accuracy worse than 50 meters
- **THEN** the compass button fill SHALL display a light yellow color in light presentation

#### Scenario: Good GPS fix shows light green fill

- **WHEN** a GPS fix is available with accuracy ≤50 meters
- **THEN** the compass button fill SHALL display a light green color in light presentation

#### Scenario: Dark presentation dims the same hue family

- **WHEN** dark presentation is active
- **THEN** the compass button fill SHALL display a dark tone of the current quality's hue family
- **AND** the fill SHALL NOT use the light presentation tone

#### Scenario: Quality change stays inside the active presentation

- **WHEN** dark presentation is active and the GPS fix quality changes
- **THEN** the fill SHALL switch to the dark tone of the new quality's hue family
- **AND** the three qualities SHALL remain visually distinguishable from each other in dark presentation

#### Scenario: Aged-out fix shows the no-fix fill

- **WHEN** the last GPS fix was classified GOOD
- **AND** no new fix arrives
- **AND** the fix becomes older than the fix age limit (spec: `gps-fix-quality`)
- **THEN** the compass button fill SHALL switch to the no-fix hue family within the re-evaluation
  delay
- **AND** it SHALL NOT keep the GOOD tone

#### Scenario: Disabled location services show the no-fix fill

- **WHEN** the last GPS fix was classified GOOD
- **AND** the platform reports that location services are disabled
- **THEN** the compass button fill SHALL switch to the no-fix hue family without waiting for the
  fix age limit

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

### Requirement: Compass colors follow the resolved day/night presentation

The system SHALL draw the compass needle, its north label, the button rim and the status fill from a single per-presentation palette, so needle/label/rim contrast against the fill is guaranteed in both presentations rather than derived from a user-themable color role. In light presentation the needle, label and rim SHALL be dark against a light fill; in dark presentation they SHALL be light against a dark fill, with a contrast ratio of at least 4.5:1 against the fill in both presentations. The palette SHALL be resolved from the app's resolved dark-presentation decision (the outcome of the On / Off / Automatic preference and its environment signal), never from the system night-mode flag directly, so a manual On or Off preference overrides the environment.

#### Scenario: Dark presentation uses light needle on dark fill

- **WHEN** dark presentation is active and any GPS fix quality is shown
- **THEN** the compass needle, its north label and the rim SHALL be drawn in a light color
- **AND** the needle SHALL have a contrast ratio of at least 4.5:1 against the fill

#### Scenario: Light presentation uses dark needle on light fill

- **WHEN** light presentation is active and any GPS fix quality is shown
- **THEN** the compass needle, its north label and the rim SHALL be drawn in a dark color
- **AND** the needle SHALL have a contrast ratio of at least 4.5:1 against the fill

#### Scenario: Manual dark mode overrides a light environment

- **WHEN** the dark mode preference is On while the environment signal reports light
- **THEN** the compass SHALL use the dark-presentation palette

#### Scenario: Manual light mode overrides a dark environment

- **WHEN** the dark mode preference is Off while the environment signal reports dark
- **THEN** the compass SHALL use the light-presentation palette

#### Scenario: GPS quality does not change the needle color

- **WHEN** the GPS fix quality changes while the presentation stays the same
- **THEN** the needle, north label and rim colors SHALL remain unchanged
- **AND** only the fill SHALL change

#### Scenario: Presentation change applies without restart

- **WHEN** the resolved presentation changes while the map screen is visible
- **THEN** the compass SHALL be re-rendered with the new palette without an app restart
