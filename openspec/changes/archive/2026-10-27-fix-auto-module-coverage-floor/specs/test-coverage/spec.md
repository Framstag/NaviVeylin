## ADDED Requirements

### Requirement: A module's suite reaches the coverage row's floor

Each module's unit suite SHALL cover the classes a host test can reach, so the instruction coverage that the
sensor registry's coverage row reads from the module's own report does not fall below that row's floor. A case
added for that coverage SHALL assert a behaviour the capability owns, never that a line ran; a class only the
native side can reach SHALL NOT be covered from a host test; and a case that builds a component with long-lived
background work SHALL release it before it returns.

#### Scenario: The coverage probe passes on the change

- **WHEN** the coverage row's arithmetic — the last instruction counter of the module's Kover report against the
  row's 70 % floor — is applied to the `auto` module's report
- **THEN** the module SHALL be at or above the floor, having been below it on the base tree with the same
  arithmetic
- **Case**: `coverage-floor-probe#theAutoModulesInstructionCoverageReachesTheFloor`

#### Scenario: The free-driving screen's template is the map-only template

- **WHEN** the free-driving screen is asked for its template
- **THEN** the template SHALL be a `NavigationTemplate`, the one template with no content slot, and its action
  strips SHALL carry the pan/exit and the zoom-in/zoom-out actions
- **Case**: `FreeDrivingScreenHostTest#theTemplateIsTheBareNavigationTemplateCarryingItsActions`

#### Scenario: A started screen takes the session surface and a stopped one releases it

- **WHEN** the free-driving screen reaches the started state and later the stopped state
- **THEN** it SHALL attach itself as the session surface's owner on start and detach on stop, so no screen holds
  the surface while the session draws through another
- **Case**: `FreeDrivingScreenHostTest#aStartedScreenTakesTheSessionSurfaceAndStopsReleasing`

#### Scenario: A started screen observes fixes and map-data revisions

- **WHEN** the shared location source publishes a fix and the basemap notifier publishes a revision while the
  free-driving screen is started
- **THEN** the screen SHALL consume both without faulting and still answer a `NavigationTemplate`
- **Case**: `FreeDrivingScreenHostTest#aStartedScreenObservesFixesAndBasemapRevisions`

#### Scenario: The screen votes for free driving on entry

- **WHEN** the free-driving screen is constructed
- **THEN** it SHALL publish the car's free-driving flag, so the shared indicator and the ongoing notification
  follow the surface that is driving
- **Case**: `FreeDrivingScreenHostTest#anEnteredScreenVotesTheFreeDrivingFlagOn`

#### Scenario: A destroyed screen drops the road status it published

- **WHEN** a free-driving screen that published the car's road/speed status is destroyed
- **THEN** it SHALL clear that status, so no road from an ended session survives into the notification
- **Case**: `FreeDrivingScreenHostTest#aDestroyedScreenDropsTheRoadStatusItPublishedAndItsSurface`
