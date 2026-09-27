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

The system SHALL keep the vehicle marker during free driving at the configured free-driving anchor position while follow mode is active, instead of at the screen center. The anchor is one of 15 positions on a 5×3 grid (horizontal 10/30/50/70/90% of the surface width, vertical 10/50/90% of the surface height). The default free-driving anchor is center/center (50% width, 50% height), which reproduces the pre-feature framing exactly. To place the marker at the anchor, the map render target SHALL be shifted so the vehicle's geographic position projects to the anchor under the heading-up rotation (free driving is always heading-up).

The system SHALL keep the map centered on the GPS position while free driving (follow mode active), except while the map is panned.

#### Scenario: Map re-centers on GPS position

- **WHEN** the GPS position moves while free driving and the user does not pan the map
- **THEN** the map render target shifts so the map keeps the vehicle marker at the configured anchor position
- **AND** with the default center/center anchor this reproduces re-centering the viewport on the GPS position
- **THEN** the map viewport re-centers on the new position

#### Scenario: Default anchor reproduces today's framing

- **GIVEN** the free-driving anchor is at its default center/center
- **WHEN** free driving is active in follow mode
- **THEN** the vehicle marker projects to the center of the free-driving surface
- **AND** the map framing is identical to free driving without anchor presets


#### Scenario: Anchor kept under heading-up rotation

- **WHEN** the vehicle bearing changes while free driving
- **THEN** the map rotates heading-up with the bearing
- **AND** the vehicle marker keeps projecting to the chosen anchor position


#### Scenario: Anchor restored after manual pan

- **WHEN** the user pans the map during free driving (follow suspended)
- **AND** the user then stops panning
- **THEN** follow mode re-engages and the vehicle marker returns to the free-driving anchor without a snap


#### Scenario: Auto-zoom keeps the anchored vehicle in view

- **WHEN** speed-driven auto-zoom changes the magnification while free driving
- **THEN** the map zooms around the anchor position
- **AND** the vehicle marker stays at the chosen anchor


#### Scenario: Host panel clearance clamps only the covered presets

- **GIVEN** the host draws its route-status UI over the leading 40% of the surface width
- **WHEN** the driver selects a preset whose fraction falls inside that band
- **THEN** the resolved anchor SHALL be just inside the visible strip, clear of the host UI
- **AND** a preset outside the band (including the default center/center) SHALL keep its exact fraction


#### Scenario: Bottom chrome clamps the vehicle above it

- **GIVEN** the host overlays the bottom of the map surface with control chrome (e.g. the AAOS bottom bar) beyond the surface it grants
- **WHEN** the free-driving anchor preset is in the bottom row and the anchor fraction would land under that chrome
- **THEN** the resolved anchor SHALL move up so the vehicle stays inside the host-guaranteed visible area
- **AND** presets not under the chrome SHALL keep their exact fraction


#### Scenario: Surface uses its own free-driving anchor

- **GIVEN** the phone's free-driving anchor differs from Android Auto's free-driving anchor
- **WHEN** free driving runs on Android Auto
- **THEN** the Android Auto free-driving anchor SHALL frame the map
- **AND** the phone's value SHALL NOT be applied


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


#### Scenario: Band change re-engages auto-zoom

- **WHEN** the speed crosses a speed-table band boundary after a manual zoom
- **THEN** auto-zoom re-engages and adjusts to the speed-appropriate magnification


#### Scenario: Pan suspends auto-zoom

- **WHEN** the user pans the map while free driving
- **THEN** auto-zoom is suspended and the map stays at the panned zoom level while the speed stays in the same speed band

### Requirement: Exit free driving

The system SHALL provide an action to leave the free-driving view and return to the map view.

#### Scenario: Exit returns to map view

- **WHEN** the user activates the exit action (an "x" button) on the free-driving view
- **THEN** the system returns to the map view (the previous map screen)


#### Scenario: No menu affordance in free driving

- **WHEN** the free-driving view is shown
- **THEN** the map action strip shows only the exit "x" button (no back/menu affordance)


#### Scenario: System back exits free driving

- **WHEN** the user presses the system back action while in the free-driving view
- **THEN** the system returns to the map view

