# Spec Delta

## Purpose

Defines the phone route-planning session: the overlay in which a user builds, reviews and ends a route, the anchors it can take, the camera it holds while active, and the two ways it may end (Start Navigation or Cancel) including the short grace after navigation is stopped.

## ADDED Requirements

### Requirement: Session lifetime and its only exits

While a route-planning session is active the system SHALL keep the session open until the user starts navigation or ends the session. Ending the session SHALL clear the calculated route from the map (polyline and start/target markers) and reset the session state. A route SHALL NOT remain on the phone map after its session has ended.

- Starting navigation SHALL end the session and hand the map to navigation.
- Ending the session (Cancel, the system back gesture, or an explicit End action) SHALL return the map to the mode the session was opened from.
- The explicit End action SHALL be rendered in **both** card states of the phone overlay — as a labelled action next to the session's other actions in max, and as a control in the min strip's step row (which has no header) — so a route can be left without starting navigation from wherever the user is (owner finding, 2026-10-03: "I do not see how I can leave the stateful route analysis without starting the navigation"; the header's close only minimises, and "Start/Ziel ändern" keeps the session).
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

### Requirement: Session overlay anchors

The planning overlay SHALL be collapsible on the phone between three anchors — max, min and hidden — rendered as a **fixed two-state bottom card** plus the hidden affordance. Each state SHALL have a fixed height, so the map area the overlay leaves free is known: **min** SHALL be a small strip that hugs its content (at most 18 % of the screen height, capped at one control row) carrying only the analysed step — its instruction, its position "i of n", the two step controls and the session's End control (min renders no header, so its exit lives in this row) — with the step name as the way back to max — and **max** SHALL take at most 45 % of the screen height, carrying the location fields, the route's step list (scrolling inside the card) and the session actions, so the map keeps **at least 55 % of the screen height** free. With no route calculated the card SHALL stay in max: there is nothing to minimise to. The session actions SHALL stay reachable without scrolling: in max they SHALL be pinned in their own band at the card's bottom edge, and two actions of the same state SHALL share one row. The card SHALL sit at the screen's bottom edge. The planning overlay SHALL start a route on its **current step**, which is the first step the native layer gave a distance or a time for: the start line carries neither, and presenting it left the min overlay's instruction detail empty (owner finding, 2026-10-03). The card SHALL report the height it has, including min's content-driven one, to `MapCanvasViewModel` for the overview fit. While a route is reviewed the two location fields SHALL collapse into one read-only line (tapping it opens the fields for editing), so the route's list starts on screen; the vehicle selector is a pre-calculation control and SHALL give its room to the list once a route exists. The hidden anchor SHALL leave the whole map free behind a single route-ready affordance. The secondary clear action SHALL keep a row of its own only in the docked layout (the session's cancel exit already clears the route on the phone). Collapsing the overlay SHALL NOT end the session. (Device findings, 2026-10-03: the content-driven sheet covered 36–48 % of the screen in its compact state and ~97 % expanded, so neither "compact frees the map" nor a nominal fit fraction could hold; the same runs showed the statistics and then the primary action being pushed out of a fixed card by stacked full-width buttons, and the card rendering at the top of the screen because `BoxWithConstraints` without `fillMaxSize()` is content-sized. Owner directive, 2026-10-03: max shows the route list at the bottom, min only the current position with the step controls, selecting a list entry switches to min, and tapping the step name switches back.)

#### Scenario: The overlay never covers the map controls

- **WHEN** the session's card is in max or min on the phone
- **THEN** the right-side control column (compass, speed, location options, zoom) SHALL sit fully above the card
- **AND** its stack order and spacing SHALL be unchanged

#### Scenario: The actions stay reachable

- **WHEN** a route is calculated and the card is at either state
- **THEN** the primary session action SHALL be visible without scrolling the card
- **AND** the card's other content SHALL scroll above it

#### Scenario: The route line gives way to editing

- **WHEN** a route is reviewed and the card is in max
- **THEN** the start and destination SHALL appear as one read-only line
- **AND** the route's step list SHALL start on screen
- **WHEN** the user taps that line
- **THEN** the editable fields SHALL appear with the destination field focused
- **AND** the edit state SHALL stay open until the field loses focus

#### Scenario: Compact anchor frees the map

- **WHEN** a route is calculated and the overlay is in max
- **AND** the user minimises it or selects a step from the list
- **THEN** only the analysed step's instruction, its position and the two step controls SHALL be shown
- **AND** the card SHALL occupy only its min height, leaving the map free for gestures
- **AND** the session SHALL remain active with the route still drawn

#### Scenario: Expanded anchor keeps the map visible

