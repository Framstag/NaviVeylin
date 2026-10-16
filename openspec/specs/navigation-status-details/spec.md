# navigation-status-details Specification

## Purpose

Lets the driver expand the routing status card during active navigation into a full-screen view that shows the route description list with the current step highlighted, without leaving the map.

## Requirements

### Requirement: Routing status card is clickable

During active navigation, the phone's routing status card SHALL be tappable, and the stop control it carries SHALL be a tap target of its own: a tap inside the control's hit area SHALL end navigation, and a tap anywhere else on the card SHALL open the full-screen route description. The two hit areas SHALL NOT overlap. The control's hit area SHALL be at least 48 dp in each dimension.

#### Scenario: Tap opens full-screen details

- **WHEN** the user taps the routing status card during active navigation
- **AND** the tap is outside the stop control's hit area
- **THEN** a full-screen view SHALL open showing the route description

#### Scenario: Tap does not stop navigation

- **WHEN** the user taps the routing status card outside the stop control's hit area
- **THEN** navigation SHALL continue uninterrupted
- **AND** the stop-navigation button SHALL remain available in the expanded view

#### Scenario: Tap at the stop control's centre ends navigation

- **WHEN** the routing status card shows the stop control during active navigation
- **AND** the user taps the centre of the control's hit area
- **THEN** navigation SHALL end
- **AND** the expanded route description SHALL NOT open

#### Scenario: The tap is received by the control, not by the card

- **WHEN** the user taps the centre of the stop control's hit area during active navigation
- **THEN** the navigation stop action SHALL be the action invoked
- **AND** the expanded route description SHALL NOT be opened
- **AND** the card's own action SHALL be the target of a tap outside the control's hit area

#### Scenario: The two targets do not overlap

- **WHEN** the routing status card is shown during active navigation
- **THEN** the stop control's hit area SHALL lie outside the card's own tap area
- **AND** the hit areas SHALL be exposed as distinct accessibility targets, each with its own bounds

#### Scenario: Stop control is large enough to hit

- **WHEN** the routing status card is shown during active navigation
- **THEN** the stop control's hit area SHALL be at least 48 dp wide and 48 dp high

#### Scenario: The expanded view's stop control is its own target too

- **WHEN** the full-screen route description is open during active navigation
- **AND** the user taps the centre of its stop control's hit area
- **THEN** navigation SHALL end
- **AND** the tap SHALL NOT be handled by any surrounding tap area of the expanded view

#### Scenario: The car card carries no stop control

- **WHEN** the routing status card is shown on the Android Auto / Automotive surface
- **THEN** it SHALL NOT show a stop control
- **AND** this deviation from the phone layout is by design, not a parity defect

### Requirement: Full-screen view shows status content

The expanded view SHALL keep the routing status content visible: current road name and the ETA / remaining time / remaining distance stats. The current and max speed are no longer part of the status content — they are shown by the on-map speed widget.

#### Scenario: Status content preserved

- **WHEN** the full-screen view is open
- **THEN** the current road name SHALL be shown
- **AND** the ETA, remaining time, and remaining distance SHALL be shown

#### Scenario: Speed not shown in expanded view

- **WHEN** the full-screen view is open
- **THEN** the current and max speed SHALL NOT be shown in the expanded view

### Requirement: Route description list

The expanded view SHALL show the route description list, styled like the route details view (`RouteSummaryDialog`): each step with its turn icon, distance, and instruction text. A step's distance SHALL be the distance of that step's own segment — the leg leading to its manoeuvre — and each step SHALL additionally show the time for its segment, so both values match the per-step values shown in the route summary (spec: `routing-summary`). A value whose meaning is a route-start-to-here distance next to a leg time SHALL NOT be shown. A roundabout-exit step's turn icon SHALL carry that step's own exit ordinal.

#### Scenario: Steps listed in order

- **WHEN** the full-screen view is open
- **THEN** each route instruction SHALL be listed in order from start to destination
- **AND** each step SHALL show the turn type icon, distance, and instruction text

