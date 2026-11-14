## ADDED Requirements

### Requirement: A car POI result list can be narrowed by name

The car POI results screen SHALL offer a filter row above its results that opens a search-template screen prefilled with the current term. Submitting SHALL apply the term to that same results screen and return to it, so the back stack does not grow per refinement. The header SHALL carry the active term. A POI walk under a filter SHALL be asked for the candidate cap, which SHALL be at least twice the row capacity the host reports (spec: `car-list-row-limit`).

#### Scenario: The filter row sits above the results

- **WHEN** the car POI results screen shows results
- **THEN** a "Filter by name" row SHALL be its first row
- **AND** that row SHALL carry the active term as its text when one is set
- **Case**: `auto: PoiResultsScreenTest.theFilterRowSitsAboveTheResults`

#### Scenario: The filter row is shown without a fix

- **WHEN** the car POI results screen has no GPS fix
- **THEN** the "Filter by name" row SHALL still be shown
- **AND** the screen SHALL state that a GPS signal is required instead of running the search
- **Case**: `auto: PoiResultsScreenSearchLifecycleTest.withoutAFixTheScreenExplainsInsteadOfSearching`

#### Scenario: The filter screen opens with the current term

- **WHEN** the driver taps the filter row while a term is active
- **THEN** the search-template screen SHALL open with that term prefilled
- **Case**: `auto: PoiFilterScreenTest.theScreenOpensWithTheCurrentTerm`

#### Scenario: Submitting applies the term and pops

- **WHEN** the driver submits a term
- **THEN** the results screen SHALL re-search that category with the term and show the narrowed list
- **AND** the filter screen SHALL be popped, so the results screen is the one on top
- **Case**: `auto: PoiFilterScreenTest.submittingAppliesTheTermAndPops`

#### Scenario: An empty submit clears the filter

- **WHEN** the driver submits an empty field
- **THEN** the results screen SHALL show the unfiltered list of that category
- **Case**: `auto: PoiFilterScreenTest.anEmptySubmitClearsTheFilter`

#### Scenario: The header carries the active term

- **WHEN** a term is active on the results screen
- **THEN** its header title SHALL name the category and the term
- **Case**: `auto: PoiResultsScreenTest.theHeaderCarriesTheActiveTerm`

#### Scenario: A filtered walk asks for the candidate cap

- **WHEN** the results screen searches a category under a non-empty term
- **THEN** the POI walk SHALL be asked for the candidate cap rather than for twice the row capacity
- **Case**: `auto: PoiResultsScreenTest.aFilteredWalkAsksForTheCandidateCap`

#### Scenario: The pop goes through the guarded host call

- **WHEN** the filter screen pops itself after a submit
- **THEN** the pop SHALL be issued through the guarded host call, so a refusing host cannot fault the car path
- **Case**: `auto: PoiFilterScreenTest.thePopGoesThroughTheGuardedHostCall`

#### Scenario: A screen left without submitting changes nothing

- **WHEN** the driver leaves the filter screen without submitting
- **THEN** the term on the results screen SHALL stay as it was
- **Case**: `auto: PoiFilterScreenTest.leavingWithoutSubmittingChangesNothing`

### Requirement: A car POI row names its operator or brand

A car POI result row SHALL compose its title with the phone's POI row rule — the name with the brand (preferred) or operator in parentheses, the brand or operator alone when there is no name — through one composer shared by both variants, so a row that survived a name filter shows the attribute that matched. The row's single text line SHALL keep the object type and the distance.

#### Scenario: A row title carries the operator

- **WHEN** a car POI row's entry is named `Supermarkt` with operator `REWE`
- **THEN** the row title SHALL read `Supermarkt (REWE)`
- **Case**: `auto: PoiResultsScreenTest.aRowTitleCarriesTheOperator`

#### Scenario: An unnamed row shows the operator alone

- **WHEN** a car POI row's entry has no name and carries an operator
- **THEN** the row title SHALL be that operator
- **Case**: `auto: PoiResultsScreenTest.anUnnamedRowShowsTheOperatorAlone`

#### Scenario: Both variants compose the same title

- **WHEN** the same entry is rendered by the phone's POI row and the car's
- **THEN** both titles SHALL be the shared composer's answer
- **Case**: `core: PoiRowTextTest.bothVariantsComposeTheSameTitle`

#### Scenario: The text line keeps type and distance

- **WHEN** a car POI row is rendered
- **THEN** its text line SHALL carry the object type and the distance
- **Case**: `auto: PoiResultsScreenTest.theTextLineKeepsTypeAndDistance`
