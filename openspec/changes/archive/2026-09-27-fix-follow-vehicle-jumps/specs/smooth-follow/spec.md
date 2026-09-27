# smooth-follow Delta Specification

## Purpose

Smooth follow-mode map scrolling on the phone renderer by extrapolating the displayed viewport between 1 Hz GPS fixes and easing corrections on fix arrival.

## MODIFIED Requirements

### Requirement: Display center extrapolation

The system SHALL extrapolate the displayed viewport center between GPS fixes in follow mode. The predicted position SHALL be computed from the last fix position, GPS speed, smoothed heading, and elapsed time since the fix: `predicted = lastFix + speed * heading * (now - fixTime)`. The map SHALL be scrolled to the predicted position by blitting the front buffer each display frame. The displayed frame's viewport SHALL be centered on the anchor center of the position it was rendered for (see "Anchor-centered follow framing"), so the blitted offset carries the prediction drift only.

The prediction base SHALL be the SAME position source the follow framing renders on: the engine-filtered position while route guidance is active, the raw GPS fix otherwise. The rendered frame's center and the predicted/displayed position SHALL therefore describe the same vehicle location.

#### Scenario: Vehicle moves at constant speed between fixes

- **WHEN** a fix arrives at position P with speed 14 m/s and heading 90°, and 500 ms later no new fix has arrived
- **THEN** the displayed viewport center SHALL be approximately 7 m east of P
- **AND** the map SHALL be scrolled by blitting the front buffer, not by a full render


#### Scenario: No speed or heading available

- **WHEN** the last fix has no speed or heading (speed unknown, bearing < 0)
- **THEN** the displayed viewport SHALL remain at the last fix position until the next fix
- **AND** no extrapolation SHALL be applied


#### Scenario: Default anchors reproduce the pre-anchor framing

- **WHEN** both vehicle anchors are at their `center/center` defaults and the phone map is in follow mode
- **THEN** the displayed frame, the blitted offset and the marker position SHALL be identical to follow mode without anchor presets


#### Scenario: Prediction base matches the rendered position during guidance

- **WHEN** route guidance is active and the navigation engine reports a snapped position several meters from the raw GPS fix (junction snap, GPS jitter)
- **THEN** the prediction SHALL extrapolate from the engine position, not from the raw GPS fix
- **THEN** the rendered frame, the blit offset, and the marker SHALL stay mutually consistent (no lateral correction jump at the snap)

### Requirement: Correction easing

The system SHALL ease the displayed viewport from the predicted position toward the true fix on arrival over approximately 200-300 ms instead of snapping. The easing SHALL absorb extrapolation drift caused by curves and acceleration.

The system SHALL absorb extrapolation drift at fix arrival. The displayed position SHALL advance monotonically along the direction of travel across fixes; it SHALL NOT move backward at fix arrival. When the true fix lies at or behind the displayed position, the display SHALL hold at its current position until extrapolation from the new fix advances beyond it. When the true fix lies ahead of the displayed position, the display SHALL ease forward toward it over approximately 200-300 ms.

The monotonic advance keeps the residual display lead bounded (speed × ease time constant) and prevents the per-fix "moved forward, jumped backward" sawtooth.

#### Scenario: Prediction drifts before fix arrival

- **WHEN** the vehicle turns a corner and the predicted position is 8 m from the true fix when the fix arrives
- **THEN** the displayed viewport SHALL move smoothly from the predicted position to the true fix over ~200-300 ms
- **AND** the map SHALL NOT jump to the true fix in a single frame
- **THEN** the displayed viewport SHALL hold its current position (no backward slide)
- **AND** the display SHALL resume forward once extrapolation from the new fix advances beyond it

#### Scenario: Fix arrives close to prediction

- **WHEN** the true fix is within 2 m of the predicted position
- **THEN** the correction SHALL be imperceptible (sub-pixel easing)
- **AND** no full render SHALL be triggered solely by the correction


#### Scenario: Fix arrives ahead of display

- **WHEN** the true fix is ahead of the displayed position (e.g., after acceleration)
- **THEN** the displayed viewport SHALL ease forward to the true fix over approximately 200-300 ms
- **AND** no full render SHALL be triggered solely by the correction while the offset stays within the overrun margin

### Requirement: Extrapolation loop gating

The system SHALL run the extrapolation display loop only in follow mode while the vehicle is moving. The loop SHALL stop when the vehicle is stationary (speed below a threshold) or follow mode is disengaged.

#### Scenario: Vehicle stops

- **WHEN** the vehicle speed drops below the movement threshold
- **THEN** the extrapolation loop SHALL stop
- **AND** the displayed viewport SHALL remain at the last position
- **AND** the blit offset SHALL be frozen (not zeroed)

#### Scenario: User pans the map

- **WHEN** the user pans or zooms (follow mode disengaged)
- **THEN** the extrapolation loop SHALL stop
- **AND** the map SHALL follow the user's gestures normally


#### Scenario: Vehicle resumes after a stop

- **WHEN** the vehicle accelerates again after a brief stop (e.g., a traffic light) and the speed crosses back above the threshold
- **THEN** the display SHALL resume extrapolating from the frozen position
- **AND** the map and marker SHALL NOT snap (no jump at stop/resume)

### Requirement: Prediction state update

The system SHALL update the prediction state (position, speed, heading, fix time) on every GPS fix. A fix SHALL update the state without necessarily triggering a render.

#### Scenario: Fix updates prediction state only

- **WHEN** a new fix arrives while the predicted position is still inside the overrun region
- **THEN** the prediction state SHALL be updated to the new fix
- **AND** no full render SHALL be initiated
- **AND** the displayed viewport SHALL NOT be re-centered on the fix

#### Scenario: Fix arrives during a zoom animation

- **WHEN** a new fix arrives while an auto-zoom animation is playing
- **THEN** the zoom animation SHALL continue from the displayed position (not from a re-centered raw fix)
- **AND** the frame SHALL NOT jump between zoom-only and center re-commits
