# Spec Delta — compass-button

## MODIFIED Requirements

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
