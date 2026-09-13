# auto-map-renderer Specification (delta)

## MODIFIED Requirements

### Requirement: GPS position marker on car map

The system SHALL display the current GPS position as a marker on the car map, reusing the existing `LocationService.location` data. In follow mode between fixes, the marker SHALL be drawn at the predicted position (extrapolated from the last fix, speed, and heading) so the marker glides with the blitted map. The marker SHALL use the unified marker style shared with the phone marker: a rounded-tip direction arrow (tip + tail triangles) with a white casing ring, a dark accent rim, a vertical blue gradient core (light from above), and a soft blurred drop shadow — no hard-offset shadow. The size SHALL be density-aware (`32 × surface density` dp) so the marker is the same visual size as the phone marker. Only the casing color SHALL branch on the host night state — white casing in day, deep blue-black casing in dark — so no bright halo appears against dark land; it SHALL remain legible on both daylight and dark map variants.

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
