## MODIFIED Requirements

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
