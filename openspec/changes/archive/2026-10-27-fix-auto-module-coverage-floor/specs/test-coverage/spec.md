## ADDED Requirements

### Requirement: A module's suite reaches the coverage row's floor

A module's unit suite SHALL cover the classes a host test can reach, so the instruction coverage that the
sensor registry's coverage row reads from that module's own report does not fall below that row's floor. A case
added for that coverage SHALL assert a behaviour the capability owns, never that a line ran; a class only the
native side can reach SHALL NOT be covered from a host test; and a case that builds a component with long-lived
background work SHALL release it before it returns.

#### Scenario: The coverage probe passes on the change

- **WHEN** the coverage row's arithmetic — the last instruction counter of the module's Kover report against the
  row's 70 % floor — is applied to the `auto` module's report
- **THEN** the module SHALL be at or above the floor
- **Case**: `coverage-floor-probe#theAutoModulesInstructionCoverageReachesTheFloor`

#### Scenario: The free-driving screen's template is the map-only template

- **WHEN** the free-driving screen is asked for its template
- **THEN** the template SHALL be a `NavigationTemplate`, the one template with no content slot; its map
  action strip SHALL carry two actions, and its action strip the zoom-in and zoom-out actions in that order
- **Case**: `FreeDrivingScreenHostTest#theTemplateIsTheBareNavigationTemplateCarryingItsActions`

#### Scenario: A started screen takes the session surface and a stopped one releases it

- **WHEN** the free-driving screen reaches the started state and later the stopped state
- **THEN** it SHALL call the session surface's attach exactly once on start and its detach exactly once on
  stop, so no screen holds the surface while the session draws through another
- **Case**: `FreeDrivingScreenHostTest#aStartedScreenTakesTheSessionSurfaceAndStopsReleasing`

#### Scenario: A started screen observes fixes and map-data revisions

- **WHEN** the shared location source publishes a fix and the basemap notifier publishes a revision while the
  free-driving screen is started
- **THEN** the screen SHALL process both without faulting and still answer a `NavigationTemplate`
- **Case**: `FreeDrivingScreenHostTest#aStartedScreenObservesFixesAndBasemapRevisions`

#### Scenario: The screen votes for free driving on entry

- **WHEN** the free-driving screen is constructed
- **THEN** it SHALL publish the car's free-driving flag, so the shared indicator and the ongoing notification
  follow the surface that is driving
- **Case**: `FreeDrivingScreenHostTest#anEnteredScreenVotesTheFreeDrivingFlagOn`

#### Scenario: A destroyed screen drops the road status it published

- **WHEN** the shared status provider carries the car's road/speed status while a free-driving screen is
  started, and the screen is destroyed
- **THEN** it SHALL clear that status, so no road from an ended session survives into the notification
- **Case**: `FreeDrivingScreenHostTest#aDestroyedScreenDropsTheRoadStatusItPublishedAndItsSurface`
