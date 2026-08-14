## Purpose

Provides a visual compass widget on the map screen that shows north direction, indicates GPS fix quality via a colored ring, and lets users toggle map orientation mode or re-center on their location.

## ADDED Requirements

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

### Requirement: GPS fix status ring

The system SHALL display a colored ring around the compass that indicates GPS fix quality using light colors.

#### Scenario: No GPS fix shows light red ring

- **WHEN** no GPS location fix is available
- **THEN** the compass ring SHALL display a light red color

#### Scenario: Poor GPS accuracy shows light yellow ring

- **WHEN** a GPS fix is available with accuracy worse than 50 meters
- **THEN** the compass ring SHALL display a light yellow color

#### Scenario: Good GPS fix shows light green ring

- **WHEN** a GPS fix is available with accuracy ≤50 meters
- **THEN** the compass ring SHALL display a light green color

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

### Requirement: Compass positioned between menu and search

The system SHALL position the compass button in the top-right overlay column, below the menu button and above the search button.

#### Scenario: Compass visible in overlay column

- **WHEN** the map screen is displayed
- **THEN** the compass button SHALL be visible in the top-right overlay column
- **AND** it SHALL appear below the menu (⋮) button
- **AND** it SHALL appear above the search (🔍) button
