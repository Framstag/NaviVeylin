# Spec Delta — route-analysis

## MODIFIED Requirements

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

## ADDED Requirements

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
