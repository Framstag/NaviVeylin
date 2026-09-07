## MODIFIED Requirements

### Requirement: Leave navigation at any time

The system SHALL let the user stop active navigation from the car display at any time via the host ETA card stop button or via system back, and SHALL NOT show an explicit stop or back button in the navigation map action strip.

#### Scenario: Stop navigation from car

- **WHEN** the user presses system back during navigation
- **THEN** navigation stops on the car display and the screen returns to the root menu

#### Scenario: Stop action shown on navigation map

- **WHEN** the user is navigating on the car display with a travel estimate
- **THEN** the host ETA card shows a stop action and the navigation map action strip shows no stop or back button

#### Scenario: Stop action stops navigation

- **WHEN** the user activates the stop action on the host ETA card while navigating
- **THEN** navigation stops on the car display and the screen returns to the root menu

#### Scenario: System back still leaves with the stop action shown

- **WHEN** the user is navigating and the host ETA card shows the stop action
- **THEN** pressing system back still stops navigation and returns the screen to the root menu
