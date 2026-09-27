# auto/map-pan Specification

## Purpose
Panning the Android Auto map surface: the driver pans the map while free driving, the panned viewport survives speed-band crossings and fix updates, and follow mode plus speed-driven auto-zoom re-engage only on an explicit exit from panning.

## Requirements

### Requirement: Speed-band crossing while panned

The system SHALL keep follow mode and speed-driven auto-zoom suspended when the vehicle speed crosses a speed-band boundary while the map is panned, so a speed change during a pan can never re-engage follow or change the zoom.

#### Scenario: No zoom change across a speed band

- **WHEN** the vehicle speed crosses a speed-band boundary while the map is panned
- **THEN** the map zoom does not change

#### Scenario: Follow stays suspended across a speed band

- **WHEN** the vehicle speed crosses a speed-band boundary while the map is panned
- **THEN** the viewport stays at the panned position and follow mode remains suspended

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
