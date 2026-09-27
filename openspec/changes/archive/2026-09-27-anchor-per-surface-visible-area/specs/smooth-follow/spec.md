## MODIFIED Requirements

### Requirement: Vehicle position anchor in follow mode

The phone map SHALL keep the vehicle marker in follow mode at the configured anchor position instead of at the screen center. The active anchor depends on the driving state: the routing anchor while turn-by-turn route guidance is active, the free-driving anchor otherwise. Each anchor is one of 15 positions on a 5×3 grid (horizontal 10/30/50/70/90% of the screen width, vertical 10/50/90% of the screen height); the default for both is center/center (50% width, 50% height), which reproduces the pre-feature framing exactly. To place the marker at the anchor, the map render target SHALL be shifted so the vehicle's geographic position projects to the anchor under the current map rotation.

#### Scenario: Default anchors reproduce today's framing

- **GIVEN** both anchors are at their defaults (center/center)
- **WHEN** the phone map is in follow mode
- **THEN** the vehicle marker projects to the center of the map canvas
- **AND** the map framing is identical to follow mode without anchor presets
- **AND** the phone's navigation overlays are measured (next-turn card, routing-status card, right widget column)
- **THEN** the vehicle marker projects to the EXACT center of the canvas — the default preset collides with no overlay region, so it resolves to (50%, 50%) rather than to the center of the reduced visible area

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


#### Scenario: Bottom anchor stays visible above the routing status card

- **GIVEN** the phone is navigating and the routing-status card covers the bottom of the canvas
- **AND** the routing anchor is bottom-center (50%, 90% of the canvas)
- **WHEN** the map is in follow mode
- **THEN** the resolved vertical fraction SHALL be above the routing-status card, clear of the marker footprint and padding
- **AND** the resolved horizontal fraction SHALL remain exactly 50% (the card covers no horizontal position of the marker, so no horizontal move is applied)
- **AND** the vehicle marker SHALL be fully visible, not covered by the card


#### Scenario: Top anchor stays visible below the turn card

- **GIVEN** the phone is navigating and the next-turn card covers the top of the canvas
- **AND** the routing anchor is top-center (50%, 10% of the canvas)
- **WHEN** the map is in follow mode
- **THEN** the resolved vertical fraction SHALL be below the turn card, clear of the marker footprint and padding
- **AND** the resolved horizontal fraction SHALL remain exactly 50%
- **AND** the vehicle marker SHALL be fully visible, not covered by the card


#### Scenario: Right anchor stays clear of the widget column only when covered

- **GIVEN** the widget column covers the right edge of the canvas
- **AND** the anchor is at 90% width (its marker would fall inside the column)
- **WHEN** the map is in follow mode
- **THEN** the resolved horizontal fraction SHALL be left of the widget column, clear of the marker footprint and padding
- **AND** the vehicle marker SHALL NOT be covered by the column


#### Scenario: Non-covered preset keeps its exact fraction

- **GIVEN** the widget column covers the right edge of the canvas
- **AND** the anchor is at 70% width (its marker stays clear of the column)
- **WHEN** the map is in follow mode
- **THEN** the resolved horizontal fraction SHALL equal exactly 70% — an uncovered preset is not moved by the mere presence of an overlay
- **AND** the vehicle marker SHALL keep the preset's screen position


#### Scenario: Corner preset moves on both axes

- **GIVEN** the phone is navigating (routing-status card covers the bottom, widget column covers the right edge)
- **AND** the routing anchor is bottom-right (70%, 90% of the canvas)
- **WHEN** the map is in follow mode
- **THEN** the resolved fraction SHALL move up above the routing-status card AND left of the widget column
- **AND** the vehicle marker SHALL be fully visible, clear of both the card and the column


#### Scenario: Collision uses the marker footprint and padding

- **GIVEN** a preset whose center still clears an overlay region but whose marker footprint plus padding would overlap it
- **WHEN** the map is in follow mode
- **THEN** the preset SHALL be treated as covered and SHALL move until the footprint and padding are clear of the region
- **AND** the resolved position SHALL leave at least the footprint and padding between the marker and the overlay edge


#### Scenario: No overlay measured means the preset fraction

- **WHEN** no overlay region is measured (browse mode, or a surface without app overlays)
- **THEN** every resolved anchor SHALL equal the preset fraction
- **AND** the framing SHALL be identical to a surface without overlay clearance


#### Scenario: Phone anchor value is independent of Android Auto

- **GIVEN** the phone's routing anchor and the car's routing anchor are both configured
- **WHEN** the driver changes the anchor on one surface
- **THEN** only that surface's stored value changes
- **AND** the other surface keeps its own anchor


#### Scenario: Car falls back to the phone anchor until it has its own value

- **GIVEN** settings written before the per-surface split (no car-specific anchor stored)
- **WHEN** Android Auto reads its anchor
- **THEN** it SHALL use the value the phone stored for that mode
- **AND** once an anchor is chosen on the car, the car SHALL keep that value independently
