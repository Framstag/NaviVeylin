# navigation-status-details Specification

## Purpose

Lets the driver expand the routing status card during active navigation into a full-screen view that shows the route description list with the current step highlighted, without leaving the map.

## ADDED Requirements

### Requirement: Routing status card is clickable

During active navigation, the NavigationStateOverlay card SHALL be tappable.

#### Scenario: Tap opens full-screen details

- **WHEN** the user taps the routing status card during active navigation
- **THEN** a full-screen view SHALL open showing the route description

#### Scenario: Tap does not stop navigation

- **WHEN** the user taps the routing status card
- **THEN** navigation SHALL continue uninterrupted
- **AND** the stop-navigation button SHALL remain available in the expanded view

### Requirement: Full-screen view shows status content

The expanded view SHALL keep the routing status content visible: current road name and the ETA / remaining time / remaining distance / speed stats.

#### Scenario: Status content preserved

- **WHEN** the full-screen view is open
- **THEN** the current road name SHALL be shown
- **AND** the ETA, remaining time, remaining distance, and speed SHALL be shown

### Requirement: Route description list

The expanded view SHALL show the route description list, styled like the route details view (`RouteSummaryDialog`): each step with its turn icon, distance, and instruction text.

#### Scenario: Steps listed in order

- **WHEN** the full-screen view is open
- **THEN** each route instruction SHALL be listed in order from start to destination
- **AND** each step SHALL show the turn type icon, distance, and instruction text

#### Scenario: List scrollable

- **WHEN** the instruction list exceeds the visible area
- **THEN** the list SHALL be scrollable

### Requirement: Current step selected at top

The current navigation step SHALL be visually highlighted and shown at the top of the route description list.

#### Scenario: Current step highlighted

- **WHEN** the full-screen view is open during active navigation
- **THEN** the current navigation step SHALL be visually highlighted (e.g. primary container background, bold text)

#### Scenario: Current step at top of list

- **WHEN** the full-screen view is open
- **THEN** the list SHALL be scrolled so the current step is the first visible item

#### Scenario: Highlight follows progress

- **WHEN** the user progresses to the next navigation step while the view is open
- **THEN** the highlighted step SHALL advance to the new current step
- **AND** the list SHALL scroll to keep the new current step at the top

### Requirement: Dismiss returns to map

Dismissing the expanded view SHALL return to the map with the routing status card still visible.

#### Scenario: Dismiss via close button

- **WHEN** the user taps the close (X) button
- **THEN** the full-screen view SHALL close
- **AND** the routing status card SHALL remain visible

#### Scenario: Dismiss via back gesture

- **WHEN** the user presses the system back button
- **THEN** the full-screen view SHALL close
- **AND** the routing status card SHALL remain visible
