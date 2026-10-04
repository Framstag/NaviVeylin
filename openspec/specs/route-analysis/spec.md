# route-analysis Specification

## Purpose
Defines how a calculated route is inspected on the map: moving through its steps — on the phone with the session card's step list (its max state) collapsed to a small min overlay after a selection, in a docked wide layout with the same selectable list — moving the camera to the selected manoeuvre, highlighting the polyline segment that belongs to it, and clearing the selection.

## Requirements

### Requirement: Step selection

The session SHALL provide one step selector per state of the overlay: on the phone the **step list** in the card's max state and the **step navigator** (spec: Step navigator) in its min state; a docked wide layout uses the **selectable step list**. At most one step SHALL be analysed at a time. The analysed step SHALL be apparent in the selector that is in use — the marked row in a list, the navigator's contents and position in the min overlay — and SHALL be distinguishable from the navigation step highlight, which the selector may show at the same time. Selecting a step in the phone's list SHALL collapse the card to its min state, so the map with that manoeuvre and its highlighted segment is what the user sees next, and a tap on the step name in the min state SHALL bring the list back. (Owner directive, 2026-10-03: a list at the bottom for the max state, a small overlay with only the current position and the step controls for the min state.)

#### Scenario: The navigator selects a step (phone)

- **WHEN** a route is calculated on the phone
- **AND** the user moves the step navigator forward
- **THEN** the next step SHALL be marked as the analysed step
- **AND** the card SHALL show that step's instruction, distance and duration

#### Scenario: Tapping a step selects it (docked wide layout)

- **WHEN** the step list is displayed in a docked wide layout
- **AND** the user taps a step
- **THEN** that step SHALL be marked as the analysed step
- **AND** the row SHALL be visually distinguished from the other rows

#### Scenario: Selecting a step in the list collapses the card (phone)

- **WHEN** the phone card is in its max state with the route's step list
- **AND** the user taps a step
- **THEN** that step SHALL be the analysed step
- **AND** the card SHALL collapse to its min state
- **AND** the map SHALL show that manoeuvre with its segment highlighted

#### Scenario: The min overlay returns to the list (phone)

- **WHEN** the phone card is in its min state
- **AND** the user taps the analysed step's name
- **THEN** the card SHALL show its max state with the step list again
- **AND** the analysed step SHALL remain the analysed one

#### Scenario: Selecting another step replaces the selection

- **WHEN** a step is analysed
- **AND** the user taps a different step
- **THEN** the newly tapped step SHALL be the analysed step
- **AND** the previously analysed step SHALL no longer be marked

#### Scenario: Selection cleared on route change

- **WHEN** a step is analysed
- **AND** the route is recalculated, cleared, or the session ends
- **THEN** no step SHALL be analysed

### Requirement: Step navigator

The phone's **min overlay** SHALL carry a step navigator for a calculated route: the analysed step's instruction text, its distance and duration, a position indicator of the form "step i of n", and controls that move the analysed step one step back and one step forward. Moving SHALL analyse the target step, so the camera moves to that manoeuvre and its segment is highlighted. The controls SHALL be disabled at the ends of the route. The analysed step SHALL survive a change of the card's state. The navigator SHALL take no vertical space when no route is calculated, and it SHALL keep the analysed step while a route stays on the map. With no step analysed the indicator SHALL name no step. It SHALL be the min overlay's only content besides the card's own frame: the fields, the list and the session actions belong to the max state.

#### Scenario: Next moves one step and analyses it

- **WHEN** step 3 of 12 is analysed
- **AND** the user taps the forward control
- **THEN** step 4 SHALL be analysed
- **AND** the map camera SHALL move to its manoeuvre
- **AND** its segment SHALL be highlighted

#### Scenario: Back at the first step is a no-op

- **WHEN** step 1 of 12 is analysed
- **AND** the user taps the back control
- **THEN** step 1 SHALL stay analysed
- **AND** the camera SHALL NOT move

#### Scenario: Position indicator agrees with the analysed step

- **WHEN** step i of n is analysed
- **THEN** the navigator SHALL show "i of n"
- **AND** the shown instruction SHALL be that step's instruction

#### Scenario: A calculated route starts at its current step

