## MODIFIED Requirements

### Requirement: Marker projects against displayed bitmap viewport

The system SHALL project every overlay drawn on the map surface (the GPS marker and the destination pin) to screen pixels using the viewport of the bitmap currently displayed, not the target viewport of a render that has not completed.

- The overlay projection SHALL use the displayed bitmap's center, magnification, rotation, and DPI
- The overlay SHALL be shifted by the same blit offset the displayed frame was drawn with
- The frame's blit offset SHALL be published together with the frame it describes, so a frame completing concurrently in another thread can never make an overlay use a different frame's offset
- The marker SHALL be reprojected on every displayed frame
- A viewport write whose frame has not been committed yet (a follow re-anchor on a GPS fix, a heading rotation, a zoom change) SHALL NOT move any overlay before that frame is on the surface
- In follow mode the marker SHALL be projected against the anchor center of the displayed (predicted) position: the frame is rendered anchor-centered on its own position and then blitted by the prediction drift, so only this projection places the marker on the map content at the anchor. Projecting against the anchor-centered frame's own center would leave the marker ahead of the content by the blit offset
- The marker and the map content SHALL share one projection and one offset: the marker SHALL NOT be clamped, shifted or held back independently of the map content, so marker and road cannot drift apart while the displayed frame lags behind the prediction
- Both follow-mode implementations SHALL satisfy this: the phone projects against the committed render viewport, and the Android Auto renderer SHALL publish the displayed frame's own center, magnification and rotation and project its overlays against that published frame

#### Scenario: Marker stays anchored during pan

- **WHEN** the user pans and the target viewport leads the rendered frame
- **THEN** the marker SHALL remain at the same screen-relative position over the same map features as the displayed bitmap

#### Scenario: Marker anchored during rotation placeholder

- **WHEN** the map rotates and a placeholder frame is displayed before the final render completes
- **THEN** the marker SHALL reproject against the displayed placeholder viewport each frame
- **THEN** the marker SHALL land on the correct geographic point in the final frame

#### Scenario: Marker stays on its content while a follow re-render is pending

- **WHEN** a GPS fix re-anchors the follow frame and the frame carrying the new anchor has not been rendered yet
- **THEN** the marker SHALL be projected against the frame still on the surface
- **AND** the marker SHALL NOT move relative to the map content it rides
- **AND** the marker SHALL sit at the anchor fraction once the re-anchored frame is committed

#### Scenario: Frame stays consistent when the target moves during a render

- **WHEN** the render target changes while a native render is in flight (a fix re-anchor, a clamp re-anchor, an auto-zoom commit)
- **THEN** the frame that becomes the displayed frame SHALL be described by the center, magnification and rotation the pixels were rendered with
- **AND** every overlay SHALL project against those parameters for the whole inter-commit window

#### Scenario: Destination pin shares the displayed frame

- **WHEN** the destination pin and the vehicle marker are drawn on the same displayed frame
- **THEN** both SHALL use the same displayed-frame center, magnification and rotation
- **AND** both SHALL move together with the map content of that frame

#### Scenario: Marker sits on the anchor of the displayed frame

- **WHEN** follow mode is active with a non-center anchor and the displayed frame was rendered for the current vehicle position
- **THEN** the marker SHALL be drawn at the anchor screen fraction of that frame
- **AND** the map content under the marker SHALL be the vehicle's road position

#### Scenario: Marker at the anchor while the frame lags the prediction

- **WHEN** the displayed position has moved ahead of the position the displayed frame was rendered for
- **THEN** the marker SHALL be drawn at the same screen position as the map content of the displayed position (the anchor fraction)
- **AND** the marker SHALL NOT be displaced from the content by the blit offset
