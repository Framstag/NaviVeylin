## ADDED Requirements

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
