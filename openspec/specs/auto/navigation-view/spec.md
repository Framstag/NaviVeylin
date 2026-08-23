# Android Auto Navigation View (auto/navigation-view) Specification

## Purpose

Defines the Android Auto navigation view: the host-rendered `NavigationTemplate` instruction panel (next-turn maneuver, route-description step list with next-next turns, lane guidance) plus surface-drawn current street name, with time/distance to destination and exit-anytime. Compass rose and speed-limit badge stay covered by `auto-map-layout`.

## Requirements

### Requirement: Next turn maneuver in host instruction panel

The system SHALL render the next turn instruction in the host-rendered navigation instruction panel of the `NavigationTemplate`, showing a turn-type icon, the distance to the turn and the target street name, and SHALL update it as navigation progresses.

#### Scenario: Maneuver shown during navigation

- **WHEN** navigation is active and next-turn data is available
- **THEN** the host instruction panel shows a maneuver with the next turn's type icon, distance and target street name

#### Scenario: Maneuver updates on approach

- **WHEN** the vehicle approaches the next turn and the next-turn data changes
- **THEN** the host maneuver updates to the new turn instruction

#### Scenario: No maneuver when not navigating

- **WHEN** navigation is not active
- **THEN** the host instruction panel shows no maneuver

### Requirement: Current and next step in host instruction panel

The system SHALL render the next-turn instruction (current step) and the following turn (next step) in the host-rendered navigation instruction panel of the `NavigationTemplate`, showing a turn-type icon, the distance to the turn and the target street name, and SHALL update them as navigation progresses.

#### Scenario: Current step shown during navigation

- **WHEN** navigation is active and next-turn data is available
- **THEN** the host instruction panel shows the current step with a turn-type icon, the distance to the turn and the target street name

#### Scenario: Next-next turn shown when present

- **WHEN** the route has at least one turn after the current step
- **THEN** the host instruction panel shows the next step (next-next turn) with its own turn type and target street

#### Scenario: Steps update on approach

- **WHEN** the vehicle approaches the next turn and the navigation data changes
- **THEN** the host instruction panel updates to the new current and next steps

#### Scenario: No step when not navigating

- **WHEN** navigation is not active
- **THEN** the host instruction panel shows no step information

#### Scenario: Distance rounded for display

- **WHEN** the distance to the current step is shown
- **THEN** it is rounded for display: exact up to 50 m, multiples of 50 m up to 1 km, one decimal km above (meters below 1 km, kilometers above)

### Requirement: Route line on navigation map

The system SHALL draw the calculated route on the navigation map, styled by the map stylesheet's route style, and SHALL update it when the route changes.

#### Scenario: Route shown during navigation

- **WHEN** navigation is active
- **THEN** the route polyline is drawn on the map

#### Scenario: Route updates on reroute

- **WHEN** a reroute replaces the route
- **THEN** the map shows the new route polyline

#### Scenario: Route hidden when not navigating

- **WHEN** navigation is not active
- **THEN** no route polyline is drawn

### Requirement: Route description screen

The system SHALL provide a route description screen reachable from the navigation view that lists every remaining route instruction from the current step to the destination, with the current step marked.

#### Scenario: Route description opened from navigation view

- **WHEN** the user taps the route-description action on the navigation view while navigating
- **THEN** a route description screen opens listing all remaining instructions in route order, starting with the current step

#### Scenario: Current step highlighted

- **WHEN** the route description screen is shown
- **THEN** the current step is visually marked and the list advances as the vehicle progresses

#### Scenario: List scrollable

- **WHEN** the route has more instructions than fit on the display
- **THEN** the route description list scrolls to reveal the remaining instructions

#### Scenario: Back returns to navigation view

- **WHEN** the user presses back on the route description screen
- **THEN** the screen returns to the navigation view and navigation keeps running

### Requirement: Host-rendered lane guidance

The system SHALL render lane guidance in the host instruction panel when lane data is available and the lane-hints setting is enabled, showing the suggested lane or lanes for the next maneuver.

#### Scenario: Lane guidance shown

- **WHEN** navigation is active, lane data is available and lane hints are enabled
- **THEN** the host instruction panel shows lane guidance for the next maneuver with the suggested lane(s) highlighted

#### Scenario: Lane guidance hidden without data

- **WHEN** no lane data is available or lane hints are disabled
- **THEN** no lane guidance is shown in the instruction panel

### Requirement: Time and distance to destination

The system SHALL show the remaining travel time and remaining distance to the destination in the host instruction panel.

#### Scenario: ETA and distance shown

- **WHEN** navigation is active
- **THEN** the host instruction panel shows the remaining time and remaining distance to the destination, updating as the trip progresses

#### Scenario: Remaining distance rounded for display

- **WHEN** the remaining distance to the destination is shown
- **THEN** it is rounded for display: exact up to 50 m, multiples of 50 m up to 1 km, one decimal km above

### Requirement: Current street name shown during navigation

The system SHALL display the name of the current street on the navigation display while navigating, updating when the vehicle changes roads.

#### Scenario: Street name displayed while driving

- **WHEN** navigation is active and current-road data is available
- **THEN** the current street name is drawn on the map surface

#### Scenario: Street name updates on street change

- **WHEN** the vehicle enters a new road during navigation
- **THEN** the displayed street name updates to the new road's name

#### Scenario: No street name when unnamed

- **WHEN** navigation is active but no street name is available
- **THEN** no street-name label is drawn

### Requirement: Speed-driven auto-zoom during navigation

The system SHALL adjust the navigation map zoom with the vehicle speed while the auto-zoom setting is enabled; a manual zoom suspends auto-zoom until the vehicle crosses a speed band.

#### Scenario: Speed increases zoom out

- **WHEN** navigation is active, auto-zoom is enabled and the vehicle speeds up
- **THEN** the map zoom level adjusts to the higher speed band

#### Scenario: Speed decreases zoom in

- **WHEN** navigation is active, auto-zoom is enabled and the vehicle slows down
- **THEN** the map zoom level adjusts to the lower speed band

#### Scenario: Manual zoom suspends auto-zoom

- **WHEN** the user zooms manually during navigation
- **THEN** auto-zoom stops adjusting the zoom until the speed crosses a speed-band boundary

#### Scenario: Auto-zoom disabled by setting

- **WHEN** the auto-zoom setting is disabled
- **THEN** the navigation map zoom is not adjusted by speed

### Requirement: Leave navigation at any time

The system SHALL let the user stop active navigation from the car display at any time via a visible stop action (an "x" button) in the navigation map action strip or via system back, and SHALL NOT show an explicit back button in the map action strip.

#### Scenario: Stop navigation from car

- **WHEN** the user presses system back during navigation
- **THEN** navigation stops on the car display and the screen returns to the root menu

#### Scenario: Stop action shown on navigation map

- **WHEN** the user is navigating on the car display
- **THEN** the navigation map action strip shows a stop action (an "x" button) beside the route-description action, and no back button

#### Scenario: Stop action stops navigation

- **WHEN** the user activates the stop action on the navigation map while navigating
- **THEN** navigation stops on the car display and the screen returns to the root menu

#### Scenario: System back still leaves with the stop action shown

- **WHEN** the user is navigating and the map action strip shows the stop and route-description actions without an explicit back button
- **THEN** pressing system back still stops navigation and returns the screen to the root menu
