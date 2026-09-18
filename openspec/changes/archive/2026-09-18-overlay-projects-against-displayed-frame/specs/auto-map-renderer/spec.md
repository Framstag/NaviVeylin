# Spec Delta: auto-map-renderer

## MODIFIED Requirements

### Requirement: GPS position marker on car map

The system SHALL display the current GPS position as a marker on the car map, reusing the existing `LocationService.location` data. In follow mode between fixes, the marker SHALL be drawn at the predicted position (extrapolated from the last fix, speed, and heading) so the marker glides with the blitted map. The marker SHALL be projected against the displayed frame's own center, magnification and rotation (not the pending render target) and shifted by that frame's blit offset, so it stays on the map content it rides while a re-render is in flight. The marker SHALL use the unified marker style shared with the phone marker: a rounded-tip direction arrow (tip + tail triangles) with a white casing ring, a dark accent rim, a vertical blue gradient core (light from above), and a soft blurred drop shadow — no hard-offset shadow. The size SHALL be density-aware (`32 × surface density` dp) so the marker is the same visual size as the phone marker. Only the casing color SHALL branch on the host night state — white casing in day, deep blue-black casing in dark — so no bright halo appears against dark land; it SHALL remain legible on both daylight and dark map variants.

#### Scenario: GPS marker shown

- **WHEN** GPS position is available
- **THEN** a position marker appears on the car map at the current coordinates

#### Scenario: GPS marker updates

- **WHEN** the vehicle moves more than 5 meters
- **THEN** the GPS marker position updates on the car map

#### Scenario: GPS marker glides between fixes

- **WHEN** the vehicle moves at constant speed between two fixes in follow mode
- **THEN** the marker SHALL move incrementally each display frame along the predicted path
- **AND** the marker SHALL NOT jump from fix to fix

#### Scenario: Marker does not lead the content while a fix re-anchor renders

- **WHEN** a GPS fix re-anchors the follow frame while a blitted frame is still on the surface
- **THEN** the marker SHALL be drawn against the displayed frame's center, magnification and rotation
- **AND** the marker SHALL stay on its road/track pixel of the displayed map
- **AND** the marker SHALL come to rest at the configured anchor fraction when the re-anchored frame is committed

#### Scenario: Marker legible on daylight map

- **WHEN** the car map renders the daylight style variant with the marker visible
- **THEN** the arrow's white casing ring and dark rim keep the blue core distinguishable from the light land background

#### Scenario: No white halo in dark host mode

- **WHEN** the host reports night state and the car map renders the dark style variant
- **THEN** the marker's casing renders deep blue-black and the arrow silhouette shows no bright white halo

#### Scenario: Marker size uniform with phone

- **WHEN** the car marker and the phone marker are both visible
- **THEN** both arrows render at 32 dp (density-aware), the same visual size on both surfaces

#### Scenario: Marker style unified with phone

- **WHEN** the Android Auto marker is drawn
- **THEN** it renders the same geometry and palette as the phone marker (rounded-tip arrow with tail, casing, rim, gradient core, blurred shadow)

### Requirement: Map re-renders on viewport change

The system SHALL update the displayed map when the viewport center, zoom, or rotation changes. A viewport center change within the overrun region SHALL be served by blitting the overrun buffer; a full re-render SHALL occur only when the center exits the overrun region or when zoom or rotation changes. This SHALL hold for every vehicle anchor preset: the blit offset SHALL be derived from the displayed vehicle position, never from the frame center, because a frame center is not a point of the rendered bitmap and charging the offset with the anchor displacement pushes it outside the overrun margin for every preset away from the surface center.

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
