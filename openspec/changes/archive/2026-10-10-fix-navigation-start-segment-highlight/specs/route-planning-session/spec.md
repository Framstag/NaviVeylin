# Spec Delta — route-planning-session

## MODIFIED Requirements

### Requirement: Session lifetime and its only exits

While a route-planning session is active the system SHALL keep the session open until the user starts navigation or ends the session. Ending the session SHALL clear the calculated route from the map (polyline and start/target markers), reset the session state, and remove the session's surface: no card and no remaining affordance SHALL stay on the map after the session ended. A route SHALL NOT remain on the phone map after its session has ended.

- Starting navigation SHALL end the session and hand the map to navigation.
- Ending the session (Cancel, the system back gesture, the header's close control, or the explicit End action) SHALL return the map to the mode the session was opened from.
- The explicit End action SHALL be rendered in **both** card states of the phone overlay — as a labelled action next to the session's other actions in max, and as a control in the min strip's step row (which has no header) — so a route can be left without starting navigation from wherever the user is (owner finding, 2026-10-03: "I do not see how I can leave the stateful route analysis without starting the navigation"). The header's close control SHALL be an End action as well: it carries the same meaning in both card states, and collapsing to min is a separate control.
- While turn-by-turn navigation is active the route belongs to navigation: ending a session opened during navigation SHALL NOT clear the route.
- The analysis is the session's, not the route's: every exit SHALL end what the session owns besides the route — the analysed step, the camera anchor that moves onto it, and the highlighted segment — and the route that stays for navigation SHALL carry none of them until a session selects a step again (spec: `route-analysis` — An analysis is created only by the selector that owns it).

#### Scenario: Start navigation ends the session

- **WHEN** a route is calculated in the session
- **AND** the user taps Start Navigation
- **THEN** turn-by-turn navigation SHALL start on that route
- **AND** the session SHALL end
- **AND** the route SHALL remain drawn for navigation

#### Scenario: Cancel clears the route

- **WHEN** a route is calculated in the session
- **AND** the user ends the session without starting navigation
- **THEN** the route polyline and the start/target markers SHALL be removed from the map
- **AND** the session state (start, destination, vehicle, steps) SHALL be reset

#### Scenario: The explicit End action is always reachable

- **WHEN** a route is on the map and the session is not navigating
- **THEN** the max card SHALL show the explicit End action beside "Start/Ziel ändern" and "Navigation starten"
- **AND** the min strip SHALL show it in its step row
- **AND** activating either SHALL end the session and clear the route

#### Scenario: System back ends the session

- **WHEN** the session is the topmost open overlay
- **AND** the user performs the system back gesture
- **THEN** the session SHALL end as a cancel
- **AND** the app SHALL NOT exit

#### Scenario: No route outlives its session

- **WHEN** the session has ended
- **THEN** the map SHALL NOT show a route polyline or route start/target markers
- **AND** re-opening a session SHALL start from the empty state

#### Scenario: Ending a review during navigation keeps the route

- **WHEN** turn-by-turn navigation is active
- **AND** the user opens the session, reviews the route, and ends it
- **THEN** navigation SHALL continue on the route
- **AND** the route SHALL remain drawn on the map

#### Scenario: Start navigation ends the session's analysis

- **WHEN** a route is calculated in the session and a step of it is analysed
- **AND** the user taps Start Navigation
- **THEN** the session SHALL end and the route SHALL remain drawn for navigation
- **AND** no step SHALL remain analysed
- **AND** no segment SHALL remain highlighted

#### Scenario: Ending a review during navigation ends the analysis

- **WHEN** turn-by-turn navigation is active
- **AND** a session opened beside it has analysed a step
- **AND** the user ends that session
- **THEN** navigation SHALL continue on the route and the route SHALL remain drawn
- **AND** no step SHALL remain analysed while no session is open
