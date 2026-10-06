# Spec Delta

## MODIFIED Requirements

### Requirement: Map re-renders on viewport change

The system SHALL update the displayed map when the viewport center, zoom, or rotation changes. A viewport center change within the overrun region SHALL be served by blitting the overrun buffer; a full re-render SHALL occur only when the center exits the overrun region or when zoom or rotation changes. This SHALL hold for every vehicle anchor preset: the blit offset SHALL be derived from the displayed vehicle position, never from the frame center, because a frame center is not a point of the rendered bitmap and charging the offset with the anchor displacement pushes it outside the overrun margin for every preset away from the surface center. A fault that skipped one frame SHALL NOT stop later viewport changes from being displayed: the next viewport change SHALL be served (blit or full render) without the driver re-entering the screen.

#### Scenario: Re-render on pan

- **WHEN** the user pans the map
- **THEN** the map updates at the new center position (blit within overrun, full render beyond it)

#### Scenario: Re-render on zoom

- **WHEN** the user zooms in or out
- **THEN** the map re-renders at the new magnification level

#### Scenario: Re-render on rotation

- **WHEN** the map rotation changes
- **THEN** the map re-renders at the new angle

#### Scenario: Follow-mode move served by blit

- **WHEN** the vehicle moves and the new viewport center stays within the overrun region
- **THEN** the map SHALL be updated by blitting the overrun buffer
- **AND** no full native render SHALL be initiated

#### Scenario: Follow center change served by blit with a non-center anchor

- **WHEN** the follow anchor resolves away from the surface center (e.g. a bottom-row preset with the host bottom band clamped)
- **AND** the frame center moves by a delta that stays within the overrun region
- **THEN** the surface SHALL be updated by blitting the overrun buffer
- **AND** no full native render SHALL be initiated for that center change
- **AND** the vehicle content SHALL hold the resolved anchor fraction

#### Scenario: Viewport change after a skipped frame

- **WHEN** a frame was skipped because its work faulted
- **AND** the viewport center, magnification or rotation changes afterwards
- **THEN** that change is displayed by the same rule as before the fault (blit within the overrun region, full render beyond it)

### Requirement: A stopped renderer holds no surface or frame buffer

A car map renderer whose screen is not started SHALL hold no reference to the car surface and no
rendered frame buffer: it SHALL release both when its screen stops, and it SHALL re-acquire the
session's surface and render a full frame before its first frame after a start. Releasing the surface
reference SHALL NOT release the surface itself, which the session owns. A renderer re-created after
sustained faults SHALL acquire the surface under this same rule and SHALL NOT inherit the previous
instance's frame buffer.

#### Scenario: Screen stops

- **WHEN** a car screen with a renderer stops (backgrounded, or covered by a pushed screen)
- **THEN** its renderer holds no car surface reference and no overrun frame buffer
- **AND** a later frame of that renderer cannot lock or draw a surface while the screen is stopped

#### Scenario: Screen starts again with the session's surface held

- **WHEN** a stopped car screen starts again while the session still holds its surface
- **THEN** the renderer re-acquires that surface and renders a full frame before its first frame is drawn
- **AND** the first frame after the start is not blitted from a buffer that predates the stop

#### Scenario: Stopped screen after a surface transition

- **WHEN** the host delivers a new surface while a screen with a renderer is stopped
- **THEN** the stopped renderer does not hold the destroyed surface, and it uses the current one after its next start

#### Scenario: Stopped renderer reports no failure

- **WHEN** a screen with a renderer stops while its renderer had reported a surface failure
- **THEN** the stop clears that failure state, so the next start is not treated as a failed surface and no host template refresh is requested for it

#### Scenario: Re-created renderer starts from the session's current surface

- **WHEN** the renderer is re-created while the session holds a surface
- **THEN** the new renderer acquires that surface and renders a full frame before its first frame is drawn
- **AND** it holds no overrun buffer and no blit-eligible frame from the previous instance (the state it draws is re-applied, see *A re-created renderer restores the displayed map state*)

## ADDED Requirements

### Requirement: A re-created renderer restores the displayed map state

The system SHALL carry the car map's displayed state across a renderer re-creation, so a recovery is
invisible to the driver apart from the frames that were skipped: the current viewport (center,
magnification including its fractional part, rotation), follow mode and follow anchor, host
presentation (day/night) and pane geometry (top/bottom insets, RTL), the GPS marker with its last fix
and fix time, favorites, the drawn route, the destination marker and the overlay drawer SHALL be
applied to the new renderer before its first frame is displayed. The map SHALL NOT fall back to its
initial center, zoom or follow mode after a re-creation, and the first frame of the new renderer SHALL
NOT be blitted from a frame the previous instance rendered.

#### Scenario: Viewport and follow mode survive a re-creation

- **WHEN** the car map is following the vehicle at a committed magnification and the renderer is re-created
- **THEN** the first frame of the new renderer shows the displayed center and the fractional magnification the map had before the fault
- **AND** follow mode is still active, so the next fix re-anchors as before

#### Scenario: Presentation and pane geometry survive a re-creation

- **WHEN** the host reports night mode and the follow anchor sits in a bottom-row preset with inset bands clamped
- **AND** the renderer is re-created
- **THEN** the new renderer draws the dark style variant and keeps the resolved anchor, the pane insets and the RTL direction

#### Scenario: Overlays survive a re-creation

- **WHEN** a GPS fix, favorites, a route, a destination marker and an overlay drawer are set
- **AND** the renderer is re-created before the next fix arrives
- **THEN** the new renderer draws the marker at its last fix, keeps the favorites and route, and still invokes the overlay drawer

#### Scenario: Recovery does not jump to the initial viewport

- **WHEN** the renderer is re-created after sustained faults
- **THEN** no frame is displayed at the initial latitude/longitude/zoom the renderer was constructed with
- **AND** no frame is displayed that blits the previous instance's overrun buffer