#### Scenario: Per-step time shown

- **WHEN** the full-screen view is open
- **THEN** each step SHALL show the time for its segment (e.g., "5 min")
- **AND** the time SHALL match the per-step time shown in the route summary step list

#### Scenario: Per-step distance is the step's own segment

- **WHEN** the full-screen view is open for a 17.3 km route
- **THEN** each step's distance SHALL be the leg leading to that step's manoeuvre
- **AND** the distances SHALL NOT be distances measured from the route's start

#### Scenario: List scrollable

- **WHEN** the instruction list exceeds the visible area
- **THEN** the list SHALL be scrollable

#### Scenario: A roundabout exit step shows its own exit number

- **WHEN** the full-screen view is open and a step is a roundabout exit carrying an ordinal
- **THEN** that step's icon SHALL carry that step's ordinal
- **AND** it SHALL NOT carry the ordinal of another step in the list
- **Case**: `app: NavigationDetailsOverlayTest` "a roundabout exit step icon carries that step's number"

#### Scenario: A roundabout exit step without an ordinal

- **WHEN** the full-screen view is open and a roundabout exit step carries no ordinal
- **THEN** that step's icon SHALL be the plain roundabout leave icon it showed before the number existed
- **Case**: `app: NavigationDetailsOverlayTest` "a roundabout exit step without an ordinal keeps the plain icon"

### Requirement: Current step selected at top

The current navigation step SHALL be visually highlighted and shown at the top of the route description list.

#### Scenario: Current step highlighted

- **WHEN** the full-screen view is open during active navigation
- **THEN** the current navigation step SHALL be visually highlighted (e.g. primary container background, bold text)

#### Scenario: Current step at top of list

- **WHEN** the full-screen view is open
- **THEN** the list SHALL be scrolled so the current step is the first visible item

#### Scenario: Highlight follows progress

- **WHEN** the user progresses to the next navigation step while the view is open
- **THEN** the highlighted step SHALL advance to the new current step
- **AND** the list SHALL scroll to keep the new current step at the top

### Requirement: Dismiss returns to map

Dismissing the expanded view SHALL return to the map with the routing status card still visible.

#### Scenario: Dismiss via close button

- **WHEN** the user taps the close (X) button
- **THEN** the full-screen view SHALL close
- **AND** the routing status card SHALL remain visible

#### Scenario: Dismiss via back gesture

- **WHEN** the user presses the system back button
- **THEN** the full-screen view SHALL close
- **AND** the routing status card SHALL remain visible

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

### Requirement: The status card's stop is the phone's shared stop path

A tap on the routing status card's stop control SHALL end navigation through the same phone stop path every other stop control uses. A route-planning session that is open when navigation stops SHALL enter its stopped state (spec: `route-planning-session` — Grace period after navigation is stopped) instead of being cleared behind its back, and the map SHALL return to the mode that was active before navigation started (spec: `map-modes` — Map mode model).

#### Scenario: An open session enters its stopped state

- **WHEN** a route-planning session is open during active navigation
- **AND** the user taps the card's stop control
- **THEN** navigation SHALL end
- **AND** the session SHALL enter its stopped state with the route still drawn
- **AND** a Restart action and an End action SHALL be presented

#### Scenario: No session, no grace

- **WHEN** no route-planning session is open during active navigation
- **AND** the user taps the card's stop control
- **THEN** navigation SHALL end and the route SHALL be cleared
- **AND** no stopped state SHALL be presented

#### Scenario: The expanded view's stop behaves the same

- **WHEN** a route-planning session is open during active navigation
- **AND** the full-screen route description is open
- **AND** the user taps its stop control
- **THEN** the session SHALL enter its stopped state with the route still drawn

#### Scenario: The map returns to the pre-navigation mode

- **WHEN** the user taps the card's stop control
- **AND** the mode before navigation started was BROWSE
- **THEN** the map mode SHALL be BROWSE
- **AND** the map SHALL be north-up with follow disabled
