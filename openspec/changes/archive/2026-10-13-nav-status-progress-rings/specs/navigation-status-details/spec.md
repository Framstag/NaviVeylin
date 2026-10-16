# Spec Delta

## REMOVED Requirements

### Requirement: Route progress lines in routing status card

**Reason**: The two full-width progress lines cost roughly a quarter of the routing status card's height (~37 dp of a ~129 dp card) in the bottom band of a driving screen, and they duplicate the two values the stats row below them already prints as text. At their drawn height they are not readable shapes; the driver reads the numbers.

**Migration**: Spec `navigation-status-details`, requirement "Route progress rings on the routing status row" (added by this change). The same two percent values are still shown — each as a ring drawn around the glyph of the value it belongs to (remaining distance, remaining time) — computed from the same inputs (`routeProgressPercent`, `elapsedTimePercent`) and clamped to the same 0-100 range. Nothing carries a stored progress value, so there is nothing to migrate at runtime.

## ADDED Requirements

### Requirement: Route progress rings on the routing status row

During active navigation, the routing status row SHALL show the driver's progress along the route as a ring drawn around the glyph of the value it belongs to: a ring around the remaining-time glyph for the percent of estimated travel time elapsed, and a ring around the remaining-distance glyph for the percent of distance traveled. A ring SHALL be filled with the completed fraction of its dimension, and SHALL NOT show a text label or a percent value.

#### Scenario: Progress rings always visible during navigation

- **WHEN** navigation is active and both progress values are available
- **THEN** the routing status row SHALL show a ring around the remaining-distance glyph and a ring around the remaining-time glyph

#### Scenario: Progress rings not shown outside navigation

- **WHEN** navigation is not active
- **THEN** no glyph in the routing status row SHALL carry a ring

#### Scenario: Distance ring reflects traveled distance

- **WHEN** navigation is active and the driver has traveled part of the route
- **THEN** the remaining-distance glyph's ring SHALL be filled to the percent of the total route distance traveled, computed from the remaining distance relative to the total distance
- **AND** that total SHALL be the route's length as its step list sums it, so the ring closes at the destination

#### Scenario: Time ring reflects elapsed time

- **WHEN** navigation is active and part of the estimated travel time has elapsed
- **THEN** the remaining-time glyph's ring SHALL be filled to the percent of the estimated travel time elapsed, computed from the elapsed time relative to the estimated total travel time

#### Scenario: The two rings are told apart from the plain glyph

- **WHEN** navigation is active and both progress values are available
- **THEN** the remaining-distance glyph and the remaining-time glyph SHALL each carry a ring
- **AND** the arrival-time glyph SHALL carry no ring and SHALL be a glyph distinct from the remaining-time glyph

#### Scenario: No labels or percent values shown

- **WHEN** the progress rings are displayed
- **THEN** no text label and no percent value SHALL be shown for either ring

#### Scenario: Progress values clamped to valid range

- **WHEN** a computed progress percent is below 0 or above 100
- **THEN** the ring SHALL be drawn at the clamped 0-100 position

#### Scenario: A glyph without progress keeps its place in the row

- **WHEN** the routing status row is shown and one value's progress percent is unavailable while another's is
- **THEN** every glyph slot in the row SHALL be of the same size as the others and SHALL be vertically aligned with them
- **AND** the glyph whose percent is unavailable SHALL be drawn at its own slot position without a ring

#### Scenario: The same rings on every host of the row

- **WHEN** the routing status row is rendered on the phone's routing status card, in the phone's expanded route description, or on the car session surface
- **THEN** each host SHALL show the same two rings around the same two glyphs with the same semantics
- **AND** the labels and the order of the row's values SHALL be identical on the phone and on the car (spec: `cross-variant-ui-parity`)

#### Scenario: A recalculation does not hold a stale ring

- **WHEN** a reroute or recalculation lowers a progress percent
- **THEN** the ring SHALL be drawn at the newly computed percent on the next navigation-state update
- **AND** the row SHALL NOT hold the previous percent while the reroute is in progress
