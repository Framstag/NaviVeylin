# smooth-follow Specification

## Purpose
Smooth follow-mode map scrolling on the phone renderer by extrapolating the displayed viewport between 1 Hz GPS fixes and easing corrections on fix arrival.

## Requirements

### Requirement: Display center extrapolation

The system SHALL extrapolate the displayed viewport center between GPS fixes in follow mode. The predicted position SHALL be computed from the last fix position, GPS speed, smoothed heading, and elapsed time since the fix: `predicted = lastFix + speed * heading * (now - fixTime)`. The map SHALL be scrolled to the predicted position by blitting the front buffer each display frame. The displayed frame's viewport SHALL be centered on the anchor center of the position it was rendered for (see "Anchor-centered follow framing"), so the blitted offset carries the prediction drift only.

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

### Requirement: Correction easing

The system SHALL ease the displayed viewport from the predicted position toward the true fix on arrival over approximately 200-300 ms instead of snapping. The easing SHALL absorb extrapolation drift caused by curves and acceleration.

#### Scenario: Prediction drifts before fix arrival

- **WHEN** the vehicle turns a corner and the predicted position is 8 m from the true fix when the fix arrives
- **THEN** the displayed viewport SHALL move smoothly from the predicted position to the true fix over ~200-300 ms
- **AND** the map SHALL NOT jump to the true fix in a single frame

#### Scenario: Fix arrives close to prediction

- **WHEN** the true fix is within 2 m of the predicted position
- **THEN** the correction SHALL be imperceptible (sub-pixel easing)
- **AND** no full render SHALL be triggered solely by the correction

### Requirement: Display-only prediction

The system SHALL feed predicted positions only to the map display (viewport blit and marker overlay). The navigation engine SHALL receive only real GPS fixes.

#### Scenario: Predicted position never reaches navigation engine

- **WHEN** the display extrapolates the viewport between fixes
- **THEN** `NavigationController.processLocation()` SHALL NOT be called with a predicted position
- **AND** routing decisions SHALL be based only on real fixes

### Requirement: Extrapolation loop gating

The system SHALL run the extrapolation display loop only in follow mode while the vehicle is moving. The loop SHALL stop when the vehicle is stationary (speed below a threshold) or follow mode is disengaged.

#### Scenario: Vehicle stops

- **WHEN** the vehicle speed drops below the movement threshold
- **THEN** the extrapolation loop SHALL stop
- **AND** the displayed viewport SHALL remain at the last position

#### Scenario: User pans the map

- **WHEN** the user pans or zooms (follow mode disengaged)
- **THEN** the extrapolation loop SHALL stop
- **AND** the map SHALL follow the user's gestures normally

### Requirement: Prediction state update

The system SHALL update the prediction state (position, speed, heading, fix time) on every GPS fix. A fix SHALL update the state without necessarily triggering a render.

#### Scenario: Fix updates prediction state only

- **WHEN** a new fix arrives while the predicted position is still inside the overrun region
- **THEN** the prediction state SHALL be updated to the new fix
- **AND** no full render SHALL be initiated

### Requirement: Anchor-centered follow framing

In phone follow mode the map SHALL render each frame with a viewport centered on the **anchor center** of the position the frame is rendered for: the geographic point that projects the vehicle to the configured anchor screen fraction under the current map rotation. The vehicle's geographic position SHALL therefore project to the anchor inside the rendered frame, and the anchor SHALL be applied exactly once — never again as an additional shift of the blitted frame or of the marker projection.

The active anchor SHALL be the routing anchor while turn-by-turn route guidance is active and the free-driving anchor otherwise; each is one of the 15 presets of the shared vehicle-position grid.

The follow blit offset (the prediction drift) SHALL remain inside the overrun margin of the rendered frame, so no uncovered strip of the surface background is ever visible and no part of the map content is pushed off-screen. Re-centering (the re-center control and follow re-engage) SHALL commit the anchor-centered viewport, so the configured anchor is restored without a snap.

#### Scenario: Vehicle projects to the active anchor

- **WHEN** the phone map is in follow mode with a non-center anchor (for example bottom-center) for the active mode
- **THEN** the vehicle's geographic position SHALL project to that anchor screen fraction in the displayed frame
- **AND** the map content around the vehicle SHALL be visible at the anchor (no grey/black uncovered strip, no content pushed off-screen)

#### Scenario: Every preset stays inside the overrun buffer

- **WHEN** the user selects any of the 15 anchor presets and drives in follow mode, in north-up and in heading-up orientation
- **THEN** the applied blit offset SHALL NOT exceed the overrun margin of the rendered frame
- **AND** the visible map SHALL cover the full surface with map content

#### Scenario: Anchor follows guidance state

- **WHEN** the user configured distinct routing and free-driving anchors
- **AND** route guidance starts or stops
- **THEN** the follow framing SHALL switch between the routing and the free-driving anchor on the next position update

#### Scenario: Anchor restored after manual pan or re-center

- **WHEN** the user pans the map (follow disengaged) and re-engages follow, or activates the re-center control
- **THEN** the next committed frame SHALL be anchor-centered on the current position
- **AND** the vehicle SHALL reappear at the configured anchor without a framing snap

#### Scenario: Anchor held under heading-up rotation

- **WHEN** the map is rotated (heading-up follow or a manual rotation) and the vehicle moves in follow mode
- **THEN** the render target SHALL be re-derived from the rotated viewport so the vehicle keeps projecting to the anchor screen fraction

### Requirement: Vehicle position anchor in follow mode

The phone map SHALL keep the vehicle marker in follow mode at the configured anchor position instead of at the screen center. The active anchor depends on the driving state: the routing anchor while turn-by-turn route guidance is active, the free-driving anchor otherwise. Each anchor is one of 15 positions on a 5×3 grid (horizontal 10/30/50/70/90% of the screen width, vertical 10/50/90% of the screen height); the default for both is center/center (50% width, 50% height), which reproduces the pre-feature framing exactly. To place the marker at the anchor, the map render target SHALL be shifted so the vehicle's geographic position projects to the anchor under the current map rotation.

#### Scenario: Default anchors reproduce today's framing

- **GIVEN** both anchors are at their defaults (center/center)
- **WHEN** the phone map is in follow mode
- **THEN** the vehicle marker projects to the center of the map canvas
- **AND** the map framing is identical to follow mode without anchor presets

#### Scenario: Routing anchor active during guidance

- **GIVEN** the user configured a distinct routing anchor
- **WHEN** turn-by-turn route guidance is active and the map is in follow mode
- **THEN** the vehicle marker stays at the routing anchor position

#### Scenario: Free-driving anchor active without guidance

- **GIVEN** the user configured a distinct free-driving anchor
- **WHEN** no route guidance is active and the map is in follow mode (browsing/free driving)
- **THEN** the vehicle marker stays at the free-driving anchor position

#### Scenario: Anchor kept under map rotation

- **WHEN** the map is rotated (navigation or free-form orientation) and the vehicle moves in follow mode
- **THEN** the map render target shifts so the vehicle marker keeps projecting to the active anchor position

#### Scenario: Anchor restored after manual pan or recenter

- **WHEN** the user pans the map (follow disengaged) and re-engages follow, or activates the recenter control
- **THEN** the map returns to the anchor-centered framing without a snap
