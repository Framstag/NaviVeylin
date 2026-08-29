# map-pan-zoom

## MODIFIED Requirements

### Requirement: Pinch-to-zoom

The system SHALL support two-finger pinch to zoom the map viewport with a
continuous (fractional) magnification commit.

- Pinch zoom SHALL use `ProjectionUtils.zoomAtCursor()` to keep the geographic point under the pinch center fixed
- The gesture zoom factor SHALL be applied visually during the drag as a scaled placeholder from the current front buffer, using the placeholder scale factor and anchor origin of the gesture
- On zoom change, the system SHALL immediately display a scaled placeholder from the current front buffer using the exact placeholder scale factor and anchor origin described above
- The epoch SHALL be incremented on zoom change to discard stale renders
- At gesture end, the system SHALL commit the unrounded magnification `startMag + log2gestureZoom` — no rounding to an integer level
- The committed magnification SHALL equal the visual preview factor within floating-point tolerance — no snap at gesture end
- The per-gesture zoom factor SHALL be capped at 16.0× (zoom in) and 1/16 (zoom out), in addition to the viewport magnification headroom clamp between MIN_MAG (4) and MAX_MAG (20)
- The visual preview SHALL keep the same headroom clamp so preview and commit always match inside the magnification limits

#### Scenario: Pinch zoom in

- **WHEN** user places two fingers on the map and spreads them apart by a factor of 2.3 from magnification 14
- **THEN** during the drag the front buffer is displayed scaled by up to 2.3× around the pinch centroid
- **THEN** the geographic point under the pinch center stays fixed
- **THEN** at gesture end the committed magnification is 14 + log2(2.3) ≈ 15.2 (fractional, not rounded to 15)
- **THEN** a full native render is triggered at the fractional magnification after the debounced interval
- **THEN** the rendered frame at the committed magnification matches the gesture preview scale

#### Scenario: Pinch zoom out

- **WHEN** user places two fingers on the map and pinches them together by a factor of 0.4 from magnification 16
- **THEN** at gesture end the committed magnification is 16 + log2(0.4) ≈ 14.68
- **THEN** the geographic point under the pinch center stays fixed
- **THEN** a full native render is triggered at the fractional magnification

#### Scenario: Pinch past the maximum magnification

- **WHEN** magnification is 19 and user pinches outward by a factor of 8 (would reach 22)
- **THEN** the visual preview is clamped by the headroom to 2^(20 − 19) = 2.0× during the drag
- **THEN** at gesture end the magnification is committed as 20
- **THEN** the display matches the preview (no snap-back)

#### Scenario: Zoom clamped at maximum

- **WHEN** magnification is 20 and user pinches to zoom in
- **THEN** the magnification stays at 20 (headroom is 1.0× — no visual scale, no re-render)

#### Scenario: Pinch past the minimum magnification

- **WHEN** magnification is 5 and user pinches inward by a factor of 0.125 (would reach 2)
- **THEN** the visual preview is clamped by the headroom to 2^(5 − 4) = 2.0× during the drag
- **THEN** at gesture end the magnification is committed as 4
- **THEN** the display matches the preview (no snap-back)

#### Scenario: Zoom clamped at minimum

- **WHEN** magnification is 4 and user pinches to zoom out
- **THEN** the magnification stays at 4
- **THEN** no placeholder or re-render occurs

## RENAMED Requirements

- Requirement: Placeholder scale during pinch → (content unchanged, folded into the Pinch-to-zoom requirement bullet: "placeholder scale factor computed as the ratio between the current gesture magnification and the magnification of the last completed native render")