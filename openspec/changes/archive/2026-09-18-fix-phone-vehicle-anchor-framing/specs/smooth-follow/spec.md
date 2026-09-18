## MODIFIED Requirements

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

## ADDED Requirements

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
