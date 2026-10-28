# rerouting-visual-feedback

## MODIFIED Requirements

### Requirement: Rerouting state is exposed in navigation state

The system SHALL expose an `isRerouting` boolean in the navigation state that is `true` while a reroute is being
calculated and `false` otherwise. Every terminal outcome of a reroute attempt — the new route's steps arriving,
failure, cancellation — SHALL leave `isRerouting` `false`.

#### Scenario: Rerouting begins
- **WHEN** the navigation engine fires `onRerouteRequest`
- **THEN** `isRerouting` SHALL be set to `true`

#### Scenario: Rerouting completes
- **WHEN** the new route's steps arrive via `onRouteSteps`
- **THEN** `isRerouting` SHALL be set to `false`

#### Scenario: Navigation stops during rerouting
- **WHEN** the user stops navigation while `isRerouting` is `true`
- **THEN** `isRerouting` SHALL be set to `false`

#### Scenario: Failed reroute ends rerouting state
- **WHEN** the reroute's route calculation reports an error, or the calculation call itself throws
- **THEN** `isRerouting` SHALL be set to `false`
- **AND** the error SHALL still be published in the same state update that clears it
- **AND** the running navigation SHALL keep its step list, its route geometry and its position updates

#### Scenario: Cancelled reroute ends rerouting state
- **WHEN** a reroute's in-flight calculation is cancelled through the engine's cancel entry point while navigation is running
- **THEN** `isRerouting` SHALL be set to `false`

#### Scenario: Car guidance survives a failed reroute
- **WHEN** a reroute attempt fails while navigation is running and the car navigation template is built from the shared state
- **THEN** the reported trip SHALL carry the running route's next turn and its travel estimate, not a loading trip

#### Scenario: No surface is told a reroute is running after it ended
- **WHEN** any terminal outcome clears `isRerouting`
- **THEN** a subsequent state emission SHALL NOT report `isRerouting` as `true` until a new `onRerouteRequest` fires
