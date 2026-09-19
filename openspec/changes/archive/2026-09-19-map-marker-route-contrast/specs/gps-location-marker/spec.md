# Spec Delta

## MODIFIED Requirements

### Requirement: Location marker rendering

The system SHALL render a location marker overlay on top of the map canvas at the user's current GPS position. The marker SHALL consist of:

- An accuracy circle: a semi-transparent filled circle centered on the estimated position, with radius proportional to the GPS horizontal accuracy in meters, projected to screen pixels at the current zoom level
- A direction indicator: a filled compass-style arrow pointing in the direction of travel when bearing is available (bearing ≥ 0), or pointing north (0°) when bearing is unavailable (bearing < 0), **corrected for current map rotation**
- The direction arrow SHALL use the unified marker style shared with the Android Auto marker: tip + tail triangles with a rounded tip, a casing ring around the blue gradient core, a dark accent rim, and a soft blurred drop shadow offset below the arrow — no hard-offset shadow
- The marker SHALL be sized in `dp` (38 dp) so it has the same visual size on every device density
- The marker's casing color **and core gradient palette** SHALL follow the resolved dark presentation:
  - light presentation: white casing ring, dark accent rim, blue gradient core (`#42A5F5` at the tip end to `#0D47A1` at the tail end)
  - dark presentation: deep blue-black casing (no bright halo), dark accent rim, and a **lighter** blue gradient core (`#BBDEFB` at the tip end to `#1E88E5` at the tail end), so the marker reads as a light object on dark land
- The dark-presentation core SHALL be lighter than the light-presentation core but SHALL NOT be white, and the dark-presentation casing SHALL remain dark, so no white ring appears around the arrow
- The marker's **geometry SHALL NOT branch** on presentation: vertices, casing scale, rim width, and shadow offset and blur are identical in both presentations. Only the palette branches
- The marker SHALL be legible and distinguishable in both presentations: on light land the dark rim and casing separate the blue core from the background; on dark land the lighter core contrasts with the dark casing and the dark background
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
- **THEN** the arrow's core uses the lighter dark-presentation blue gradient
- **AND** the core is markedly lighter than the dark-presentation casing, so the arrow is distinguishable from the dark background

#### Scenario: Arrow not too dark on dark map

- **WHEN** the map renders the dark style variant and the marker is drawn on dark land
- **THEN** at least the tip half of the arrow SHALL read as a light blue surface against the dark background
- **AND** the arrow SHALL NOT be dominated by near-black pixels

#### Scenario: No white halo on dark map

- **WHEN** the map renders the dark style variant and the marker's casing is drawn
- **THEN** the casing renders deep blue-black and the arrow silhouette shows no bright stencil-white ring
- **AND** the lighter core SHALL NOT be white, so it does not read as a white halo

#### Scenario: Marker geometry identical in both presentations

- **WHEN** the marker is drawn once in light presentation and once in dark presentation at the same size
- **THEN** both arrows occupy the same footprint and have the same outline, casing scale, rim width, and shadow offset and blur
- **AND** only the colors differ

#### Scenario: Marker size uniform across device densities

- **WHEN** the app renders the marker on devices of different densities
- **THEN** the arrow occupies 38 dp on every device, appearing at the same visual size

#### Scenario: Phone and Auto marker share one style

- **WHEN** the phone and Android Auto markers are both drawn
- **THEN** both render the same geometry and palette (rounded-tip arrow with tail, casing ring, accent rim, gradient core, blurred shadow), differing only in surface-appropriate density scaling
- **AND** both use the same size constant, so growing the marker changes both surfaces identically
