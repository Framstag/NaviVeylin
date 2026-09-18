# Android Auto Map Renderer (auto-map-renderer)

## Purpose

Render libosmscout map tiles to the Android Auto display using `MapTemplate` with a custom `Surface` renderer, providing visual map context alongside turn-by-turn navigation.

## Requirements

### Requirement: Map rendered on car display
The system SHALL render a libosmscout map on the Android Auto car display using `MapTemplate` with a custom `Surface` renderer backed by `OSMScoutClient.renderWithRouteAndPois()`.

#### Scenario: Map shown when not navigating
- **WHEN** Android Auto is connected and no navigation is active
- **THEN** the car screen shows a browsable map centered on the current GPS position (or last known position)

#### Scenario: Map shown during navigation
- **WHEN** navigation is active on Android Auto
- **THEN** the car screen shows the `NavigationTemplate` with turn-by-turn guidance (existing behavior unchanged)

### Requirement: Map renders at correct center and zoom
The system SHALL render the map at the correct geographic center, zoom level, and rotation angle matching the current viewport state.

#### Scenario: Map renders at GPS position
- **WHEN** the car map is displayed and GPS position is available
- **THEN** the map centers on the current GPS latitude/longitude at a default zoom level

#### Scenario: Map rotation follows navigation heading
- **WHEN** navigation is active and the car map is visible
- **THEN** the map rotates to match the driving direction (north-up mode available as toggle)

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

### Requirement: Favorites markers on car map
The system SHALL display favorite location markers on the car map, reusing `FavoriteRepository.favorites` data.

#### Scenario: Favorites shown on map
- **WHEN** the car map is displayed and favorites exist
- **THEN** favorite location markers appear on the map

#### Scenario: Favorites update on change
- **WHEN** a favorite is added, removed, or modified on the phone
- **THEN** the car map markers update to reflect the change

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

### Requirement: Map follows host day/night

The system SHALL render the car map surface using the host's day/night state: the dark style sheet variant (the `daylight` flag unset) when the host reports night mode, and the daylight variant when the host reports day mode. The host's state SHALL be read from the car environment (`CarContext.isDarkMode()`), not from the phone's system night mode. A change in the host's day/night state while the car app is running SHALL re-render the visible map without user interaction and SHALL NOT show cached tiles or patterns from the previous variant.

#### Scenario: Map renders dark at night

- **WHEN** the car app starts while the host reports night mode
- **THEN** the map surface is rendered with the dark style sheet variant

#### Scenario: Map renders light during the day

- **WHEN** the car app starts while the host reports day mode
- **THEN** the map surface is rendered with the daylight style sheet variant

#### Scenario: Map switches live on host change

- **WHEN** the host changes its day/night state while the car app is running (e.g. entering a tunnel)
- **THEN** the map surface re-renders with the new variant without user interaction

#### Scenario: Map darkens from the start

- **WHEN** a map database is opened while the host reports night mode
- **THEN** the first render uses the dark style sheet variant

#### Scenario: Phone night mode does not drive the car map

- **WHEN** the phone's system night mode differs from the host's day/night state
- **THEN** the car map surface follows the host's state, not the phone's

### Requirement: Renderer initialization off the car-app main thread

The system SHALL initialize the car map renderer off the car-app main thread: native client access, the initial-viewport resolution (saved viewport JSON or first installed map database bounding box), and any native database bounding-box queries SHALL run on a background dispatcher, never on the main/host-callback thread. When the map screen starts while the native client is still building, the main thread SHALL NOT block on native client construction or database queries.

#### Scenario: Template delivered while the native client is still building

- **WHEN** the car map screen is constructed while the session's background warmup is still building or opening the native client
- **THEN** the screen constructor returns without touching the native client on the main thread and the map template is delivered without waiting for renderer initialization

#### Scenario: Surface arrives before the renderer is ready

- **WHEN** the host delivers a map surface before the renderer initialization completes
- **THEN** the surface dimensions and DPI are retained and applied to the renderer when it becomes ready, so the first rendered frame uses the delivered surface

### Requirement: No map state lost during renderer initialization

The system SHALL preserve map state that arrives between screen start and renderer readiness: surface delivery, host day/night state, follow-mode re-centering, north-up/angle changes, GPS position updates, and settings-driven viewport changes that occur before the renderer is ready SHALL be applied to the renderer once it becomes available, with the most recent value of each state winning.

#### Scenario: Dark-mode push during initialization

- **WHEN** the host day/night state changes while the renderer is still initializing
- **THEN** the renderer applies the current dark presentation on readiness and re-renders with the correct style variant

#### Scenario: Follow re-center during initialization

- **WHEN** the map screen starts in follow mode and a GPS fix arrives before the renderer is ready
- **THEN** the renderer centers on the latest fix and shows the GPS marker at the current position once ready, without an intermediate stale viewport

#### Scenario: Renderer still initializing when the screen stops

- **WHEN** the map screen is stopped or destroyed while renderer initialization is still in flight
- **THEN** the pending initialization is cancelled and no renderer work continues after the screen is destroyed
