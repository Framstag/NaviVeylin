# Spec Delta

## MODIFIED Requirements

### Requirement: GPS fix status fill color

The system SHALL display GPS fix quality using the compass button's fill (background) color, with one hue family per quality — red for no fix, yellow for poor fix, green for good fix — and a presentation-specific tone of that hue: the light tone while light presentation is active, a dark dimmed tone while dark presentation is active. The hue family assigned to a quality SHALL NOT change with presentation. The fill SHALL be clearly visible at a glance in light presentation, and in dark presentation it SHALL be visible without being the brightest element on the screen.

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

## ADDED Requirements

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