- **WHEN** a route is calculated
- **THEN** the overlay's current step SHALL be the first step that carries a distance or a time
- **AND** the position indicator SHALL name it
- **AND** the map camera SHALL stay with the route overview until a step is selected explicitly

#### Scenario: The analysed step survives the card state change

- **WHEN** step 5 of 12 is analysed
- **AND** the user expands or collapses the session card
- **THEN** step 5 SHALL still be analysed
- **AND** the navigator SHALL still show its instruction and position

#### Scenario: The navigator does not fight navigation

- **WHEN** turn-by-turn navigation is active
- **THEN** the navigator SHALL show the step navigation is on
- **AND** moving it SHALL NOT change the step navigation follows

### Requirement: Analysed step is locatable on the map

Each step of a calculated route SHALL have a position on the map that agrees with the drawn polyline, so that analysing a step can show where that manoeuvre is. A step SHALL own the polyline leg that **leads to** its manoeuvre — from the previous manoeuvre's vertex up to its own — because the native description's `[x km, y min]` is the cumulative distance at the manoeuvre's own node minus the previous node's, so the leg it describes ends where the instruction happens. (Owner finding, 2026-10-03: the highlight and the camera used the leg *after* the manoeuvre named in the card, so a correctly fitted view still showed the wrong segment.) The start line sits on the route's first vertex and therefore owns no leg. Selecting a step SHALL move the map camera **onto that leg** at a magnification that shows **it completely**: the leg's bounding box (with the marker margin) SHALL lie inside the map area the overlay leaves free, and the leg SHALL be centred on that free area. The magnification SHALL be the fit for the leg's bounding box, with a readable lower bound for short legs, and a leg too long for the current zoom SHALL be zoomed **out** until it fits — the earlier fixed focus level left parts of a long leg behind the card (owner requirement, 2026-10-03: "adjust the zoom level so that the bounding box of the current segment is really inside the visible part").

#### Scenario: Camera moves to the analysed manoeuvre

- **WHEN** the user taps a step whose manoeuvre lies outside the visible map area
- **THEN** the map SHALL be centered on that manoeuvre
- **AND** the magnification SHALL be close enough to see the junction the manoeuvre happens at

#### Scenario: The analysed segment is completely visible

- **WHEN** a step is analysed
- **THEN** its segment's bounding box SHALL lie inside the free map area above the overlay, with the marker margin
- **AND** the segment's midpoint SHALL sit on that free area's centre
- **AND** the magnification SHALL be the segment's fit, not a fixed level, so a longer segment is shown wider than a shorter one

#### Scenario: The analysed segment is published before the anchor

- **WHEN** a step is analysed
- **THEN** the step's polyline segment SHALL be published before the anchor that triggers a consumer
- **AND** a consumer reading the segment when the anchor arrives SHALL see that step's segment, never the previous step's

#### Scenario: Position agrees with the drawn route

- **WHEN** a step is analysed
- **THEN** the position shown for that step SHALL lie on the drawn route polyline
- **AND** the position SHALL lie between the positions of the previous and the following step

#### Scenario: Empty polyline degrades safely

- **WHEN** a step is analysed and the route polyline is empty
- **THEN** the map SHALL NOT fail to render
- **AND** no highlight segment SHALL be drawn

### Requirement: Analysed segment is highlighted on the map

The portion of the route polyline belonging to the analysed step SHALL be drawn as a highlighted segment over the route, distinguishable from the rest of the route in both the daylight and the dark presentation. The highlight SHALL be removed when no step is analysed.

#### Scenario: Segment highlighted

- **WHEN** the user analyses a step of a drawn route
- **THEN** the part of the polyline between that manoeuvre and the next manoeuvre SHALL be highlighted
- **AND** the rest of the route SHALL keep its normal appearance

#### Scenario: Highlight distinguishable in dark presentation

- **WHEN** the map renders the dark presentation
- **AND** a step is analysed
- **THEN** the highlighted segment SHALL be distinguishable from the route's normal colour and from the road classes beneath it

#### Scenario: Highlight removed with the selection

- **WHEN** a step's segment is highlighted
- **AND** the selection is cleared, the route is cleared, or the session ends
- **THEN** the highlighted segment SHALL no longer be drawn
- **AND** the plain route SHALL remain drawn while the route is still active
