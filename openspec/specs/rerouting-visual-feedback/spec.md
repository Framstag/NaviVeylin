# rerouting-visual-feedback Specification

## Purpose

Shows visual feedback on the navigation status card when the vehicle leaves the planned route and while a reroute is being calculated, giving the driver clear, non-distracting status cues.

## Requirements

### Requirement: Rerouting state is exposed in navigation state
The system SHALL expose an `isRerouting` boolean in the navigation state that is `true` while a reroute is being calculated and `false` otherwise. Every terminal outcome of a reroute attempt — new instructions, failure, cancellation — SHALL leave `isRerouting` `false`.

#### Scenario: Rerouting begins
- **WHEN** the navigation engine fires `onRerouteRequest`
- **THEN** `isRerouting` SHALL be set to `true`

#### Scenario: Rerouting completes
- **WHEN** new route instructions arrive via `onRouteInstructions`
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

### Requirement: Off-route state is exposed in navigation state
The system SHALL expose an `isOffRoute` boolean in the navigation state that is `true` when the vehicle has left the planned route and `false` otherwise. This is distinct from `isRerouting` — off-route state covers the entire period from route deviation through reroute completion. A reroute attempt that fails or is cancelled SHALL NOT clear `isOffRoute`.

#### Scenario: Off-route begins on reroute request
- **WHEN** the navigation engine fires `onRerouteRequest`
- **THEN** `isOffRoute` SHALL be set to `true`

#### Scenario: Off-route clears on new route
- **WHEN** new route calculation completes via `RoutePanelViewModel`
- **THEN** `isOffRoute` SHALL be set to `false`

#### Scenario: Off-route clears on navigation stop
- **WHEN** the user stops navigation while `isOffRoute` is `true`
- **THEN** `isOffRoute` SHALL be set to `false`

#### Scenario: Off-route survives a failed reroute
- **WHEN** a reroute attempt fails while `isOffRoute` is `true`
- **THEN** `isOffRoute` SHALL stay `true` until the next instruction list arrives or navigation stops

### Requirement: Solid red tint on status card during rerouting
When `isOffRoute` is `true`, the navigation status card (`NavigationStateOverlay`) SHALL display a solid red tint overlay. The tint SHALL use `MaterialTheme.colorScheme.error` at low alpha (0.16). The tint SHALL cover the entire card including text and icons. No animated border SHALL be used.

#### Scenario: Red tint appears on reroute
- **WHEN** `isOffRoute` transitions to `true`
- **THEN** the status card SHALL show a solid red tint overlay

#### Scenario: Red tint disappears on reroute complete
- **WHEN** `isOffRoute` transitions to `false`
- **THEN** the red tint SHALL be removed

#### Scenario: Tint is non-distracting
- **WHEN** the red tint is shown
- **THEN** the tint SHALL be subtle (alpha ≤ 0.16) and SHALL NOT prevent reading text or recognizing icons on the card

### Requirement: Reroute calculation progress is exposed
While a reroute is in progress the system SHALL expose the reroute calculation's progress as the same percentage an initial acquisition exposes, and SHALL present the reroute as a progress notice rather than silence.

#### Scenario: Progress during a reroute
- **WHEN** the routing engine reports progress while a reroute is being calculated
- **THEN** the navigation state SHALL carry that percentage
- **AND** the reroute SHALL still be reported as in progress

#### Scenario: Reroute progress notice
- **WHEN** a reroute is still running after the car notice delay has elapsed
- **THEN** the car session SHALL show the calculation notice (see `route-calculation-feedback`)

#### Scenario: Reroute state clears the progress
- **WHEN** the reroute completes or navigation is stopped while it runs
- **THEN** `isRerouting` SHALL be `false`
- **AND** the state SHALL report no calculation in progress
