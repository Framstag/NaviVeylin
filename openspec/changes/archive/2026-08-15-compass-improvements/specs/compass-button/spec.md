## MODIFIED Requirements

### Requirement: Compass visually differentiates orientation modes

The compass needle SHALL have a visually distinct appearance between "always north" (north-up) and "follow direction" modes, so the user can tell at a glance which mode is active. The button body color reflects GPS fix quality (see "GPS fix status fill color") and is not mode-dependent.

#### Scenario: Always north mode shows fixed north indicator

- **WHEN** the orientation mode is "always north" (north-up)
- **THEN** the compass SHALL display a prominent north indicator (e.g., a red "N" or arrow)

#### Scenario: Follow direction mode shows directional indicator

- **WHEN** the orientation mode is "follow direction"
- **THEN** the compass SHALL display a compass-needle-like triangle pointing in the travel direction
- **AND** the triangle's base line SHALL be smaller than its height

## ADDED Requirements

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

### Requirement: Compass button matches overlay button sizing

The compass button SHALL be the same size as the other map overlay buttons (menu, search, location options) and SHALL use the same shadow as those buttons. The follow-direction triangle needle SHALL be sized to about 70% of the button.

#### Scenario: Compass button same size as other overlay buttons

- **WHEN** the map screen is displayed
- **THEN** the compass button SHALL match the size of the other overlay buttons
- **AND** the compass button SHALL use the same shadow as the other overlay buttons
- **AND** the follow-direction triangle SHALL be sized to about 70% of the button

## REMOVED Requirements

### Requirement: GPS fix status ring

**Reason**: The ring was replaced by the button fill color, which is more visible at a glance.

**Migration**: GPS fix quality is now indicated by the compass button's fill color (see "GPS fix status fill color").

### Requirement: Thicker GPS fix status ring

**Reason**: Superseded by the fill-color indicator; the ring no longer exists.

**Migration**: GPS fix quality is now indicated by the compass button's fill color (see "GPS fix status fill color").
