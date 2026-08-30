# nav-hints-layout Specification

## Purpose

Defines layout constraints for the navigation hints overlay so it does not overlap on-map controls and is positioned flush to the left display edge.

## Requirements

### Requirement: Navigation hints full width
The NextTurnOverlay SHALL span the full display width without horizontal padding. During navigation the top area is free — the action button columns are hidden and the compass/speed cluster is bottom-anchored above the routing status — so the hint card can extend edge to edge.

#### Scenario: NextTurnOverlay edge-to-edge
- **WHEN** navigation is active and NextTurnOverlay is displayed
- **THEN** the overlay SHALL extend from the left to the right display edge with no horizontal padding

#### Scenario: No right gap
- **WHEN** navigation is active and NextTurnOverlay is displayed
- **THEN** there SHALL be no gap between the right edge of the overlay and the right display edge

### Requirement: Routing status panel full width
The NavigationStateOverlay SHALL span the full screen width without horizontal padding.

#### Scenario: NavigationStateOverlay edge-to-edge
- **WHEN** navigation is active and NavigationStateOverlay is displayed
- **THEN** the panel SHALL extend from the left to the right display edge with no horizontal padding

### Requirement: Routing status covers the bottom of the window
The NavigationStateOverlay SHALL extend to the bottom edge of the window, with square bottom corners, so no gap remains between the card and the display bottom. Card content SHALL be padded above the system navigation bar.

#### Scenario: Card reaches the display bottom
- **WHEN** navigation is active and NavigationStateOverlay is displayed
- **THEN** the card SHALL reach the bottom edge of the window with square bottom corners

#### Scenario: Content clear of the navigation bar
- **WHEN** navigation is active and NavigationStateOverlay is displayed
- **THEN** the card content SHALL be padded above the system navigation bar inset
