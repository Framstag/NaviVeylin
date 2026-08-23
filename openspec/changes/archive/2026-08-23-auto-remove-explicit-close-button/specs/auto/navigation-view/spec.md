# Android Auto Navigation View — Delta (auto/navigation-view)

## MODIFIED Requirements

### Requirement: Leave navigation at any time

The system SHALL let the user stop active navigation from the car display at any time via a visible stop action (an "x" button) in the navigation map action strip or via system back, and SHALL NOT show an explicit back button in the map action strip.

#### Scenario: Stop navigation from car

- **WHEN** the user presses system back during navigation
- **THEN** navigation stops on the car display and the screen returns to the root menu

#### Scenario: Stop action shown on navigation map

- **WHEN** the user is navigating on the car display
- **THEN** the navigation map action strip shows a stop action (an "x" button) beside the route-description action, and no back button

#### Scenario: Stop action stops navigation

- **WHEN** the user activates the stop action on the navigation map while navigating
- **THEN** navigation stops on the car display and the screen returns to the root menu

#### Scenario: System back still leaves with the stop action shown

- **WHEN** the user is navigating and the map action strip shows the stop and route-description actions without an explicit back button
- **THEN** pressing system back still stops navigation and returns the screen to the root menu
