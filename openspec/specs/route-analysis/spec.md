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

Each row of a calculated route SHALL have a position on the map that agrees with the drawn polyline, so that
analysing a row can show where that manoeuvre is. A row SHALL own the polyline run between its own boundary and
the previous row's boundary — the vertices from where the previous row's manoeuvre happens up to where this
row's manoeuvre happens — because a row's distance and duration are the values of exactly that run (spec:
`osmscout-jni` — Per-step leg values on a calculated route), so the values a row shows and the segment the map
highlights describe the same piece of route. A row that represents a collapsed or merged run SHALL own the whole
run, from the first vertex of the first step it absorbed to its own manoeuvre, so the highlight has no gap
inside it. (Owner finding, 2026-10-03: the highlight and the camera used the leg *after* the manoeuvre named in
the card, so a correctly fitted view still showed the wrong segment.) The start line sits on the route's first
vertex and therefore owns no leg. Selecting a row SHALL move the map camera **onto that run** at a
magnification that shows **it completely**: the run's bounding box (with the marker margin) SHALL lie inside the
map area the overlay leaves free, and the run SHALL be centred on that free area. The magnification SHALL be
the fit for the run's bounding box, with a readable lower bound for short runs, and a run too long for the
current zoom SHALL be zoomed **out** until it fits — the earlier fixed focus level left parts of a long leg
behind the card (owner requirement, 2026-10-03: "adjust the zoom level so that the bounding box of the current
segment is really inside the visible part").

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

#### Scenario: A condensed row is highlighted without a gap

- **WHEN** the user analyses a row that represents several collapsed or merged steps
- **THEN** the highlight SHALL cover the polyline from the first absorbed step's vertex up to the row's own
  manoeuvre
- **AND** SHALL contain no break where a merged step's manoeuvre sits inside the run
- **Case**: `app: RouteStepSegmentsTest` "a condensed row's segment spans every absorbed step"

### Requirement: Analysed segment is highlighted on the map

The portion of the route polyline belonging to the analysed step SHALL be drawn as a highlighted segment over the route, distinguishable from the rest of the route in both the daylight and the dark presentation. The highlight SHALL be removed when no step is analysed. The highlight and the analysis SHALL live and end together: a session that ends by any of its exits ends both, and no session exit leaves one without the other. A route that navigation owns after the session ended SHALL stay drawn, without a highlighted segment, until a session opens and selects a step again.

#### Scenario: Segment highlighted

- **WHEN** the user analyses a step of a drawn route
- **THEN** the part of the polyline from the previous manoeuvre up to that step's manoeuvre SHALL be highlighted
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

#### Scenario: Start Navigation ends the highlight

- **WHEN** a route is calculated in an open session and a step of it is analysed
- **AND** the user taps Start Navigation
- **THEN** the session SHALL end and the route SHALL remain drawn, because navigation owns it
- **AND** the highlighted segment SHALL no longer be drawn
- **AND** the map SHALL show no highlighted segment for the rest of the drive unless a session opens and a step is analysed again

#### Scenario: Ending a review during navigation ends the highlight

- **WHEN** turn-by-turn navigation is active and a session open beside it has a step analysed
- **AND** the user ends that session
- **THEN** the highlighted segment SHALL no longer be drawn
- **AND** navigation SHALL continue and the route SHALL remain drawn

### Requirement: Step values describe the step's own leg

Every place the route analysis shows a row's distance and duration — the selectable step list, the step
navigator's analysed row, and the session card's statistic line for the route — SHALL show the run that belongs
to that row: its own step's leg plus the legs of every step merged into it, taken from the route's per-step
values (spec: `osmscout-jni` — Per-step leg values on a calculated route). The numbers of a row SHALL describe
the same run that selecting that row highlights, and the rows' distances SHALL add up to the distance the
route's per-step values carry and cover the route once (the two native total distances — the router's overall
distance and the description's cumulative node distances — are different sources and may disagree; measured
1.34× on a 70 km route, TODO.md §126). The values SHALL be formatted by the app with the shared locale-aware
formatters, not shown as they arrive from the native layer.

#### Scenario: Row values and highlighted leg are the same leg

- **WHEN** the user analyses a step of a calculated route
- **THEN** the distance and duration shown for that step SHALL be the values of the leg that is highlighted
- **AND** the leg SHALL begin at the previous step's manoeuvre and end at this step's manoeuvre

#### Scenario: The listed steps add up to the route's total

