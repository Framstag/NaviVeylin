# map-pan-zoom Specification (Delta)

## ADDED Requirements

### Requirement: Pinch placeholder scale is derived from magnification delta

The system SHALL compute the placeholder scale during a pinch-to-zoom gesture as the ratio between the current gesture magnification and the magnification of the last completed native render.

- The scale factor SHALL be `currentMagnification / lastRenderedMagnification`.
- The scale factor SHALL be applied to the front buffer every frame while the gesture is active.

#### Scenario: Zooming in from level 10 to 12

- **GIVEN** the last native render was at magnification 10
- **WHEN** the gesture updates magnification to 11
- **THEN** the placeholder SHALL be drawn at scale 1.1 centered on the pinch focal point

#### Scenario: Zooming out from level 12 to 10

- **GIVEN** the last native render was at magnification 12
- **WHEN** the gesture updates magnification to 11
- **THEN** the placeholder SHALL be drawn at scale 0.9167 centered on the pinch focal point

### Requirement: Pinch placeholder anchor keeps focal point stationary

The system SHALL compute the placeholder draw origin so the geographic point under the pinch focal point stays at the same screen pixel while the gesture is active.

- Let `focus` be the screen pixel of the pinch focal point.
- Let `scaledFocus` be `focus * scaleFactor`.
- The draw origin SHALL be `focus - scaledFocus`.

#### Scenario: Two-finger pinch centered on a building

- **GIVEN** the user places two fingers centered on a visible building
- **WHEN** the fingers spread apart to zoom in
- **THEN** the building under the focal point SHALL remain at the same screen pixel until the native render replaces the placeholder

## MODIFIED Requirements

### Requirement: Touch-based pan

The system SHALL support single-finger drag to pan the map viewport.

- Panning SHALL update the viewport center latitude/longitude in real time using Mercator projection
- Pan distance SHALL be converted from screen pixels to geographic delta using `ProjectionUtils.dragDeltaToNewCenter()` with the current magnification, viewport dimensions, and display DPI
- During a pan gesture, the system SHALL use sub-region blit from the overrun buffer when the new viewport is within bounds
- When the pan extends beyond the overrun buffer, the system SHALL trigger a full re-render at the new center
- Pan gesture events SHALL be debounced at 50ms before triggering a full re-render
- Pan SHALL feel responsive — gesture tracking SHALL NOT block the UI thread

#### Scenario: Pan map east

- **WHEN** user places one finger on the map and drags left
- **THEN** the viewport center moves east by the geographic distance computed via Mercator projection
- **WHEN** the new viewport is within the overrun buffer
- **THEN** the visible sub-region is blitted from the overrun buffer without a native re-render
- **WHEN** user lifts finger
- **THEN** the viewport is persisted

#### Scenario: Pan map south

- **WHEN** user places one finger on the map and drags up
- **THEN** the viewport center moves south by the geographic distance computed via Mercator projection
- **WHEN** the new viewport is within the overrun buffer
- **THEN** the visible sub-region is blitted from the overrun buffer without a native re-render

#### Scenario: Pan beyond overrun buffer triggers re-render

- **WHEN** user pans far enough that the viewport extends beyond the overrun buffer
- **THEN** a full native render is triggered at the new center
- **THEN** a new overrun buffer is created

### Requirement: Pinch-to-zoom

The system SHALL support two-finger pinch to zoom the map viewport.

- Pinch zoom SHALL use `ProjectionUtils.zoomAtCursor()` to keep the geographic point under the pinch center fixed
- The magnification SHALL be clamped to a minimum of 4 and a maximum of 18
- On zoom change, the system SHALL immediately display a scaled placeholder from the current front buffer using the exact placeholder scale factor and anchor origin described above
- Zoom gesture events SHALL be debounced at 200 ms before triggering a full re-render
- The epoch SHALL be incremented on zoom change to discard stale renders

#### Scenario: Pinch zoom in

- **WHEN** user places two fingers on the map and spreads them apart
- **THEN** the magnification level increases
- **THEN** the geographic point under the pinch center stays fixed
- **THEN** a scaled placeholder is displayed immediately at the correct scale and anchored to the pinch focal point
- **WHEN** the debounce period elapses
- **THEN** a full native render is triggered at the new magnification

#### Scenario: Pinch zoom out

- **WHEN** user places two fingers on the map and pinches them together
- **THEN** the magnification level decreases
- **THEN** the geographic point under the pinch center stays fixed
- **THEN** a scaled placeholder is displayed immediately at the correct scale and anchored to the pinch focal point
- **WHEN** the debounce period elapses
- **THEN** a full native render is triggered at the new magnification

#### Scenario: Zoom clamped at minimum

- **WHEN** magnification is 4 and user pinches to zoom out
- **THEN** the magnification stays at 4
- **THEN** no placeholder or re-render occurs

#### Scenario: Zoom clamped at maximum

- **WHEN** magnification is 18 and user pinches to zoom in
- **THEN** the magnification stays at 18
- **THEN** no placeholder or re-render occurs
