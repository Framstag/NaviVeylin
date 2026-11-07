# Spec Delta: route-analysis

## MODIFIED Requirements

### Requirement: Rows are derived from the route's steps

The system SHALL derive route rows from the route's structured steps — never from description text — by one policy shared by the phone route list and the car route description. A row SHALL exist for the start, the arrival, every turn, roundabout, motorway and street-name manoeuvre; consecutive straight-on turns SHALL form one row. A step carrying only informational values SHALL NOT be a row. A row SHALL carry the legs of every step merged into it, so the rows cover the route once.

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

#### Scenario: Both surfaces list the same rows

- **WHEN** a route is listed by the phone route list and by the car route description
- **THEN** both lists SHALL contain the same rows in the same order with the same count
- **AND** neither surface SHALL derive a list the other one does not
- **Case**: `auto: RouteDescriptionScreenTest` "the car list is the same row set as the phone's"
