## Purpose

Defines the phone behavior when a reroute calculation is started or fails during navigation: the previously drawn route stays on the map until the new route is ready, failures are surfaced to the user, and the shared navigation state carries the route geometry on both phone and car.

## ADDED Requirements

### Requirement: Route stays drawn during reroute calculation
When a reroute is confirmed, the system SHALL keep the currently drawn route on the map until the reroute calculation produces a new route; the system SHALL NOT clear the drawn route as a side effect of updating the start/destination for the reroute.

#### Scenario: Failed reroute keeps the last route visible
- **WHEN** a reroute is confirmed, the route calculation fails, and navigation is still active
- **THEN** the map SHALL continue to display the previously drawn route and SHALL NOT display an empty map

#### Scenario: Successful reroute replaces the route
- **WHEN** a reroute is confirmed and the reroute calculation succeeds
- **THEN** the map SHALL display the newly calculated route in place of the previously drawn route

### Requirement: Failed reroute calculation is surfaced during navigation
When a reroute calculation fails while navigation is active on the phone, the system SHALL make the failure visible: a log entry SHALL be written via the platform log, and the user SHALL see a snackbar with the failure message.

#### Scenario: Calc failure shows snackbar while navigating
- **WHEN** navigation is active on the phone and a reroute calculation fails
- **THEN** the phone SHALL show a snackbar with the error message and SHALL write a `Log.e` entry

#### Scenario: No snackbar spam for repeated failures
- **WHEN** multiple route-calc failures occur in quick succession
- **THEN** the snackbar SHALL NOT queue unlimited messages (existing snackbar channel coalescing applies)

### Requirement: Phone and Android Auto parity for route geometry state
The shared `NavigationState` SHALL carry `routeLats`/`routeLons` when navigation starts on the phone, identical to the Android Auto / Android Automotive OS controller.

#### Scenario: Phone startNavigation populates route geometry
- **WHEN** the phone starts navigation on a calculated route
- **THEN** `NavigationState.routeLats` and `NavigationState.routeLons` SHALL be set to the route polyline, and the values SHALL be cleared when navigation stops

### Requirement: Panel-driven route clearing unchanged
Changing the start or destination through the route panel UI SHALL keep clearing the drawn route as today; the no-clear behavior SHALL apply only to programmatic reroute location updates.

#### Scenario: Panel edit still clears route
- **WHEN** the user edits the start or destination in the route panel
- **THEN** the previously drawn route SHALL be cleared as before this change
