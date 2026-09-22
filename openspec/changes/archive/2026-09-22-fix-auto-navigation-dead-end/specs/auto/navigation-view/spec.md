# Android Auto Navigation View Delta (auto/navigation-view)

## Purpose

Extends `auto/navigation-view` "Leave navigation at any time" with the invariant that the navigation view is never a dead end: whenever navigation is inactive, the view either is not shown or can always be left, and every visible back affordance leaves.

## MODIFIED Requirements

### Requirement: Leave navigation at any time

The system SHALL let the user stop active navigation from the car display at any time via the host ETA card stop button or via system back, and SHALL NOT show an explicit stop or back button in the navigation map action strip. The system SHALL NOT leave the user stranded on a navigation view with a non-functional back affordance: whenever navigation is not active, the navigation view SHALL either not be displayed or SHALL be leaveable via each visible back affordance, returning to a usable root (browse map) view.

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

#### Scenario: Navigation ends while the navigation view is shown

- **WHEN** navigation becomes inactive while the navigation view is on screen
- **THEN** the navigation view exits and the browse map root becomes visible
- **AND** a back or stop affordance that was pressed during the transition leaves the view rather than doing nothing

#### Scenario: Navigation view restored with navigation already ended

- **WHEN** the session starts or is restored with the navigation view displayed and navigation is (or becomes) inactive
- **THEN** the user can always leave the view to the browse map root via each visible back affordance
- **AND** no sequence of back presses leaves the user on a navigation view with a non-functional back affordance

#### Scenario: Navigation view is not the un-leaveable session root

- **WHEN** a session is created mid-navigation and navigation subsequently ends
- **THEN** the session root remains (or becomes) a leaveable screen such as the browse map
- **AND** the navigation view, if still displayed, is always exitable back to that root
