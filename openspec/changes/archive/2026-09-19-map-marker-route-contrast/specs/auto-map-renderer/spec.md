# Spec Delta

## MODIFIED Requirements

### Requirement: GPS position marker on car map

The system SHALL display the current GPS position as a marker on the car map, reusing the existing `LocationService.location` data. In follow mode between fixes, the marker SHALL be drawn at the predicted position (extrapolated from the last fix, speed, and heading) so the marker glides with the blitted map. The marker SHALL be projected against the displayed frame's own center, magnification and rotation (not the pending render target) and shifted by that frame's blit offset, so it stays on the map content it rides while a re-render is in flight. The marker SHALL use the unified marker style shared with the phone marker: a rounded-tip direction arrow (tip + tail triangles) with a casing ring, a dark accent rim, a vertical blue gradient core (light from above), and a soft blurred drop shadow — no hard-offset shadow. The size SHALL be density-aware (`38 × surface density` dp) so the marker is the same visual size as the phone marker. The palette SHALL branch on the host night state: in day the casing is white with the standard blue gradient core (`#42A5F5` to `#0D47A1`); in night the casing is deep blue-black (no bright halo against dark land) and the core gradient is lighter (`#BBDEFB` to `#1E88E5`) so the marker reads as a light object on dark land. The night core SHALL NOT be white, and the geometry SHALL NOT branch on the night state — only the palette does.

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

#### Scenario: Marker legible on dark map

- **WHEN** the host reports night state and the car map renders the dark style variant
- **THEN** the arrow's core uses the lighter dark-presentation blue gradient
- **AND** the core is markedly lighter than the casing, so the marker is distinguishable from the dark land background

#### Scenario: No white halo in dark host mode

- **WHEN** the host reports night state and the car map renders the dark style variant
- **THEN** the marker's casing renders deep blue-black and the arrow silhouette shows no bright white halo
- **AND** the lighter night core SHALL NOT be white

#### Scenario: Marker size uniform with phone

- **WHEN** the car marker and the phone marker are both visible
- **THEN** both arrows render at 38 dp (density-aware), the same visual size on both surfaces

#### Scenario: Marker style unified with phone

- **WHEN** the Android Auto marker is drawn
- **THEN** it renders the same geometry and palette as the phone marker (rounded-tip arrow with tail, casing, rim, gradient core, blurred shadow)
