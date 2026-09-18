## MODIFIED Requirements

### Requirement: Overspeed warning color

When the current speed exceeds the max speed by 5 km/h or more, the speed badge SHALL be shown in a warning state: a red background with white foreground text. The red background SHALL use the same 0.92 alpha and 12dp rounded corners as the normal badge background, so the semi-transparent overlay treatment is retained while over the limit. At or within 5 km/h of the limit, or when no limit is known, the badge SHALL use the normal colors.

#### Scenario: Badge red when exceeding max by 5+ km/h

- **GIVEN** the max speed is 50 km/h
- **WHEN** the current speed is 56 km/h (exceeds max by 6 km/h)
- **THEN** the speed badge is shown with a red (warning) background and white text

#### Scenario: Badge normal color within limit

- **GIVEN** the max speed is 50 km/h
- **WHEN** the current speed is 55 km/h (exceeds max by 5 km/h)
- **THEN** the speed badge is shown in the normal colors (standard card background, onSurface text)

#### Scenario: Badge normal color without a limit

- **WHEN** the max speed is unknown
- **THEN** the speed badge is shown in the normal colors

#### Scenario: Warning background stays semi-transparent

- **WHEN** the speed badge is shown in the warning state
- **THEN** the red background uses the same transparency as the normal badge background (0.92 alpha)

### Requirement: Speed badge uses the standard overlay card container

In the normal state (not over the limit) the speed badge background SHALL use the same card container as the other map overlays (turn instruction card, routing status): the theme surface color at 0.92 alpha with a 12dp rounded rectangle — NOT a fixed dark color. While the current speed exceeds the max speed by 5 km/h or more, the badge SHALL instead use the warning red background at the same 0.92 alpha and 12dp rounded corners with white text; the standard card container does not apply in the warning state.

#### Scenario: Badge container matches overlay cards

- **WHEN** the speed badge is displayed and not over the limit
- **THEN** its background is the theme surface card container (surface at 0.92 alpha, 12dp rounded corners)
- **AND** it is not a fixed near-black color

#### Scenario: Overspeed color readable on card

- **WHEN** the speed badge is in the warning state (over the limit by 5+ km/h)
- **THEN** the badge background is the warning red at 0.92 alpha with 12dp rounded corners
- **AND** the badge text is white and remains readable in both light and dark color schemes
