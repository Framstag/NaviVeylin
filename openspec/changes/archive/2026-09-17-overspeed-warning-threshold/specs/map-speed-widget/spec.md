## MODIFIED Requirements

### Requirement: Overspeed warning color

When the current speed equals or exceeds the max speed plus the overspeed warning delta (`current >= max + delta`), the speed badge SHALL be shown in a warning state: a red background with white foreground text. The red background SHALL use the same 0.92 alpha and 12dp rounded corners as the normal badge background, so the semi-transparent overlay treatment is retained while over the limit. The delta is a single global setting shared with Android Auto: a whole number of km/h between 0 and 30 inclusive, default 5, precise to 1 km/h. At 0 the badge warns the moment the current speed reaches the limit. Below `max + delta`, or when no limit is known, the badge SHALL use the normal colors. The delta SHALL apply identically on the phone and on Android Auto (no per-surface difference).

#### Scenario: Badge red when exceeding max by 5+ km/h

- **GIVEN** the max speed is 50 km/h and the overspeed warning delta setting is 5 km/h (the default)
- **WHEN** the current speed is 56 km/h (exceeds max + delta by 1 km/h)
- **THEN** the speed badge is shown with a red (warning) background and white text

#### Scenario: Badge normal color within limit

- **GIVEN** the max speed is 50 km/h and the overspeed warning delta setting is 5 km/h
- **WHEN** the current speed is 53 km/h (below max + delta)
- **THEN** the speed badge is shown in the normal colors (standard card background, onSurface text)

#### Scenario: Badge normal color without a limit

- **WHEN** the max speed is unknown
- **THEN** the speed badge is shown in the normal colors

#### Scenario: Badge warns at max plus delta

- **GIVEN** the max speed is 50 km/h and the overspeed warning delta setting is 5 km/h
- **WHEN** the current speed is 55 km/h (equals max + delta)
- **THEN** the speed badge is shown with a red (warning) background and white text

#### Scenario: Delta of zero warns at the limit

- **GIVEN** the max speed is 50 km/h and the overspeed warning delta setting is 0 km/h
- **WHEN** the current speed is 50 km/h (reaches the limit)
- **THEN** the speed badge is shown with a red (warning) background and white text

#### Scenario: Warning background stays semi-transparent

- **WHEN** the speed badge is shown in the warning state
- **THEN** the red background uses the same transparency as the normal badge background (0.92 alpha)

### Requirement: Speed badge uses the standard overlay card container

In the normal state (not over the limit) the speed badge background SHALL use the same card container as the other map overlays (turn instruction card, routing status): the theme surface color at 0.92 alpha with a 12dp rounded rectangle — NOT a fixed dark color. While the current speed equals or exceeds the max speed plus the overspeed warning delta (as configured, `current >= max + delta`), the badge SHALL instead use the warning red background at the same 0.92 alpha and 12dp rounded corners with white text; the standard card container does not apply in the warning state.

#### Scenario: Badge container matches overlay cards

- **WHEN** the speed badge is displayed and not over the limit
- **THEN** its background is the theme surface card container (surface at 0.92 alpha, 12dp rounded corners)
- **AND** it is not a fixed near-black color

#### Scenario: Overspeed color readable on card

- **WHEN** the speed badge is in the warning state (current speed at or beyond max plus the configured delta)
- **THEN** the badge background is the warning red at 0.92 alpha with 12dp rounded corners
- **AND** the badge text is white and remains readable in both light and dark color schemes
