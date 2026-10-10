# route-planning-session Specification

## Purpose
Defines the phone route-planning session: the overlay in which a user builds, reviews and ends a route, the anchors it can take, the camera it holds while active, and the two ways it may end (Start Navigation or Cancel) including the short grace after navigation is stopped.

## Requirements

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

In **max** the card SHALL carry the location fields, the route's step list (scrolling inside the card) and the session actions, and in **min** only the analysed step, its position "i of n", the two step controls and the session's End control (min renders no header, so its exit lives in this row), with the step name as the way back to max. The session actions SHALL stay reachable without scrolling: in max they SHALL be pinned in their own band at the card's bottom edge, and two actions of the same state SHALL share one row. The pinned band SHALL take the height its own content needs at the **current font scale** and the card's scrolling content SHALL give that space up — a fixed reservation for the band squeezed the labelled session action once a large system font scale grew the band's labels (font scale 2.0: measured on the device as the action leaving the card entirely, and on the host as the action 42.7 dp tall where its own content needs 53.3 dp — `TODO.md` §138). While a route is reviewed the two location fields SHALL collapse into one read-only line (tapping it opens the fields for editing), so the route's list starts on screen; the vehicle selector is a pre-calculation control and SHALL give its room to the list once a route exists. The overlay SHALL start a route on its **current step**, the first step the native layer gave a distance or a time for: the start line carries neither, and presenting it left the min overlay's instruction detail empty (owner finding, 2026-10-03). The secondary clear action SHALL keep a row of its own only in the docked layout.

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
- **Case** `com.naviveylin.ui.route.RoutePanelOverlayAnchorTest#the card reports the covered height it occupies` (the report), `com.naviveylin.ui.map.MapCanvasViewModelRouteFitTest#reportedCardHeight_fitUsesTheFreeMapArea` and `com.naviveylin.ui.map.MapLayerStackComposeTest#the right-side control column sits fully above the session card` (the fit and the inset using it); the screen's wiring between them is the device task 5.1

#### Scenario: The overlay never covers the map controls

- **WHEN** the session's card is in max or min on the phone
- **THEN** the right-side control column (compass, speed, location options, zoom) SHALL sit fully above the card
- **AND** its stack order and spacing SHALL be unchanged
- **Case** `com.naviveylin.ui.map.MapLayerStackComposeTest#the right-side control column sits fully above the session card` (the disjointness, with the screen's inset wiring reproduced host-side), `com.naviveylin.ui.map.MapRightWidgetColumnTest#standardColumnShowsCompassSpeedGearAndZoom` and `#zoomSitsBelowAllOtherControls` (the order and the spacing)

#### Scenario: The pinned band keeps the height its content needs

- **WHEN** the card is in max on a calculated route and the system font scale is 2.0
- **THEN** the labelled session action SHALL keep its own height — at least its 48 dp tap target — and its bounds SHALL lie inside the card
- **AND** the card's scrolling content SHALL give up that space instead of the band
- **AND** the card SHALL stay within its 45 % share of the screen height
- **Case** `com.naviveylin.ui.route.RoutePanelActionBandScaleTest#the labelled End action keeps its tap target inside the card at font scale 2`

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

While turn-by-turn navigation is active, a session opened on the map SHALL be a read-only view of the route: the start and destination fields SHALL NOT be editable, and the session SHALL NOT offer to calculate or clear the route. It SHALL offer the analysis of a step through whichever selector the layout provides — the step navigator on the phone, the step list in a docked wide layout — together with the route summary and the Stop Navigation action. Whenever the session carries no route of its own, the review SHALL present the route navigation is running on: its summary and its steps SHALL be shown, so the read-only review is never an empty panel with editable-looking fields.

#### Scenario: Fields are not editable during navigation

- **WHEN** turn-by-turn navigation is active and the user opens the session
- **THEN** the start and destination fields SHALL NOT accept input
- **AND** the calculate and clear actions SHALL NOT be offered

#### Scenario: Analysis stays available during navigation

- **WHEN** turn-by-turn navigation is active and the session is open
- **THEN** the step navigator SHALL be shown (a docked wide layout shows the step list) and a step SHALL be analysable
- **AND** Stop Navigation SHALL be offered

#### Scenario: The review shows the route navigation is running

- **WHEN** turn-by-turn navigation is active
- **AND** the user opens a session that carries no route of its own
- **THEN** the review SHALL show the route navigation is running
- **AND** its route summary (the route's distance and duration) SHALL be shown
- **AND** its steps SHALL be listed in the step navigator with the current navigation step marked

#### Scenario: The review's Stop Navigation is reachable

- **WHEN** turn-by-turn navigation is active and the session is open
- **THEN** a Stop Navigation action SHALL be part of the session's actions
- **AND** activating it SHALL stop navigation

#### Scenario: The review is the surface while it is open

- **WHEN** turn-by-turn navigation is active and the user opens the session — in whichever anchor the
  session opens
- **THEN** the review SHALL be visible on the phone's bottom band, with its own actions (the step
  navigator's controls, Stop Navigation) drawn and tappable
- **AND** the routing status card SHALL NOT occupy that band while the session's card is shown, and
  SHALL return when the session's surface closes

### Requirement: Grace period after navigation is stopped

When the user stops navigation during a session the session SHALL enter a stopped state: the route SHALL stay drawn, the camera SHALL stay free, and the overlay SHALL offer Restart and End. The stopped state SHALL end within a bounded grace period and SHALL end immediately on Restart or End. When the grace period expires the session SHALL end and the route SHALL be cleared. Every phone stop control SHALL enter this state while a session is open — the routing status card's stop control and the review's Stop Navigation included. Stopping navigation while no session is open SHALL end navigation and clear the route without a stopped state.

#### Scenario: Stop shows Restart and End

- **WHEN** the user taps Stop Navigation during a session
- **THEN** the session SHALL enter the stopped state with the route still drawn
- **AND** a Restart action and an End action SHALL be presented

#### Scenario: The status card's stop enters the stopped state

- **WHEN** a session is open during active navigation
- **AND** the user taps the routing status card's stop control
- **THEN** the session SHALL enter the stopped state with the route still drawn
- **AND** a Restart action and an End action SHALL be presented

#### Scenario: Stopping with no session open has no grace

- **WHEN** no session is open and the user stops navigation from the routing status card
- **THEN** navigation SHALL end and the route SHALL be cleared
- **AND** no stopped state and no grace period SHALL be involved

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

### Requirement: The planning card's route statistics state the route's length
When a route exists, the planning card SHALL report the route's total distance and its duration. The reported distance SHALL be the route's length as the card's own step list sums it (spec: `osmscout-jni` — One route length for a calculated route), so the card's statistic and the steps below it state one length.

#### Scenario: Headline agrees with the card's step list

- **WHEN** a route exists and the planning card shows the route's distance
- **THEN** the shown distance SHALL equal the sum of the card's listed steps' distances within rounding
- **AND** the card SHALL NOT show a distance that disagrees with that sum by a ratio

#### Scenario: A long route's statistic is not a second total

- **WHEN** a long intercity route (tens of kilometres) is calculated
- **THEN** the card's distance SHALL agree with the sum of its step list within rounding
- **AND** the two SHALL NOT differ by a factor (measured 2026-10-05: 1.34× between the card statistic and the step legs on a ~70 km route)

#### Scenario: The statistic follows the route that is shown

- **WHEN** a route is replaced by a newly calculated one
- **THEN** the card's distance SHALL be the new route's length
- **AND** it SHALL NOT keep the previous route's statistic
