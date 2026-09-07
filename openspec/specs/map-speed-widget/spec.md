# map-speed-widget Specification

## Purpose
Defines the phone on-map speed widget: a current-speed badge with the max speed as a round sign below it, drawn on the map in follow mode and during navigation, mirroring the Android Auto visualisation.

## Requirements

### Requirement: Speed widget shown in follow mode

In the initial map view while follow mode is active, the system SHALL display the current vehicle speed as an on-map widget, with the max speed of the current road as a round sign below it.

#### Scenario: Speed badge shown in follow mode

- **WHEN** follow mode is active and the GPS fix reports a ground speed
- **THEN** the map shows a speed badge with the current speed (km/h)

#### Scenario: Max speed sign shown in follow mode

- **WHEN** follow mode is active and the road at the GPS position has a defined speed limit
- **THEN** the map shows the max speed as a round sign below the speed badge

#### Scenario: No sign without a limit

- **WHEN** the road at the GPS position has no defined speed limit
- **THEN** no max-speed sign is drawn

#### Scenario: Widget hidden without speed data

- **WHEN** the GPS fix reports no ground speed
- **THEN** the speed widget is not shown

### Requirement: Speed widget shown during navigation

During active navigation, the system SHALL display the current vehicle speed as an on-map widget, with the max allowed speed as a round sign below it.

#### Scenario: Speed badge shown during navigation

- **WHEN** navigation is active and current-speed data is available
- **THEN** the map shows a speed badge with the current speed (km/h)

#### Scenario: Max speed sign shown during navigation

- **WHEN** navigation is active and the max allowed speed is known
- **THEN** the map shows the max speed as a round sign below the speed badge

#### Scenario: Widget hidden when navigation stops

- **WHEN** navigation stops
- **THEN** the speed widget is hidden

### Requirement: Overspeed warning color

When the current speed exceeds the max speed by 5 km/h or more, the speed badge SHALL be shown in a warning color.

#### Scenario: Badge red when exceeding max by 5+ km/h

- **GIVEN** the max speed is 50 km/h
- **WHEN** the current speed is 56 km/h (exceeds max by 6 km/h)
- **THEN** the speed badge is shown in the warning color

#### Scenario: Badge normal color within limit

- **GIVEN** the max speed is 50 km/h
- **WHEN** the current speed is 55 km/h (exceeds max by 5 km/h)
- **THEN** the speed badge is shown in the normal color

#### Scenario: Badge normal color without a limit

- **WHEN** the max speed is unknown
- **THEN** the speed badge is shown in the normal color

### Requirement: Widget placement on map

The speed widget SHALL be placed on the map in the right visualisation region, aligned with the compass indicator, so it does not overlap the routing status card. The compass SHALL sit directly above the speed widget, with no other widget between them, in both the standard (free-form) view and during navigation. During navigation both are bottom-anchored above the routing status card.

#### Scenario: Widget in right visualisation region

- **WHEN** the speed widget is visible
- **THEN** it is drawn in the right visualisation region of the map, near the compass indicator

#### Scenario: Compass directly above speed widget in standard view

- **WHEN** the standard (free-form) view is displayed and the speed widget is visible
- **THEN** the compass is drawn directly above the speed widget, with no other widget between them

#### Scenario: Compass directly above speed widget during navigation

- **WHEN** navigation is active and the speed widget is visible
- **THEN** the compass is drawn directly above the speed widget, with no other widget between them

#### Scenario: Status card unaffected

- **WHEN** the speed widget is visible during navigation
- **THEN** the routing status card does not show the current or max speed

### Requirement: Centered indicator cluster

The compass, the current-speed badge, and the max-speed sign SHALL share a common center axis (vertically centered on each other), in follow mode and during navigation.

#### Scenario: Compass centered over the badge

- **WHEN** the speed widget is visible
- **THEN** the compass center is aligned with the badge center on the same vertical axis

#### Scenario: Sign centered under the badge

- **WHEN** the max-speed sign is visible
- **THEN** the sign center is aligned with the badge center on the same vertical axis

### Requirement: Stable widget layout

The speed widget SHALL keep the badge (and anything above it) at a fixed position when the max-speed sign appears or disappears. In bottom-anchored placements the sign slot SHALL be reserved even when no limit is known, and the whole widget slot SHALL be reserved when no speed data is available. The badge SHALL reserve the width of the widest speed value so it does not resize when the value changes or the source switches (follow mode ↔ navigation).

#### Scenario: Badge stays in place when sign hidden

- **WHEN** the speed widget is placed bottom-anchored (navigation) and the max speed is unknown
- **THEN** the sign slot below the badge is reserved (invisible) and the badge does not shift

#### Scenario: Sign fills the reserved slot

- **WHEN** the speed widget is placed bottom-anchored (navigation) and the max speed is known
- **THEN** the round sign is drawn in the reserved slot and the badge position is unchanged

#### Scenario: No reservation in top-anchored placement

- **WHEN** the speed widget is placed top-anchored (follow mode) and the max speed is unknown
- **THEN** no sign and no reserved slot are drawn below the badge

#### Scenario: Widget slot reserved when speed data unavailable

- **WHEN** the speed widget is placed bottom-anchored (navigation) and no current-speed data is available
- **THEN** the widget slot is reserved (invisible) and the compass above it does not shift

#### Scenario: Badge width stable across speed values

- **WHEN** the current speed changes (e.g. 48 → 120 km/h)
- **THEN** the badge width does not change

#### Scenario: Badge width stable across sources

- **WHEN** the speed source switches between follow mode (GPS) and navigation (engine)
- **THEN** the badge width does not change

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
The round max-speed sign SHALL be at least 64dp in diameter with a red border of at least 6dp and digits of at least 28sp bold.

#### Scenario: Sign at least 56dp
- **WHEN** the max-speed sign is shown
- **THEN** the sign circle is at least 56dp in diameter

#### Scenario: Sign digits at least 22sp
- **WHEN** the max-speed sign is shown
- **THEN** the digit text uses a font size of 22sp or larger with bold weight

#### Scenario: Sign at least 64dp
- **WHEN** the max-speed sign is shown
- **THEN** the sign circle is at least 64dp in diameter

#### Scenario: Sign digits at least 28sp
- **WHEN** the max-speed sign is shown
- **THEN** the digit text uses a font size of 28sp or larger with bold weight

#### Scenario: Reserved slot still matches
- **WHEN** the sign is hidden but the slot is reserved (bottom-anchored placement)
- **THEN** the reserved slot keeps the same footprint as the visible sign (64dp)
