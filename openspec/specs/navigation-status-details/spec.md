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

The expanded view SHALL show the route description list, styled like the route details view (`RouteSummaryDialog`): each step with its turn icon, distance, and instruction text. Each step SHALL additionally show the time for its segment, matching the per-step time shown in the route summary.

#### Scenario: Steps listed in order

- **WHEN** the full-screen view is open
- **THEN** each route instruction SHALL be listed in order from start to destination
- **AND** each step SHALL show the turn type icon, distance, and instruction text

#### Scenario: Per-step time shown

- **WHEN** the full-screen view is open
- **THEN** each step SHALL show the time for its segment (e.g., "5 min")
- **AND** the time SHALL match the per-step time shown in the route summary step list

#### Scenario: List scrollable

- **WHEN** the instruction list exceeds the visible area
- **THEN** the list SHALL be scrollable

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

### Requirement: Route progress lines in routing status card

During active navigation, the routing status card SHALL show the driver's progress along the route as two small horizontal lines: one for percent of distance traveled and one for percent of estimated travel time elapsed. The lines SHALL be small in height, SHALL NOT show labels or percent values, and SHALL each carry a small icon for differentiation (distance line, time line). The distance line's total SHALL be the route's length as its step list sums it (spec: `osmscout-jni` — One route length for a calculated route).

#### Scenario: Progress lines always visible during navigation

- **WHEN** navigation is active
- **THEN** the routing status card SHALL show a distance progress line and a time progress line

#### Scenario: Progress lines not shown outside navigation

- **WHEN** navigation is not active
- **THEN** the routing status card SHALL NOT show the progress lines

#### Scenario: Distance line reflects traveled distance

- **WHEN** navigation is active and the driver has traveled part of the route
- **THEN** the distance line SHALL reflect the percent of the total route distance traveled, computed from the remaining distance relative to the total distance
- **AND** that total SHALL be the route's length as its step list sums it, so the line reaches full at the destination

#### Scenario: Time line reflects elapsed time

- **WHEN** navigation is active and part of the estimated travel time has elapsed
- **THEN** the time line SHALL reflect the percent of the estimated travel time elapsed, computed from the elapsed time relative to the estimated total travel time

#### Scenario: Lines differentiated by icon

- **WHEN** the progress lines are displayed
- **THEN** the distance line SHALL carry a distance icon and the time line SHALL carry a time icon

#### Scenario: No labels or percent values shown

- **WHEN** the progress lines are displayed
- **THEN** the lines SHALL NOT show text labels or percent values

#### Scenario: Progress values clamped to valid range

- **WHEN** the computed progress percent is below 0 or above 100
- **THEN** the line SHALL be rendered at the clamped 0–100 position

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
