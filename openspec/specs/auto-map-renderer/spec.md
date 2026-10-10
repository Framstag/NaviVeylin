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

### Requirement: Favorites markers on car map
The system SHALL display favorite location markers on the car map, reusing `FavoriteRepository.favorites` data.

#### Scenario: Favorites shown on map
- **WHEN** the car map is displayed and favorites exist
- **THEN** favorite location markers appear on the map

#### Scenario: Favorites update on change
- **WHEN** a favorite is added, removed, or modified on the phone
- **THEN** the car map markers update to reflect the change

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

The system SHALL initialize the car map renderer off the car-app main thread: native client access, the initial-viewport resolution (saved viewport JSON or first installed map database bounding box), and any native database bounding-box queries SHALL run on a background dispatcher, never on the main/host-callback thread. When the map screen starts while the native client is still building, the main thread SHALL NOT block on native client construction or database queries. Resolving a car provider (client, favorites, location, settings) SHALL NOT build the native client on the host thread: a screen constructor or a host callback SHALL only retain what it received, and a provider that needs the native client SHALL resolve it on a background dispatcher.

#### Scenario: Template delivered while the native client is still building

- **WHEN** the car map screen is constructed while the session's background warmup is still building or opening the native client
- **THEN** the screen constructor returns without touching the native client on the main thread and the map template is delivered without waiting for renderer initialization

#### Scenario: Surface arrives before the renderer is ready

- **WHEN** the host delivers a map surface before the renderer initialization completes
- **THEN** the surface dimensions and DPI are retained and applied to the renderer when it becomes ready, so the first rendered frame uses the delivered surface

#### Scenario: Provider resolution does not build the client

- **WHEN** a car screen resolves its client, favorites, location or settings provider before the native client exists
- **THEN** the native client is not built on the calling (host) thread
- **AND** the client is built on a background dispatcher

#### Scenario: Surface delivery never builds the client

- **WHEN** the host delivers a surface while the native client is still being built
- **THEN** the surface callback returns without building or touching the native client

### Requirement: No map state lost during renderer initialization

The system SHALL preserve map state that arrives between screen start and renderer readiness: surface delivery, host day/night state, follow-mode re-centering, north-up/angle changes, GPS position updates, and settings-driven viewport changes that occur before the renderer is ready SHALL be applied to the renderer once it becomes available, with the most recent value of each state winning. A renderer that was constructed but not yet handed to the screen SHALL be shut down when the screen is destroyed during initialization, so no background render or display loop outlives its screen.

#### Scenario: Dark-mode push during initialization

- **WHEN** the host day/night state changes while the renderer is still initializing
- **THEN** the renderer applies the current dark presentation on readiness and re-renders with the correct style variant

#### Scenario: Follow re-center during initialization

- **WHEN** the map screen starts in follow mode and a GPS fix arrives before the renderer is ready
- **THEN** the renderer centers on the latest fix and shows the GPS marker at the current position once ready, without an intermediate stale viewport

#### Scenario: Renderer still initializing when the screen stops

- **WHEN** the map screen is stopped or destroyed while renderer initialization is still in flight
- **THEN** the pending initialization is cancelled and no renderer work continues after the screen is destroyed

#### Scenario: Renderer constructed while the screen is being destroyed

- **WHEN** a renderer instance is created and the screen is destroyed before that instance is handed to it
- **THEN** the instance is shut down, and none of its background work (render loop, display-extrapolation loop, zoom-walk loop) stays alive

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

### Requirement: Marker drawing allocates no per-frame objects

The car map renderer's marker drawing (the GPS position marker and the destination marker) SHALL
create its drawing objects — path outlines, paints, shaders and blur filters — on first use and reuse
them across frames. They SHALL be rebuilt only when an input that defines their geometry or palette
changes: the surface bounds, the surface density, or the day/night presentation. The rendered marker
SHALL keep the geometry, size and palette contract of the marker requirements above.

#### Scenario: Repeated frames allocate nothing

- **WHEN** the GPS marker is drawn on N consecutive frames with unchanged surface bounds, density and presentation
- **THEN** no new drawing object SHALL be created for those frames
- **AND** the marker SHALL be drawn at the same position, size and palette as before the change

#### Scenario: Presentation change rebuilds the palette

- **WHEN** the host day/night state changes while the car map draws markers
- **THEN** the marker's palette objects SHALL be rebuilt for the new presentation
- **AND** the next drawn frame SHALL use the dark-presentation or daylight palette for the new state

#### Scenario: Surface bounds or density change rebuilds the geometry

- **WHEN** the car surface is resized, or recreated at a different display density
- **THEN** the geometry-dependent drawing objects SHALL be rebuilt for the new bounds and density
- **AND** the marker SHALL keep its density-aware size on the new surface

#### Scenario: Destination marker follows the same rule

- **WHEN** the destination marker is drawn on consecutive frames with unchanged inputs
- **THEN** no new drawing object SHALL be created for those frames
- **AND** the pin and its label SHALL be drawn as before

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

### Requirement: Car renders carry the surface DPI

The car map renderer SHALL pass the DPI delivered with the car surface on every native render request it issues, and surface delivery SHALL NOT mutate client-wide render state.

- The DPI SHALL be the value delivered with the surface by the Car App Library surface callback (`SurfaceCallback`), the same value the car renderer uses for its marker, anchor and gesture projection
- A change of the delivered surface DPI SHALL apply to the car renderer and its subsequent frames only
- The surface callback SHALL remain state-retaining: it SHALL NOT reach the native client, and applying the DPI SHALL NOT require a client call from the host thread

#### Scenario: First car frame after surface delivery

- **WHEN** the host delivers a surface with DPI D and the car renderer becomes ready
- **THEN** the first frame is projected at D
- **THEN** the vehicle marker sits on the map content it was projected against

#### Scenario: Surface DPI change

- **WHEN** the host delivers a car surface with a different DPI
- **THEN** subsequently rendered car frames use the new value
- **THEN** no other surface's frames are affected

#### Scenario: Host callback does not touch the native client

- **WHEN** the surface callback retains the delivered DPI
- **THEN** no native client call is made from the host thread to apply it
