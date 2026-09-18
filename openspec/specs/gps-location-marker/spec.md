# gps-location-marker Specification

## Purpose

Shows the user's current GPS-estimated position on the map as a Compose overlay, with visual indicators for accuracy and heading direction.

## Requirements

### Requirement: Location marker rendering

The system SHALL render a location marker overlay on top of the map canvas at the user's current GPS position. The marker SHALL consist of:

- An accuracy circle: a semi-transparent filled circle centered on the estimated position, with radius proportional to the GPS horizontal accuracy in meters, projected to screen pixels at the current zoom level
- A direction indicator: a filled compass-style arrow pointing in the direction of travel when bearing is available (bearing ≥ 0), or pointing north (0°) when bearing is unavailable (bearing < 0), **corrected for current map rotation**
- The direction arrow SHALL use the unified marker style shared with the Android Auto marker: tip + tail triangles with a rounded tip, a white casing ring around the blue gradient core, a dark accent rim, and a soft blurred drop shadow offset below the arrow — no hard-offset shadow
- The marker SHALL be sized in `dp` (32 dp) so it has the same visual size on every device density
- The marker's casing color SHALL follow the resolved dark presentation: a white casing ring in light presentation, a deep blue-black casing (no bright halo) in dark presentation. The shape, rim, gradient and shadow SHALL NOT branch; the layered design SHALL remain legible and distinguishable on both light and dark map style variants
- The arrow SHALL point in the direction of travel whenever bearing is available, **regardless of the map orientation mode** (north-up or follow-direction). Orientation mode controls only the map rotation; it SHALL NOT change the arrow's travel-direction semantics. `bearing < 0` means "bearing unavailable" only — never "north-up mode active"
- The arrow SHALL be centered on the estimated position, not on the accuracy circle edge
- The marker SHALL be drawn using Compose Canvas drawing primitives, not via JNI/Cairo

#### Scenario: Accuracy circle reflects GPS accuracy

- **WHEN** GPS reports horizontal accuracy of 10 meters at zoom level 14
- **THEN** the accuracy circle SHALL have a screen radius of approximately 10 meters projected to screen pixels at zoom level 14

#### Scenario: Direction arrow shown when bearing is known

- **WHEN** GPS reports bearing ≥ 0 (e.g., bearing = 45 degrees) and map rotation is 0°
- **THEN** the marker SHALL render as a filled arrow rotated to match the bearing angle on the screen

#### Scenario: Arrow shown when bearing is unknown

- **WHEN** GPS reports bearing < 0 (bearing unavailable)
- **THEN** the marker SHALL render as a filled arrow pointing north (0°) on the map, which is `(0° - mapRotation)` on the screen

#### Scenario: Arrow points in travel direction in north-up mode

- **WHEN** the map is in north-up orientation (rotation 0°) and GPS reports bearing 180° (driving south)
- **THEN** the marker SHALL render as a filled arrow pointing south (180°) on the screen, in the direction of travel
- **AND** the arrow SHALL NOT point north

#### Scenario: Arrow points in travel direction in follow-direction mode

- **WHEN** the map is in follow-direction orientation (rotated to match the bearing) and GPS reports bearing 180°
- **THEN** the marker SHALL render as a filled arrow pointing up (0° on screen), aligned with the map rotation and the direction of travel

#### Scenario: Marker updates on each GPS fix

- **WHEN** a new GPS location is received with different lat/lon/bearing/accuracy
- **THEN** the marker SHALL re-render at the new position within 100ms

#### Scenario: Accuracy circle hidden with good GPS fix

- **WHEN** GPS accuracy is good (accuracy circle radius < 20px on screen)
- **THEN** the accuracy circle SHALL NOT be rendered
- **THEN** only the direction arrow SHALL be shown

#### Scenario: Arrow legible on daylight map

- **WHEN** the map renders the daylight style variant (light land) with the marker visible
- **THEN** the arrow's white casing ring and dark accent rim keep the blue core distinguishable from the light background

#### Scenario: Arrow legible on dark map

- **WHEN** the map renders the dark style variant with the marker visible
- **THEN** the arrow uses the deep blue-black casing color and keeps the blue core distinguishable from the dark background

#### Scenario: No white halo on dark map

- **WHEN** the map renders the dark style variant and the marker's casing is drawn
- **THEN** the casing renders deep blue-black and the arrow silhouette shows no bright stencil-white ring

#### Scenario: Marker size uniform across device densities

- **WHEN** the app renders the marker on devices of different densities
- **THEN** the arrow occupies 32 dp on every device, appearing at the same visual size

#### Scenario: Phone and Auto marker share one style

