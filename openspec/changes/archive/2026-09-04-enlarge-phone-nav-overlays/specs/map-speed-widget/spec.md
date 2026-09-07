# map-speed-widget — Delta for enlarge-phone-nav-overlays

## ADDED Requirements

### Requirement: Speed badge uses the standard overlay card container
The speed badge background SHALL use the same card container as the other map overlays (turn instruction card, routing status): the theme surface color at 0.92 alpha with a 12dp rounded rectangle — NOT a fixed dark color. This keeps the badge readable on any map (light/dark) in both color schemes and guarantees the overspeed warning color contrasts with the badge background.

#### Scenario: Badge container matches overlay cards
- **WHEN** the speed badge is displayed
- **THEN** its background is the theme surface card container (surface at 0.92 alpha, 12dp rounded corners)
- **AND** it is not a fixed near-black color

#### Scenario: Overspeed color readable on card
- **WHEN** the current speed exceeds the max speed by 5+ km/h and the badge uses the warning color
- **THEN** the warning color text is rendered on the standard card background
- **AND** the badge remains readable in both light and dark color schemes

### Requirement: Minimum readable size for speed text
The current-speed text in the badge SHALL render at least as large as 24sp bold, so it is readable at a glance while driving.

#### Scenario: Badge text at least 24sp
- **WHEN** the speed badge shows the current speed (km/h)
- **THEN** the speed text uses a font size of 24sp or larger with bold weight

### Requirement: Minimum readable size for the max-speed sign
The round max-speed sign SHALL be at least 56dp in diameter with a red border of at least 4dp and digits of at least 22sp bold.

#### Scenario: Sign at least 56dp
- **WHEN** the max-speed sign is shown
- **THEN** the sign circle is at least 56dp in diameter

#### Scenario: Sign digits at least 22sp
- **WHEN** the max-speed sign is shown
- **THEN** the digit text uses a font size of 22sp or larger with bold weight

#### Scenario: Reserved slot still matches
- **WHEN** the sign is hidden but the slot is reserved (bottom-anchored placement)
- **THEN** the reserved slot keeps the same footprint as the visible sign (56dp)
