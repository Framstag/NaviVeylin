# Spec Delta

## ADDED Requirements

### Requirement: Step values describe the step's own leg

Every place the route analysis shows a step's distance and duration — the selectable step list, the step
navigator's analysed step, and the session card's statistic line for the route — SHALL show the leg that
belongs to that step, taken from the route's per-step values (spec: `osmscout-jni` — Per-step leg values on a
calculated route). The numbers of a row SHALL describe the same leg that selecting that row highlights, and
the rows' distances SHALL add up to the distance the route's per-step values carry and cover the route once
(the two native total distances — the router's overall distance and the description's cumulative node distances —
are different sources and may disagree; measured 1.34× on a 70 km route, TODO.md §126).
The values SHALL be formatted by the app with the shared locale-aware formatters, not shown as they arrive
from the native layer.

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

- **WHEN** a calculated route carries no per-step distance or duration
- **THEN** the step list SHALL still show every step's instruction text and turn type
- **AND** a row SHALL omit the missing values instead of showing a wrong one

#### Scenario: Divergence from the route's total is reported

- **WHEN** the sum of the steps' distances diverges from the route's total distance beyond rounding
- **THEN** the app SHALL record a coordinate-free diagnostics entry naming the step count, the summed distance and the route's total distance
- **AND** the analysis SHALL still show the route and its steps

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

## MODIFIED Requirements

### Requirement: Analysed step is locatable on the map

Each step of a calculated route SHALL have a position on the map that agrees with the drawn polyline, so that analysing a step can show where that manoeuvre is. A step SHALL own the polyline leg that **leads to** its manoeuvre — from the previous manoeuvre's vertex up to its own — because a step's distance and duration are the values of exactly that leg (spec: `osmscout-jni` — Per-step leg values on a calculated route), so the values a row shows and the segment the map highlights describe the same piece of route. (Owner finding, 2026-10-03: the highlight and the camera used the leg *after* the manoeuvre named in the card, so a correctly fitted view still showed the wrong segment.) The start line sits on the route's first vertex and therefore owns no leg. Selecting a step SHALL move the map camera **onto that leg** at a magnification that shows **it completely**: the leg's bounding box (with the marker margin) SHALL lie inside the map area the overlay leaves free, and the leg SHALL be centred on that free area. The magnification SHALL be the fit for the leg's bounding box, with a readable lower bound for short legs, and a leg too long for the current zoom SHALL be zoomed **out** until it fits — the earlier fixed focus level left parts of a long leg behind the card (owner requirement, 2026-10-03: "adjust the zoom level so that the bounding box of the current segment is really inside the visible part").

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
