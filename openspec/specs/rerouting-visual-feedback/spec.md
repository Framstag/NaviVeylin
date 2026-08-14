# rerouting-visual-feedback Specification

## Purpose

Shows visual feedback on the navigation status card when the vehicle leaves the planned route and while a reroute is being calculated, giving the driver clear, non-distracting status cues.

## Requirements

### Requirement: Rerouting state is exposed in navigation state
The system SHALL expose an `isRerouting` boolean in the navigation state that is `true` while a reroute is being calculated and `false` otherwise.

#### Scenario: Rerouting begins
- **WHEN** the navigation engine fires `onRerouteRequest`
- **THEN** `isRerouting` SHALL be set to `true`

#### Scenario: Rerouting completes
- **WHEN** new route instructions arrive via `onRouteInstructions`
- **THEN** `isRerouting` SHALL be set to `false`

#### Scenario: Navigation stops during rerouting
- **WHEN** the user stops navigation while `isRerouting` is `true`
- **THEN** `isRerouting` SHALL be set to `false`

### Requirement: Off-route state is exposed in navigation state
The system SHALL expose an `isOffRoute` boolean in the navigation state that is `true` when the vehicle has left the planned route and `false` otherwise. This is distinct from `isRerouting` — off-route state covers the entire period from route deviation through reroute completion.

#### Scenario: Off-route begins on reroute request
- **WHEN** the navigation engine fires `onRerouteRequest`
- **THEN** `isOffRoute` SHALL be set to `true`

#### Scenario: Off-route clears on new route
- **WHEN** new route calculation completes via `RoutePanelViewModel`
- **THEN** `isOffRoute` SHALL be set to `false`

#### Scenario: Off-route clears on navigation stop
- **WHEN** the user stops navigation while `isOffRoute` is `true`
- **THEN** `isOffRoute` SHALL be set to `false`

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