- **WHEN** the phone and Android Auto markers are both drawn
- **THEN** both render the same geometry and palette (rounded-tip arrow with tail, casing ring, accent rim, vertical gradient core, blurred shadow), differing only in surface-appropriate density scaling

### Requirement: Marker position tracks map viewport

The marker SHALL project the GPS coordinate to screen pixel coordinates using the current map viewport (center, zoom, rotation). The marker SHALL move correctly when the user pans or zooms the map.

#### Scenario: Marker moves during pan

- **WHEN** the user pans the map
- **THEN** the marker SHALL remain at the correct geographic position relative to map features

#### Scenario: Marker repositions on zoom

- **WHEN** the user zooms in or out
- **THEN** the marker SHALL re-project to the correct screen position at the new zoom level

#### Scenario: Marker hidden when off-screen

- **WHEN** the user's GPS position is outside the visible map viewport
- **THEN** the marker SHALL NOT be rendered (no off-screen indicators)

#### Scenario: Marker position is stable during map rotation

- **WHEN** the map rotates while the GPS position is visible
- **THEN** the marker SHALL stay at the same geographic location on screen
- **THEN** the direction arrow SHALL rotate to remain aligned with the direction of travel

### Requirement: Direction arrow corrects for map rotation

The system SHALL render the direction arrow at the GPS bearing angle adjusted by the current map rotation.

- When map rotation is 0°, the arrow SHALL point in the direction of travel on the screen.
- When the map is rotated by R degrees, the arrow SHALL be drawn at angle `(bearing - R)` on the screen.

#### Scenario: Rotated map keeps arrow aligned with travel

- **WHEN** GPS bearing is 90° and map rotation is 30°
- **THEN** the arrow SHALL be drawn at 60° on the screen

#### Scenario: Unknown bearing on rotated map

- **WHEN** GPS bearing is unavailable (bearing < 0) and map rotation is 45°
- **THEN** the arrow SHALL be drawn at -45° on the screen so it points north on the map

### Requirement: Marker position stable during map rotation

The system SHALL keep the GPS marker at the same geographic screen position while the map rotates around the viewport center.

- The marker projection SHALL use the same rotation value that the native renderer uses.
- The marker SHALL be reprojected on every rotation change.

#### Scenario: Map rotates around marker

- **WHEN** the map rotates and the GPS position is near the viewport center
- **THEN** the marker SHALL remain over the same map feature
- **THEN** the accuracy circle SHALL remain centered on the same map feature

### Requirement: Marker rendered on dedicated overlay target

The system SHALL render the GPS location marker on an overlay layer separate from the map render surface. The marker SHALL NOT be written into cached tiles, the back buffer, the front buffer, or any bitmap that is reused across frames. The map render output SHALL contain only map content.

- The marker overlay SHALL redraw on top of the displayed map whenever a frame is emitted, projecting the marker state that rode with that frame (render-time snapshot)
- In follow mode between fixes, the marker SHALL be drawn at the predicted position (extrapolated from the fix that rode with the frame, the speed, and the heading) so the marker glides with the blitted map
- The marker SHALL NOT be drawn at the live GPS fix when the displayed frame was rendered for an earlier fix — doing so would place the marker ahead of the road on screen
- No marker pixels SHALL ever enter cached tiles, the back buffer, or the front buffer

#### Scenario: Marker stays on road during frame lag

- **WHEN** a new GPS fix arrives while the displayed frame was rendered for an earlier fix
- **THEN** the marker SHALL be drawn at the predicted position extrapolated from the fix that rode with the displayed frame
- **THEN** the marker SHALL remain on the road/track of the displayed map

#### Scenario: No ghost marker after cached tile reuse

- **WHEN** the user pans and the pan is served from cached tiles after a marker move
- **THEN** the displayed map SHALL contain no marker pixels from a previous marker position

#### Scenario: Marker hidden leaves no residue

- **WHEN** the marker becomes hidden (e.g., GPS lost) and the map is then panned or zoomed
- **THEN** no marker pixels SHALL remain in the displayed map or in cached tiles

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

### Requirement: Marker glides between fixes

The system SHALL update the GPS marker position every display frame in follow mode while the vehicle is moving, at the predicted position, instead of only on each GPS fix.

#### Scenario: Marker moves smoothly between fixes

- **WHEN** the vehicle moves at constant speed and the display loop runs at 60 fps between two fixes
- **THEN** the marker SHALL move incrementally each frame along the predicted path
- **AND** the marker SHALL NOT jump from fix to fix

#### Scenario: Marker stationary when vehicle stops

- **WHEN** the vehicle speed drops below the movement threshold
- **THEN** the marker SHALL remain at the last position
- **AND** the marker SHALL NOT drift
