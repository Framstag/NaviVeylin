## Purpose

Manual map panning on the Android Auto map surfaces (free driving and turn-by-turn navigation): the driver can move the map and pinch-zoom while the vehicle keeps moving, with follow mode, speed-driven auto-zoom and heading-up rotation suspended for the duration of the pan and resumed on exit.

## ADDED Requirements

### Requirement: Pan affordance on map surfaces

The system SHALL provide a pan affordance on the free-driving and navigation map surfaces that the driver can activate to enter pan mode, and SHALL forward pan gestures to the app while pan mode is active.

#### Scenario: Pan affordance shown

- **WHEN** the free-driving or navigation view is visible
- **THEN** the map action strip shows a pan button

#### Scenario: Pan mode entered

- **WHEN** the driver activates the pan button
- **THEN** the host enters pan mode and forwards pan gestures to the map surface

#### Scenario: Pan mode exited

- **WHEN** the driver deactivates the pan button
- **THEN** the host exits pan mode and stops forwarding pan gestures

### Requirement: Manual pan moves the map

The system SHALL move the map viewport with the pan gesture while pan mode is active, keeping the map at the panned position.

#### Scenario: Map follows the pan gesture

- **WHEN** the driver pans while pan mode is active
- **THEN** the map viewport moves with the gesture

#### Scenario: Map stays panned

- **WHEN** the driver stops panning
- **THEN** the map stays at the panned position and does not snap back to the vehicle

### Requirement: Pinch zoom while panned

The system SHALL zoom the map around the gesture focus while pan mode is active.

#### Scenario: Pinch zooms the map

- **WHEN** the driver pinches while pan mode is active
- **THEN** the map zooms around the pinch focus

### Requirement: Follow suspended while panned

The system SHALL suspend follow mode while the map is panned: GPS fixes update the position marker but do not move the viewport.

#### Scenario: Marker moves, viewport stays

- **WHEN** the vehicle moves while the map is panned
- **THEN** the position marker moves to the new position but the viewport stays at the panned position

### Requirement: Auto-zoom suspended while panned

The system SHALL suspend speed-driven auto-zoom while the map is panned.

#### Scenario: No speed zoom during pan

- **WHEN** the vehicle speed changes while the map is panned
- **THEN** the map zoom does not change

### Requirement: Heading-up rotation frozen while panned

The system SHALL freeze heading-up rotation while the map is panned so the map does not rotate under the gesture.

#### Scenario: Map does not rotate during pan

- **WHEN** the vehicle bearing changes while the map is panned
- **THEN** the map keeps the panned orientation

### Requirement: Follow re-engaged on pan exit

The system SHALL re-engage follow mode when the driver exits pan mode, so the map resumes tracking the vehicle.

#### Scenario: Follow resumes on pan exit

- **WHEN** the driver exits pan mode
- **THEN** the map resumes following the vehicle position
