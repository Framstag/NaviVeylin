# Spec Delta: gps-location-marker

## MODIFIED Requirements

### Requirement: Marker projects against displayed bitmap viewport

The system SHALL project every overlay drawn on the map surface (the GPS marker and the destination pin) to screen pixels using the viewport of the bitmap currently displayed, not the target viewport of a render that has not completed.

- The overlay projection SHALL use the displayed bitmap's center, magnification, rotation, and DPI
- The overlay SHALL be shifted by the same blit offset the displayed frame was drawn with
- The frame's blit offset SHALL be published together with the frame it describes, so a frame completing concurrently in another thread can never make an overlay use a different frame's offset
- The marker SHALL be reprojected on every displayed frame
- A viewport write whose frame has not been committed yet (a follow re-anchor on a GPS fix, a heading rotation, a zoom change) SHALL NOT move any overlay before that frame is on the surface
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
