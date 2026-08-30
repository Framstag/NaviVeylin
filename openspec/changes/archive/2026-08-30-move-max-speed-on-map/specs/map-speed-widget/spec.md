# map-speed-widget Specification

## Purpose

Defines the phone on-map speed widget: a current-speed badge with the max speed as a round sign below it, drawn on the map in follow mode and during navigation, mirroring the Android Auto visualisation.

## ADDED Requirements

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

The speed widget SHALL be placed on the map in the right visualisation region, aligned with the compass indicator, so it does not overlap the routing status card.

#### Scenario: Widget in right visualisation region

- **WHEN** the speed widget is visible
- **THEN** it is drawn in the right visualisation region of the map, near the compass indicator

#### Scenario: Status card unaffected

- **WHEN** the speed widget is visible during navigation
- **THEN** the routing status card does not show the current or max speed
