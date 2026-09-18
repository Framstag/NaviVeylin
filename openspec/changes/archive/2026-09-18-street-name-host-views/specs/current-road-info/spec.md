## MODIFIED Requirements

### Requirement: Current road info shown in free driving (no active route)

The system SHALL display the current road's name and ref on the phone map when no route is active, derived from the bearing-aware road lookup at the GPS position, so the driver sees the street they are driving on without starting navigation. The label SHALL be anchored at the bottom-center of the map by default; when the active follow anchor preset is in the bottom row, the label SHALL be anchored at the top-center instead, so it never covers the vehicle marker; when the preset is in the top row, the label SHALL be anchored at the bottom-center. Top placement SHALL clear the status-bar/front-camera safe area on the phone (the map renders edge-to-edge, so the top-safe inset from the system window insets must be applied) — same rule that keeps every other top-mounted overlay clear of the camera. Anchor rule matches the Android Auto free-driving and browse labels (same rows, same behavior — parity rule).

#### Scenario: Street label shown while driving

- **GIVEN** the phone map is visible and no route is active
- **WHEN** the GPS position is on a road with a name or ref
- **THEN** the map shows a street label with the road's ref and name (e.g. "B 1 Hauptstrasse"), anchored at the bottom-center

#### Scenario: Street label moves to the top for bottom-row anchors

- **GIVEN** the phone map is visible and no route is active
- **WHEN** the active follow anchor preset is in the bottom row (bottom-center, bottom-left, bottom-right, bottom-far-left, bottom-far-right) and the GPS position is on a road with a name or ref
- **THEN** the street label is shown at the top-center of the map, clear of the vehicle marker

#### Scenario: Street label at the bottom for top-row anchors

- **GIVEN** the phone map is visible and no route is active
- **WHEN** the active follow anchor preset is in the top row
- **THEN** the street label is shown at the bottom-center of the map

#### Scenario: Street label clears the status bar/camera area on the phone

- **GIVEN** the phone map is visible and no route is active
- **WHEN** the active follow anchor preset is in the bottom row and a road with a name or ref is found at the GPS position
- **THEN** the top-center street label is positioned below the system status-bar/camera safe inset (edge-to-edge screen), so the pill never overlaps the front camera or status bar

#### Scenario: Street label updates on street change

- **GIVEN** the phone map is visible and no route is active
- **WHEN** the vehicle moves onto a different road
- **THEN** the street label updates to the new road

#### Scenario: No label when no road found

- **GIVEN** the phone map is visible and no route is active
- **WHEN** no road is found at the GPS position
- **THEN** no street label is shown and no stale text from a previous road remains

#### Scenario: Street label hidden during navigation

- **GIVEN** an active navigation session
- **WHEN** the phone map is visible
- **THEN** the free-driving street label is not shown (the navigation road-info row is used instead)