- **WHEN** the session card shows the route's step list for a 17.3 km route
- **THEN** the sum of the listed steps' distances SHALL be the route's length and of the same order as the shown total distance
- **AND** SHALL NOT be the length of a single geometry edge, which is what the rows carried before this change (a few hundred metres in total, measured 2026-10-04)

#### Scenario: The two native distance sources may differ

- **WHEN** the route's own total distance and the sum of its per-step distances disagree beyond half
- **THEN** the analysis SHALL still list every step with its own leg values
- **AND** SHALL record the divergence as a coordinate-free diagnostics entry, without treating the route as broken (the sources' relationship is a separate defect, TODO.md §126)

#### Scenario: Step values use the app's formatting

- **WHEN** a step's values are displayed while the device locale uses the decimal comma
- **THEN** the row SHALL show the distance with the app's unit label and the locale's decimal separator
- **AND** SHALL NOT show the native layer's own text for the value

#### Scenario: A route without per-step values still lists its steps

- **WHEN** a calculated route carries no structured steps at all
- **THEN** the step list SHALL show no rows
- **AND** the panel SHALL state that the route carries no steps instead of inventing rows or values
- **AND** a row SHALL omit the missing values instead of showing a wrong one

#### Scenario: Divergence from the route's total is reported

- **WHEN** the sum of the steps' distances diverges from the route's total distance beyond rounding
- **THEN** the app SHALL record a coordinate-free diagnostics entry naming the step count, the summed distance and the route's total distance
- **AND** the analysis SHALL still show the route and its steps

#### Scenario: A merged row carries the absorbed legs

- **WHEN** a row absorbed one or more steps that carry no row of their own
- **THEN** its distance and duration SHALL include those steps' legs
- **AND** the sum over all rows SHALL still equal the sum over all steps
- **Case**: `app: RouteRowDerivationTest` "merged legs are attributed to the surviving row"

### Requirement: A step's values are its own, never a neighbour's

The analysis SHALL decide whether a step carries values from the route's per-step values for that step, so a
step whose leg has no length — the start line above all — is not offered as a step with numbers and never
borrows another step's values.

#### Scenario: The current step is the first step with a leg

- **WHEN** a route is calculated and its start line owns no leg
- **THEN** the first step offered as the current step SHALL be the first step that carries a leg
- **AND** the start line SHALL NOT be offered as a step with numbers

#### Scenario: A leg-less step shows no numbers

- **WHEN** a step's leg has no length
- **THEN** its row SHALL show no distance and no duration
- **AND** SHALL NOT borrow the values of the step before or after it

### Requirement: An analysis is created only by the selector that owns it

A route reaching the map SHALL carry an analysed step only when a session's own selector asked for one — the step a fresh calculation or an adopted route selects while a session is open is that session's starting selection. A route that arrives while no session is open SHALL be shown with no step analysed, whichever surface acquired it, and its steps SHALL stay available to a session opened later.

#### Scenario: A calculation in an open session analyses its current step

- **WHEN** a route is calculated while its session is open
- **THEN** the session's current step SHALL be the analysed one
- **AND** that step's segment SHALL be highlighted

#### Scenario: An adopted route without a session carries no analysis

- **WHEN** the navigation engine acquires a route — a reroute, or a start on another surface — while no session is open
- **THEN** the map SHALL show that route's geometry and the route's steps SHALL be available to a session that opens later
- **AND** no step SHALL be analysed
- **AND** no segment SHALL be highlighted, at the acquisition or at any later point of the drive

#### Scenario: An adopted route during a review starts at the new route's current step

- **WHEN** a session is open over a route and the navigation engine acquires a different one
- **THEN** the analysed step SHALL be the new route's current step
- **AND** a selection made on the previous route SHALL NOT survive the swap

#### Scenario: A selection made during navigation is unaffected

- **WHEN** turn-by-turn navigation is active and a session is open
- **AND** the user analyses a step
- **THEN** that step SHALL be the analysed one and its segment SHALL be highlighted
- **AND** the step navigation follows SHALL NOT change

### Requirement: Rows are derived from the route's steps

The route analysis SHALL derive its rows from the route's structured steps, never from description text. A row
SHALL exist for the start, the arrival, every turn, roundabout, motorway and street-name manoeuvre;
consecutive straight-on turns SHALL form one row. A step carrying only informational values SHALL NOT be a row.
A row SHALL carry the legs of every step merged into it, so the rows still cover the route once.

#### Scenario: Roundabout and motorway manoeuvres are rows

- **WHEN** a calculated route leaves a roundabout at its third exit and later enters a motorway
- **THEN** the step list SHALL contain a row for the roundabout exit carrying exit ordinal 3
- **AND** a row for the motorway entry naming the motorway
- **Case**: `app: RouteRowDerivationTest` "roundabout exit and motorway entry become rows"

#### Scenario: Consecutive straight-on turns collapse into one row

- **WHEN** three consecutive steps are turns whose move is straight on, with no street-name change between them
- **THEN** one row SHALL represent the three
- **AND** its distance and duration SHALL be the sum of the three legs
- **Case**: `app: RouteRowDerivationTest` "consecutive straight-on turns collapse into one row"

#### Scenario: A street-name change earns its own row

- **WHEN** the street name changes and the next row's manoeuvre is further away than the close-manoeuvre window
  or does not name the new street
- **THEN** a row SHALL exist naming the new street
- **Case**: `app: RouteRowDerivationTest` "a street-name change earns its own row"

#### Scenario: A street-name change restated by the next manoeuvre is merged

- **WHEN** the street name changes to the street the next manoeuvre turns into, and that manoeuvre is closer
  than the close-manoeuvre window
- **THEN** no separate row SHALL exist for the change
- **AND** its leg SHALL be added to that manoeuvre's row
- **Case**: `app: RouteRowDerivationTest` "a street-name change restated by the next manoeuvre is merged into it"

#### Scenario: A step without a readable name change is merged

- **WHEN** a step's new name is empty after trimming, or equal to the previous name after folding case and
  spacing
- **THEN** it SHALL NOT produce a row
- **AND** its leg SHALL be added to the nearest row
- **Case**: `app: RouteRowDerivationTest` "an empty or repeated name change is merged"

#### Scenario: An informative step is data, never a row

- **WHEN** a step carries only a maximum speed, lanes, a way type, crossing ways, a junction or a destination
- **THEN** the step list SHALL contain no row for it
- **AND** its leg SHALL be added to the previous row's values
- **Case**: `app: RouteRowDerivationTest` "an informative step is not a row and its leg moves to the row above"

#### Scenario: Rows tile the route

- **WHEN** two consecutive rows of a calculated route are inspected
- **THEN** the second row's segment SHALL begin where the first row's segment ends
- **AND** the first row's segment SHALL begin at the route's first vertex
- **AND** the last row's segment SHALL end at the route's last vertex
- **Case**: `app: RouteRowDerivationTest` "rows tile the polyline without a gap or an overlap"

#### Scenario: The rows add up to the route's length

- **WHEN** the distances of a calculated route's rows are summed
- **THEN** the sum SHALL equal the route's length, whichever rows the filter kept or merged
- **Case**: `app: RouteRowDerivationTest` "row distances sum to the route length after merging"

#### Scenario: Filtering is a view, not a data loss

- **WHEN** the rows are derived for display
- **THEN** the route's complete step list SHALL remain available in the navigation state
- **AND** deriving rows SHALL NOT mutate or shorten it
- **Case**: `app: RouteRowDerivationTest` "row derivation leaves the step list intact"

### Requirement: The route's start anchor is the published start position

The first step of a calculated route (the start line) SHALL be anchored at the position the route's
geometry is published from, so that analysing the start line shows the place the route begins at and
the start line owns no leg. When the route begins with an approach from a requested start to the
network, that position SHALL be the requested start; the invariant is that the start anchor and the
polyline's first vertex are the same position, not that the start anchor is a network node.

#### Scenario: Start anchor equals the polyline's first vertex

- **WHEN** a route with a snapped start is analysed
- **THEN** the start line's position SHALL be the polyline's first vertex
- **AND** the start line SHALL own no leg
  (case: `RouteStepSegmentsTest.aPrependedStartAnchorsStepZeroAndOwnsNoLeg`)

#### Scenario: The first turn's leg starts at the published start

- **WHEN** a route with a snapped start is analysed and the first turn is selected
- **THEN** the first turn's highlighted segment SHALL begin at the polyline's first vertex
- **AND** the segment's last vertex SHALL be within the polyline-match tolerance of that step's own
  manoeuvre
  (case: `RouteStepSegmentsTest.aPrependedStartAnchorsStepZeroAndOwnsNoLeg`)

#### Scenario: The start anchor is not shifted when the start is on the network

- **WHEN** a route whose start already lies on the routable network is analysed
- **THEN** the start line's position and the highlighted first leg SHALL be the ones the routed geometry
  produces
  (case: `RouteStepSegmentsTest.straight route maps each step to the leg leading to its manoeuvre`)
