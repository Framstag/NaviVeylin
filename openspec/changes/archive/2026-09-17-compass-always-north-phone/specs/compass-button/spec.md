## MODIFIED Requirements

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

### Requirement: Compass button matches overlay button sizing

The compass button SHALL be larger than the other map overlay buttons (menu, search, location options): 56dp layout / 48dp visual vs the 48dp layout / 40dp visual of the other buttons, so it reads at a glance while driving. It SHALL use the same shadow as those buttons.

#### Scenario: Compass button same size as other overlay buttons

- **WHEN** the map screen is displayed
- **THEN** the compass button SHALL be 56dp layout / 48dp visual
- **AND** the other overlay buttons (menu, search, location options) SHALL remain 48dp layout / 40dp visual
- **AND** the compass button SHALL use the same shadow as the other overlay buttons

## REMOVED Requirements

### Requirement: Follow-direction triangle shows travel direction

**Reason**: Replaced by the always-north needle. The widget's purpose is to show where north is, which is unavailable exactly in the heading-up case the triangle occupied; the travel direction is already shown by the vehicle marker arrow and by the map's rotation, and the triangle's needle target (`bearing + mapAngle`) was the only place where the raw, unsmoothed vehicle bearing leaked into the compass (source of the standstill "swirl").

**Migration**: "Follow direction" orientation still rotates the map to the driving direction (spec `compass-settings`) and still shows the vehicle marker arrow pointing along the direction of travel (spec `gps-location-marker`). Users who want to know where north is now read it from the compass needle in both orientation modes.

### Requirement: Compass visually differentiates orientation modes

**Reason**: Obsolete: both orientation modes now draw the same north pointer, so the needle no longer encodes the mode. The mode remains observable from the map's own rotation and from the orientation selection in the location options bottom sheet (spec `compass-settings`, `location-options-ui`).

**Migration**: No user action required. The location options sheet and the compass long-press remain the way to see and change the orientation mode; the GPS fix-quality fill color of the button is unchanged.
