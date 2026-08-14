## Purpose

Shows an animated border on the navigation status card during route recalculation, giving users immediate visual confirmation that rerouting is in progress.

## ADDED Requirements

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

### Requirement: Animated border on status card during rerouting
The navigation status card (`NavigationStateOverlay`) SHALL display an animated border when `isRerouting` is `true`. The animation SHALL be a subtle pulsing/scanning effect using the Material 3 primary or secondary color.

#### Scenario: Border appears on reroute
- **WHEN** `isRerouting` transitions to `true`
- **THEN** the status card SHALL show an animated border

#### Scenario: Border disappears on reroute complete
- **WHEN** `isRerouting` transitions to `false`
- **THEN** the animated border SHALL disappear

#### Scenario: Border is non-distracting
- **WHEN** the animated border is shown
- **THEN** the animation SHALL be subtle (gentle pulse, not rapid flashing) and SHALL NOT interfere with touch interaction on the status card
