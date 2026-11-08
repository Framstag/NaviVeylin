# Spec Delta — auto/navigation-view

## ADDED Requirements

### Requirement: The car's navigation view offers a way to change the destination and to end the session

The navigation template SHALL carry an action that opens the car's search to pick a new destination and an action
that ends the session, both beside the zoom actions, because a driver whose route is wrong otherwise has to stop and
reach the phone.

#### Scenario: A live session can be re-planned and ended from the car

- **WHEN** a car session is navigating and the driver chooses *change destination* or *end navigation* from the
  navigation view's action strip
- **THEN** the action strip carries both actions in order after the two zoom actions, and a tap on each new action
  reaches the callback the screen binds (the end-to-end host effects — the search opening over the map, the host
  receiving the root screen again — are owned by the device line `tasks.md` 3.2)
- **Case**: `NavigationScreenActionsTest#navigationActionStripCarriesTheFourActionsAndTheirTapsReachTheirCallbacks`
