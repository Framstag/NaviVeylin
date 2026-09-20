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

The system SHALL display the name and ref of the current street on the navigation display while navigating, taken from the route's way at the current point (not an area search) when on route, updating when the vehicle changes roads. The street name SHALL be rendered as an element of the host's routing status view — inside the travel-estimate (ETA) card via `TravelEstimate.setTripText` — and SHALL never be drawn on the map surface, regardless of the host's delivered stable/visible areas. When navigation is active but no road info is in state (off route), the street name SHALL come from the throttled bearing-aware road lookup at the GPS position.

#### Scenario: Street name displayed while driving

- **WHEN** navigation is active and current-road data is available
- **THEN** the host travel-estimate card shows the street name via `setTripText`

#### Scenario: Ref shown with the street name

- **WHEN** the current street has a ref tag
- **THEN** the ETA card shows the ref together with the name (e.g. "B 1 Hauptstrasse")

#### Scenario: Street name from the route, not an area search

- **WHEN** navigation is active and the vehicle is on the planned route
- **THEN** the street name comes from the route's way at the current point
- **AND** no reverse-geocode or description lookup is performed at the GPS position

#### Scenario: Street name updates on street change

- **WHEN** the vehicle enters a new road during navigation
- **THEN** the ETA-card trip text updates to the new road's name

#### Scenario: No street name when unnamed

- **WHEN** navigation is active but no street name is available
- **THEN** no trip text is set on the travel estimate

#### Scenario: Street name not covered by host ETA card

- **WHEN** navigation is active, a travel estimate is shown by the host, and the current street name is displayed
- **THEN** the street name is an element of the travel-estimate card itself, so no host-rendered UI can cover it

#### Scenario: Street name stays clear when host geometry is unknown

- **WHEN** navigation is active and the host has not delivered a stable area
- **THEN** the street name is still shown in the travel-estimate card, positioned by the host, with no dependence on the surface-rect geometry

#### Scenario: Street name in host ETA card when map area is not safe

- **WHEN** navigation is active, a travel estimate is shown by the host, and the host delivers no stable or visible area that clears the surface bottom
- **THEN** the street name is rendered inside the host's travel-estimate card via `setTripText`, and no street-name label is drawn on the map surface

#### Scenario: Street name on map when host area is safe

- **WHEN** navigation is active, a travel estimate is shown by the host, and the host delivers a stable or visible area that clears the surface bottom
- **THEN** the street name is still rendered in the travel-estimate card via `setTripText` and never on the map surface (a bottom-clear area does not move the street name off the card)

#### Scenario: No street-name label on the map surface

- **WHEN** navigation is active
- **THEN** no street-name text is drawn on the map surface (the map surface carries only the compass rose, speed badge and attribution)

#### Scenario: Off-route street name from GPS lookup

- **WHEN** navigation is active and the vehicle is off the planned route (no road info in the navigation state)
- **THEN** the street name comes from the throttled bearing-aware road lookup at the GPS position and updates the ETA-card trip text

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

The system SHALL let the user stop active navigation from the car display at any time via the host ETA card stop button or via system back, and SHALL NOT show an explicit stop or back button in the navigation map action strip.

#### Scenario: Stop navigation from car

- **WHEN** the user presses system back during navigation
- **THEN** navigation stops on the car display and the screen returns to the root menu

#### Scenario: Stop action shown on navigation map

- **WHEN** the user is navigating on the car display with a travel estimate
- **THEN** the host ETA card shows a stop action and the navigation map action strip shows no stop or back button

#### Scenario: Stop action stops navigation

- **WHEN** the user activates the stop action on the host ETA card while navigating
- **THEN** navigation stops on the car display and the screen returns to the root menu

#### Scenario: System back still leaves with the stop action shown

- **WHEN** the user is navigating and the host ETA card shows the stop action
- **THEN** pressing system back still stops navigation and returns the screen to the root menu

### Requirement: Smooth follow-mode scrolling during navigation

The system SHALL scroll the navigation map smoothly to follow the vehicle between GPS fixes while navigating in follow mode (no per-fix snap), using the same display extrapolation and correction easing as free driving, and SHALL keep the navigation engine fed exclusively with real fixes. The per-fix viewport commit SHALL anchor the follow render target on the displayed (eased predicted) position, never on the raw fix, so a fix never steps the map by the extrapolation lead; and the rotation SHALL be re-committed only beyond the shared heading deadband, so a sub-degree heading change does not force a full native render. Free driving SHALL observe the same two rules (parity within the car app).

