# off-route-indicator Specification

## Purpose

Shows a soft reddish background tint on the navigation status card when the vehicle deviates from the planned route, giving the driver an early visual cue before reroute calculation completes.

## Requirements

### Requirement: Off-route red tint on status card
The navigation status card (`NavigationStateOverlay`) SHALL display a soft reddish overlay when `isOffRoute` is `true`. The overlay SHALL cover the entire card including text and icons, giving all content a uniform reddish tint. The tint SHALL be subtle (alpha ≤ 0.16) and SHALL NOT prevent reading text or recognizing icons on the card.

#### Scenario: Red tint appears on route deviation
- **WHEN** `isOffRoute` transitions to `true`
- **THEN** the status card background SHALL show a soft reddish tint

#### Scenario: Red tint disappears on new route
- **WHEN** `isOffRoute` transitions to `false`
- **THEN** the reddish tint SHALL be removed

#### Scenario: Red tint covers all card content
- **WHEN** `isOffRoute` is `true`
- **THEN** the reddish tint SHALL cover the entire card area including text labels, icons, and background
