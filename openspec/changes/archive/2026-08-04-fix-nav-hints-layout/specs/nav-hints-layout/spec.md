## Purpose

Defines layout constraints for the navigation hints overlay so it does not overlap on-map controls and is positioned flush to the left display edge.

## ADDED Requirements

### Requirement: Navigation hints avoid on-map buttons
The NextTurnOverlay SHALL have its width constrained so it does not extend under the top-right button column (menu, compass, search, favorites, location options, zoom controls).

#### Scenario: NextTurnOverlay width respects button column
- **WHEN** navigation is active and NextTurnOverlay is displayed
- **THEN** the overlay width SHALL be limited to leave the top-right button column fully visible and tappable

### Requirement: Navigation hints left-aligned without margin
The NextTurnOverlay SHALL be positioned flush to the left display edge with no left-side gap.

#### Scenario: NextTurnOverlay starts at left edge
- **WHEN** navigation is active and NextTurnOverlay is displayed
- **THEN** the overlay SHALL have zero left padding/margin from the display edge

### Requirement: Routing status panel full width
The NavigationStateOverlay SHALL span the full screen width without horizontal padding.

#### Scenario: NavigationStateOverlay edge-to-edge
- **WHEN** navigation is active and NavigationStateOverlay is displayed
- **THEN** the panel SHALL extend from the left to the right display edge with no horizontal padding
