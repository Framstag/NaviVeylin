# Spec Delta — route-planning-session

## MODIFIED Requirements

### Requirement: Session lifetime and its only exits

While a route-planning session is active the system SHALL keep the session open until the user starts navigation or ends the session. Ending the session SHALL clear the calculated route from the map (polyline and start/target markers), reset the session state, and remove the session's surface: no card and no remaining affordance SHALL stay on the map after the session ended. A route SHALL NOT remain on the phone map after its session has ended.

- Starting navigation SHALL end the session and hand the map to navigation.
- Ending the session (Cancel, the system back gesture, the header's close control, or the explicit End action) SHALL return the map to the mode the session was opened from.
- The explicit End action SHALL be rendered in **both** card states of the phone overlay — as a labelled action next to the session's other actions in max, and as a control in the min strip's step row (which has no header) — so a route can be left without starting navigation from wherever the user is (owner finding, 2026-10-03: "I do not see how I can leave the stateful route analysis without starting the navigation"). The header's close control SHALL be an End action as well: it carries the same meaning in both card states, and collapsing to min is a separate control.
- While turn-by-turn navigation is active the route belongs to navigation: ending a session opened during navigation SHALL NOT clear the route.

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

## ADDED Requirements

### Requirement: Session overlay anchors (max and min)

The planning overlay SHALL be collapsible on the phone between **two** anchors — max and min — rendered as a fixed-height bottom card at the screen's bottom edge. Each anchor SHALL have a known height, so the map area it leaves free is known: **min** SHALL be a strip that hugs its content (at most 18 % of the screen height, capped at one control row) and **max** SHALL take at most 45 % of the screen height, so the map keeps **at least 55 % of the screen height** free. With no route calculated the card SHALL stay in max: there is nothing to minimise to. The card SHALL report the height it actually has, including min's content-driven one, to the map screen for the route overview fit and for the control column's inset. Collapsing the overlay SHALL NOT end the session, and it SHALL NOT be a way to free the whole map: ending the session is the way to hand the map back. (Device findings, 2026-10-03: the content-driven sheet covered 36–48 % of the screen in its compact state and ~97 % expanded, so neither "compact frees the map" nor a nominal fit fraction could hold, and the card rendered at the top of the screen because `BoxWithConstraints` without `fillMaxSize()` is content-sized. Owner directive, 2026-10-05: the third, hidden anchor and the route-ready affordance it required are gone — the card is the whole phone surface, and the flow either ends at it or continues with navigation.)

#### Scenario: Compact anchor frees the map

- **WHEN** a route is calculated and the overlay is in max
- **AND** the user collapses it or selects a step from the list
- **THEN** only the analysed step's instruction, its position and the two step controls SHALL be shown
- **AND** the card SHALL occupy only its min height, leaving the map free for gestures
- **AND** the session SHALL remain active with the route still drawn

#### Scenario: Expanded anchor keeps the map visible

- **WHEN** the card is in max
- **THEN** the card SHALL grow to its max height and stop there
- **AND** the map SHALL keep at least 55 % of the screen height for the route
- **AND** the route's step list SHALL start on screen and scroll inside the card
- **AND** the start marker, the target marker and the polyline SHALL be fitted into that free area

#### Scenario: Expanding does not end the session

- **WHEN** the overlay is collapsed at either anchor
- **AND** the user expands it again
- **THEN** the session state (start, destination, vehicle, route, steps) SHALL be unchanged

### Requirement: Planning card content and its pinned actions

In **max** the card SHALL carry the location fields, the route's step list (scrolling inside the card) and the session actions, and in **min** only the analysed step, its position "i of n", the two step controls and the session's End control (min renders no header, so its exit lives in this row), with the step name as the way back to max. The session actions SHALL stay reachable without scrolling: in max they SHALL be pinned in their own band at the card's bottom edge, and two actions of the same state SHALL share one row. While a route is reviewed the two location fields SHALL collapse into one read-only line (tapping it opens the fields for editing), so the route's list starts on screen; the vehicle selector is a pre-calculation control and SHALL give its room to the list once a route exists. The overlay SHALL start a route on its **current step**, the first step the native layer gave a distance or a time for: the start line carries neither, and presenting it left the min overlay's instruction detail empty (owner finding, 2026-10-03). The secondary clear action SHALL keep a row of its own only in the docked layout.

#### Scenario: The actions stay reachable

- **WHEN** a route is calculated and the card is at either anchor
- **THEN** the primary session action SHALL be visible without scrolling the card
- **AND** the card's other content SHALL scroll above it

#### Scenario: The route line gives way to editing

- **WHEN** a route is reviewed and the card is in max
- **THEN** the start and destination SHALL appear as one read-only line
- **AND** the route's step list SHALL start on screen
- **WHEN** the user taps that line
- **THEN** the editable fields SHALL appear with the destination field focused
- **AND** the edit state SHALL stay open until the field loses focus

#### Scenario: The card reports the height it has

- **WHEN** the card is in max or min
- **THEN** it SHALL report the covered pixel height it actually occupies to the map screen
- **AND** the route overview fit and the right-side control column's inset SHALL use that number

#### Scenario: The overlay never covers the map controls

- **WHEN** the session's card is in max or min on the phone
- **THEN** the right-side control column (compass, speed, location options, zoom) SHALL sit fully above the card
- **AND** its stack order and spacing SHALL be unchanged

### Requirement: Ending the session removes its surface

Ending a session SHALL close the surface the session was using at the same moment the session ends: the phone card SHALL disappear, the map SHALL reclaim the full height the overlay reported as covered, and no replacement affordance (a pill, badge or floating control) SHALL remain. A surface that outlives its session SHALL NOT be shown, and SHALL NOT be re-openable into a session-less overlay.

#### Scenario: The header close ends the session

- **WHEN** a route is calculated and the card is in max
- **AND** the user taps the card's close control in the header
- **THEN** the session SHALL end and the route SHALL be cleared
- **AND** the card SHALL be gone, with no further affordance left on the map
- **WHEN** the user then opens the session again
- **THEN** it SHALL open empty, in the editing state

#### Scenario: Ending from min leaves nothing behind

- **WHEN** a route is calculated, the card is in min, and the user activates its End control
- **THEN** the session SHALL end and the card SHALL be gone
- **AND** the map SHALL accept gestures over its whole area

#### Scenario: Grace expiry closes an open surface

- **WHEN** the session is in its stopped state with the card on screen
- **AND** the grace period elapses without Restart or End
- **THEN** the session SHALL end and the route SHALL be cleared
- **AND** the card SHALL be gone with no affordance left in its place

#### Scenario: No surface survives the session

- **WHEN** the session has ended for any reason (Start Navigation, End, Cancel, system back, grace expiry)
- **THEN** the map SHALL show no session card and no route-ready affordance
- **AND** the covered-height value the overlay reported SHALL return to zero

## REMOVED Requirements

### Requirement: Session overlay anchors

**Reason**: Replaced by "Session overlay anchors (max and min)" plus "Planning card content and its pinned actions" in this change. The third, hidden anchor and the route-ready affordance it required are removed: with a session active the card is the whole phone surface, and the flow either ends at the card (back to browse) or continues with navigation (owner directive, 2026-10-05). The requirement also bundled three separate behaviours in one paragraph, which the two replacement requirements state one at a time.

**Migration**: The card anchors keep their meanings under the new requirement — max carries the fields, the step list and the actions, min only the analysed step with its controls and the End control, a selected step collapses to min and the step name expands back. The hidden anchor's "whole map free" state is dropped: ending the session frees the whole map (spec `route-planning-session` — Ending the session removes its surface). Every scenario of the removed requirement is carried over by name, except the hidden anchor's, whose behaviour no longer exists.
