# map-rotation-gesture Specification (delta)

## MODIFIED Requirements

### Requirement: Two-finger rotation gesture

The system SHALL support a two-finger rotation gesture on the map canvas that rotates the map viewport by the angle traced by the fingers, applied live to the currently displayed map without triggering pan or zoom as a side effect of the rotation motion.

The rotation angle SHALL be normalized to the range `[-π, π]` radians after each gesture, so repeated rotations cannot grow the angle unbounded.

The gesture SHALL respond to slow rotations whose per-event angle deltas are small: the handler SHALL accumulate raw angle deltas across events and report the accumulated rotation once it exceeds a small threshold, so high-refresh-rate touch sampling (which delivers ~0.01 rad per event during a normal rotation) cannot swallow the gesture.

The rotation SHALL be anchored at the midpoint of the two fingers: the geographic point under the midpoint at gesture start SHALL remain under the midpoint for the whole gesture AND after the committed re-render — the viewport center SHALL be adjusted at gesture end so the anchor holds in the rendered frame.

At gesture end, the display SHALL keep the final rotation angle until the re-render at that angle lands — the map SHALL NOT show an intermediate frame at the pre-gesture angle between gesture end and render completion.

#### Scenario: Two-finger rotate clockwise

- **WHEN** the user places two fingers on the map canvas
- **AND** rotates them clockwise
- **THEN** the map SHALL rotate clockwise by the same angle
- **AND** the rotation SHALL be applied to the currently displayed map without any render call during the gesture
- **AND** the map center SHALL NOT drift while the fingers rotate around a fixed midpoint

#### Scenario: Two-finger rotate counter-clockwise

- **WHEN** the user places two fingers on the map canvas
- **AND** rotates them counter-clockwise
- **THEN** the map SHALL rotate counter-clockwise by the same angle
- **AND** the rotation SHALL be applied to the currently displayed map without any render call during the gesture
- **AND** the map center SHALL NOT drift while the fingers rotate around a fixed midpoint

#### Scenario: Slow rotation with small per-event deltas

- **WHEN** the user rotates two fingers slowly so that each touch event carries an angle delta below the old per-event threshold (e.g. 0.9° per event at high sampling rate)
- **THEN** the map SHALL still rotate by the total angle traced by the fingers
- **AND** the total reported rotation SHALL match the fingers' total rotation within 0.3 rad

#### Scenario: Rotation anchored at the finger midpoint

- **GIVEN** the user places two fingers centered on a visible object that is not at the screen center
- **WHEN** the fingers rotate around their midpoint
- **THEN** the object under the midpoint SHALL remain at the same screen pixel under the fingers for the whole gesture (the rotation pivot is the finger midpoint, not the screen center)
- **AND** at gesture end the object SHALL still be at the same screen pixel after the committed re-render (the viewport center is adjusted so the anchor holds)

#### Scenario: Map stays visible during rotation

- **WHEN** the user rotates two fingers to any angle, including near 180°
- **THEN** the map SHALL remain covering the canvas for moderate rotation angles (within the overrun-buffer margin)
- **AND** empty regions MAY appear at large rotation angles around an off-center midpoint (standard map-app behavior; the map does not swing off-screen for moderate angles)

#### Scenario: Zoom preview does not exceed the commit range at the limits

- **WHEN** the user pinches to zoom in while the magnification is at the maximum (or zoom out at the minimum)
- **THEN** the live visual zoom SHALL be clamped to the headroom the gesture-end commit can deliver
- **AND** the map SHALL NOT zoom in visually and then snap back on gesture end

#### Scenario: Rotation is re-rendered on gesture end

- **WHEN** the user finishes a two-finger rotation gesture (lifts at least one finger)
- **THEN** the map SHALL be re-rendered exactly once with the final rotation angle
- **AND** the re-rendered frame SHALL keep the finger-midpoint anchor (the viewport center is adjusted so the object under the midpoint stays under it)
- **AND** labels on the re-rendered map SHALL be drawn in the correct direction (point labels upright, path labels along the rotated roads)

#### Scenario: No temporary angle jump on gesture end

- **WHEN** the user finishes a two-finger rotation gesture (lifts at least one finger)
- **THEN** the display SHALL keep the final rotation angle while the re-render at that angle is in flight
- **AND** the map SHALL NOT show any frame at the pre-gesture rotation angle between gesture end and render completion
- **AND** once the re-render lands, the map SHALL show the final rotation angle with no further change

#### Scenario: Rotation does not zoom

- **WHEN** the user rotates two fingers while keeping the distance between them constant
- **THEN** the map magnification SHALL remain unchanged
- **AND** a distance change of less than 20% from the gesture start SHALL NOT change the magnification

#### Scenario: Rotation angle stays bounded

- **WHEN** the user rotates the map repeatedly across multiple gestures
- **THEN** the accumulated rotation angle SHALL remain within `[-π, π]` radians