- **WHEN** the card is in max
- **THEN** the card SHALL grow to its max height and stop there
- **AND** the map SHALL keep at least 55 % of the screen height for the route
- **AND** the route's step list SHALL start on screen and scroll inside the card
- **AND** the start marker, the target marker and the polyline SHALL be fitted into that free area

#### Scenario: Hidden anchor leaves the whole map free

- **WHEN** the session is active and the user collapses the overlay to the hidden anchor
- **THEN** only a route-ready affordance SHALL remain visible
- **AND** the map SHALL accept pan and zoom gestures over its full area

#### Scenario: Expanding does not end the session

- **WHEN** the overlay is collapsed at any anchor
- **AND** the user expands it again
- **THEN** the session state (start, destination, vehicle, route, steps) SHALL be unchanged

### Requirement: Overlay docks when width allows

When the available width allows a side-by-side layout the planning overlay SHALL dock to the side as a panel while the map keeps the remaining area, instead of covering the map. The docked panel SHALL present the same content and the same session actions as the expanded phone anchor.

#### Scenario: Wide layout docks the panel

- **WHEN** the session is open on a surface whose width exceeds its height
- **THEN** the planning panel SHALL be docked to one side
- **AND** the map SHALL occupy the remaining area
- **AND** the location fields, distance, duration, step list and Start/Cancel actions SHALL all be visible without scrolling the map

### Requirement: Session holds the camera while active

While the session is active the session SHALL own the route overlay and the camera: the route overview fit SHALL be applied by the session, and the camera SHALL NOT be moved by follow mode or by automatic fitting. This SHALL NOT require a fourth map mode; the session is a lease on top of BROWSE, FREE_DRIVE and NAVIGATION.

#### Scenario: Route overview follows the reported card height

- **WHEN** a route calculation completes in the session
- **THEN** the map SHALL be positioned so that the route start marker, the target marker and the polyline are visible in the area above the card
- **AND** the fit SHALL use the height the card reports for the state it is in
- **AND** a change of the card's state SHALL re-fit for the newly free area

#### Scenario: Planning while free-driving does not move the map

- **WHEN** the user opens the session while the map is following (FREE_DRIVE)
- **THEN** the map SHALL stop following while the session is active
- **AND** no follow-triggered camera move SHALL occur during the session
- **AND** ending the session SHALL leave the map at the position the session left it in, in the mode the session was opened from

### Requirement: Reviewing a route during navigation is read-only

While turn-by-turn navigation is active, a session opened on the map SHALL be a read-only view of the route: the start and destination fields SHALL NOT be editable, and the session SHALL NOT offer to calculate or clear the route. It SHALL offer the analysis of a step through whichever selector the layout provides — the step navigator on the phone, the step list in a docked wide layout — together with the route summary and the Stop Navigation action.

#### Scenario: Fields are not editable during navigation

- **WHEN** turn-by-turn navigation is active and the user opens the session
- **THEN** the start and destination fields SHALL NOT accept input
- **AND** the calculate and clear actions SHALL NOT be offered

#### Scenario: Analysis stays available during navigation

- **WHEN** turn-by-turn navigation is active and the session is open
- **THEN** the step navigator SHALL be shown (a docked wide layout shows the step list) and a step SHALL be analysable
- **AND** Stop Navigation SHALL be offered

### Requirement: Grace period after navigation is stopped

When the user stops navigation during a session the session SHALL enter a stopped state: the route SHALL stay drawn, the camera SHALL stay free, and the overlay SHALL offer Restart and End. The stopped state SHALL end within a bounded grace period and SHALL end immediately on Restart or End. When the grace period expires the session SHALL end and the route SHALL be cleared.

#### Scenario: Stop shows Restart and End

- **WHEN** the user taps Stop Navigation during a session
- **THEN** the session SHALL enter the stopped state with the route still drawn
- **AND** a Restart action and an End action SHALL be presented

#### Scenario: Restart resumes navigation

- **WHEN** the session is in the stopped state
- **AND** the user taps Restart
- **THEN** navigation SHALL start again on the same route
- **AND** the route SHALL NOT need recalculation

#### Scenario: Grace expiry clears the route

- **WHEN** the session is in the stopped state
- **AND** the grace period elapses without Restart or End
- **THEN** the session SHALL end
- **AND** the route polyline and markers SHALL be removed from the map

#### Scenario: End now clears immediately

- **WHEN** the session is in the stopped state
- **AND** the user taps End
- **THEN** the session SHALL end and the route SHALL be cleared without waiting for the grace period

#### Scenario: Grace state does not survive process death

- **WHEN** the app is killed and restarted while a session is in the stopped state
- **THEN** no session SHALL be restored
- **AND** no route polyline or markers SHALL be drawn
