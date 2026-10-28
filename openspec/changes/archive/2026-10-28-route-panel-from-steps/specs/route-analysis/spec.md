# route-analysis

## ADDED Requirements

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

## MODIFIED Requirements

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
