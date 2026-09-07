## ADDED Requirements

### Requirement: Manual map panning while free driving

The system SHALL provide a pan affordance on the free-driving view, letting the driver move the map and pinch-zoom, with follow mode, speed-driven auto-zoom and heading-up rotation suspended while panned and re-engaged on pan exit (see `auto/map-pan`).

#### Scenario: Pan button on free-driving map strip

- **WHEN** the free-driving view is visible
- **THEN** the map action strip shows a pan button beside the exit button

#### Scenario: Map panned while free driving

- **WHEN** the driver pans while free driving
- **THEN** the map viewport moves with the gesture and stays at the panned position

#### Scenario: Follow resumes after pan

- **WHEN** the driver exits pan mode while free driving
- **THEN** the map resumes following the vehicle

## MODIFIED Requirements

### Requirement: Follow mode activated

The system SHALL keep the map centered on the GPS position while free driving (follow mode active), except while the map is panned.

#### Scenario: Map re-centers on GPS position

- **WHEN** the GPS position moves while free driving and the user does not pan the map
- **THEN** the map viewport re-centers on the new position

#### Scenario: Pan disengages follow

- **WHEN** the user pans the map while free driving
- **THEN** the map stops re-centering on the GPS position and stays at the panned position

### Requirement: Auto-zoom by speed

While free driving, the system SHALL adjust the map magnification from the vehicle speed using the shared speed-to-magnification table when follow mode and the auto-zoom setting are active.

#### Scenario: Speed increases zoom out

- **WHEN** the vehicle speed increases while free driving (auto-zoom enabled)
- **THEN** the map magnification decreases (zooms out) per the speed-to-magnification mapping, staying centered on the vehicle

#### Scenario: Speed decreases zoom in

- **WHEN** the vehicle speed decreases while free driving (auto-zoom enabled)
- **THEN** the map magnification increases (zooms in) per the speed-to-magnification mapping, staying centered on the vehicle

#### Scenario: Manual zoom suspends auto-zoom

- **WHEN** the user zooms manually while free driving
- **THEN** auto-zoom is suspended and the map stays at the manual zoom level while the speed stays in the same speed band

#### Scenario: Pan suspends auto-zoom

- **WHEN** the user pans the map while free driving
- **THEN** auto-zoom is suspended and the map stays at the panned zoom level while the speed stays in the same speed band

#### Scenario: Band change re-engages auto-zoom

- **WHEN** the speed crosses a speed-table band boundary after a manual zoom
- **THEN** auto-zoom re-engages and adjusts to the speed-appropriate magnification

### Requirement: Exit free driving

The system SHALL provide an action to leave the free-driving view and return to the map view.

#### Scenario: Exit returns to map view

- **WHEN** the user activates the exit action (an "x" button) on the free-driving view
- **THEN** the system returns to the map view (the previous map screen)

#### Scenario: No menu affordance in free driving

- **WHEN** the free-driving view is shown
- **THEN** the map action strip shows the exit "x" button and the pan button (no back/menu affordance)

#### Scenario: System back exits free driving

- **WHEN** the user presses the system back action while in the free-driving view
- **THEN** the system returns to the map view
