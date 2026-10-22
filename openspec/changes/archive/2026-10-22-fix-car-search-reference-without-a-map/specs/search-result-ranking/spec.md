# Delta — search-result-ranking

## MODIFIED Requirements

### Requirement: Distance reference for ordering and display

Distances used for ordering and shown in result rows SHALL be computed from the last known GPS fix. When no fix has ever been received, the fallback reference SHALL be the phone map center on the phone surfaces and the current car map viewport center on the car surface. On the car surface the current car map viewport center SHALL be the center of the last frame a car map screen drew, and it SHALL be available to a car search screen regardless of which car screen opened that search: a car map screen SHALL publish the center of its last drawn frame as it stops drawing, a car search screen SHALL use its own map's center when it has one, and otherwise SHALL use the published center. When neither a fix nor a fallback center exists, results SHALL be ordered by tier and match quality only, and no distance SHALL be displayed.

#### Scenario: Fix available while the map is panned elsewhere

- **WHEN** a GPS fix is available
- **AND** the user has panned the map away from their position
- **THEN** both the displayed distances and the ordering SHALL be computed from the fix, not from the panned map center
- **Case**: `auto: SearchScreenTest.typedSearchPassesTheGpsFixAsTheDistanceReference`

#### Scenario: No fix uses the fallback center

- **WHEN** no GPS fix has been received
- **AND** a fallback center is available
- **THEN** ordering and displayed distances SHALL be computed from the fallback center
- **Case**: `auto: SearchScreenTest.withoutAFixTheCarViewportCenterIsTheReference`

#### Scenario: Car search opened from a surface that shows no map

- **WHEN** no GPS fix has been received
- **AND** a car map screen has drawn a frame and has stopped drawing it
- **AND** the search is opened from a car screen that shows no map (the root list or the search-history list)
- **THEN** ordering and displayed distances SHALL be computed from the center of that last drawn frame
- **Case**: `auto: SearchScreenTest.aSearchWithoutAMapUsesTheLastDrawnCarViewportCenter`

#### Scenario: A live map's own center outranks a published one

- **WHEN** no GPS fix has been received
- **AND** a car map screen has published the center of its last drawn frame
- **AND** the screen that opens the search owns a live map of its own
- **THEN** the ordering and the displayed distances SHALL be computed from that screen's own map center
- **Case**: `auto: SearchScreenTest.theMapScreensOwnCenterWinsOverThePublishedOne`

#### Scenario: No reference available

- **WHEN** neither a GPS fix nor a fallback center is available
- **AND** no car map screen has drawn a frame
- **THEN** the results SHALL still be ordered by tier and match quality
- **AND** no distance SHALL be displayed
- **Case**: `auto: SearchScreenTest.noFixAndNoDrawnCarMapMeansNoDistance`