#### Scenario: Map glides between fixes

- **WHEN** the vehicle moves during navigation in follow mode and GPS fixes arrive about once per second
- **THEN** the map scrolls continuously toward the predicted vehicle position between fixes
- **AND** it does not jump to the raw fix position in a single frame

#### Scenario: Fix correction eased

- **WHEN** a new GPS fix arrives while the navigation map is scrolled ahead of the true position
- **THEN** the map eases smoothly onto the fix position over approximately 200-300 ms
- **AND** the vehicle marker stays visually attached to the scrolled map

#### Scenario: Smooth follow without GPS speed

- **WHEN** the vehicle moves during navigation but the fixes carry no GPS speed
- **THEN** the map still scrolls smoothly using the speed derived from the movement between fixes

#### Scenario: Manual pan suspends smooth follow

- **WHEN** the user pans the map during navigation
- **THEN** smooth follow is suspended and the map stays where the user left it
- **AND** it resumes smoothly without a snap when the user exits pan mode

#### Scenario: Fix does not step the navigation map

- **WHEN** a GPS fix arrives during navigation while the displayed position leads the fix
- **THEN** the committed frame SHALL be centered on the anchor center of the displayed position
- **AND** the map SHALL NOT step by the lead at the commit
- **AND** free driving SHALL behave identically for the same fix stream

### Requirement: Vehicle anchor during navigation

The system SHALL keep the vehicle marker on the navigation surface during follow mode at the configured routing anchor position instead of at the screen center. The anchor is one of 15 positions on a 5×3 grid (horizontal 10/30/50/70/90% of the surface width, vertical 10/50/90% of the surface height). The default routing anchor is center/center (50% width, 50% height), which reproduces the pre-feature framing exactly. To place the marker at the anchor, the map render target SHALL be shifted so the vehicle's geographic position projects to the anchor under the current viewport rotation — in heading-up AND north-up navigation.

#### Scenario: Default anchor reproduces today's framing

- **GIVEN** the routing anchor is at its default center/center
- **WHEN** navigation is active in follow mode
- **THEN** the vehicle marker projects to the center of the navigation surface
- **AND** the map framing is identical to navigation without anchor presets

#### Scenario: Road-ahead bias via bottom anchor

- **GIVEN** the user selected the bottom-center anchor (50% width, 90% height) for routing
- **WHEN** navigation is active in follow mode
- **THEN** the vehicle marker stays at the bottom-center anchor
- **AND** more of the map ahead of the vehicle is visible than behind it

#### Scenario: Panel clearance via side anchor

- **GIVEN** the host draws its route-status/turn-instruction UI over the left part of the surface
- **GIVEN** the user selected a right-side anchor (e.g. 70% or 90% width) for routing
- **WHEN** navigation is active in follow mode
- **THEN** the vehicle marker stays clear of the host UI region
- **AND** the visible map area on the open side is used for the road ahead

#### Scenario: Marker stays on anchor during heading-up rotation

- **WHEN** navigation is active with heading-up orientation and the vehicle bearing changes
- **THEN** the vehicle marker keeps projecting to the chosen anchor position
- **AND** the map content rotates about the display with the bearing

#### Scenario: Anchor restored after manual pan

- **WHEN** the user pans the map during navigation (follow suspended)
- **AND** the user then stops panning
- **THEN** follow mode re-engages and the vehicle marker returns to the routing anchor without a snap

### Requirement: Routing anchor applies when the setting changes during navigation

The navigation follow-mode map SHALL re-frame at the routing anchor when the setting changes during active navigation: the change SHALL apply without restarting the navigation screen and without waiting for the next maneuver change.

#### Scenario: Anchor change applies during navigation

- **WHEN** the driver changes the routing anchor in the settings dialog during an active navigation session
- **AND** returns to the navigation map
- **THEN** follow mode re-frames the map at the new routing anchor

#### Scenario: Anchor applies on resume without a maneuver change

- **GIVEN** navigation is active and no maneuver change is pending
- **WHEN** the driver returns from the settings dialog with a new routing anchor
- **THEN** the map re-frames at the new anchor without requiring the next instruction change to trigger it
