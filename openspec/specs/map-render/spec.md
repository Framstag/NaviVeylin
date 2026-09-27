# Map Render Specification

## Purpose

Render a downloaded libosmscout map onto a Compose Canvas using the JNI render() method.

## Requirements

### Requirement: Render map to Compose canvas

The system SHALL render a downloaded libosmscout map onto a Compose `Canvas` using the JNI `OSMScoutClient.render()` method, and SHALL deliver the frame in the form the display needs for overrun-window panning and follow scrolling.

- The render target SHALL be `screenWidth × canvasOverrun` by `screenHeight × canvasOverrun` pixels, where `canvasOverrun` defaults to 1.2
- Rendering SHALL execute on a dedicated render coroutine with a debounce mechanism
- The render pipeline SHALL use double buffering: back buffer for rendering, front buffer for display
- On render completion, the pixel buffer SHALL be split into 256×256 tiles and stored in the LRU tile cache
- The back buffer SHALL be atomically swapped with the front buffer on render completion
- The frame delivered to the UI SHALL be the overrun-sized front buffer, published together with the viewport (center, magnification, rotation) its pixels were rendered with; the display positions it by drawing the frame at the display offset derived from that viewport (see `canvas-overrun` — Sub-region blit for pan)
- The frame's own viewport SHALL be used for every overlay projection and for the display offset; never the target viewport of a render that has not completed
- The frame emission SHALL reuse the previously emitted bitmap only when the front-buffer content is unchanged **and** the emitted bitmap has the same dimensions as the frame being emitted; a dimension change SHALL always produce a new emission
- If the render epoch does not match the current epoch, the result SHALL be discarded
- A loading indicator SHALL be shown only on initial render (subsequent renders use the previous front buffer as placeholder)
- If `render()` returns null or throws, the system SHALL display an error state with a retry option
- The render magnification SHALL be passed to the JNI render call as a `double` scale factor (fractional values allowed, e.g. `2^15.3`); the JNI bridge SHALL map it to `osmscout::Magnification::SetMagnification(double)` and native tile/feature lookups derive the level as `floor(log2(magnification))`
- Point/description JNI entry points (`getDescription`, `getObjectBoundingBox`, lookup APIs) SHALL keep integer level parameters

#### Scenario: Initial map render on screen entry

- **WHEN** user navigates to the map screen with a downloaded map
- **THEN** the system creates back and front buffers at overrun size
- **THEN** the system calls `OSMScoutClient.render()` with overrun dimensions
- **THEN** on completion, the back buffer is swapped to front
- **THEN** the overrun frame and the viewport its pixels were rendered with are published together to the UI

#### Scenario: Render at fractional magnification

- **WHEN** the committed viewport magnification is 15.34 and a render is requested
- **THEN** `render()` receives magnification ≈ 2^15.34 as a double
- **THEN** native tile lookup snaps to level 15 tiles while the projection renders at the fractional scale
- **THEN** the rendered frame's scale matches the committed fractional magnification

#### Scenario: Render at integer magnification (unchanged behavior)

- **WHEN** the committed viewport magnification is 14 and a render is requested
- **THEN** `render()` receives magnification 16384 and the frame is identical to pre-change behavior

#### Scenario: Render error shows retry

- **WHEN** `OSMScoutClient.render()` returns null
- **THEN** the system displays an error message and a "Retry" button
- **WHEN** user taps "Retry"
- **THEN** the system calls `render()` again with the same parameters

#### Scenario: Stale render discarded

- **WHEN** a render job is queued
- **WHEN** the user pans before the render completes
- **THEN** the epoch is incremented
- **WHEN** the render completes with a stale epoch
- **THEN** the result is discarded and the front buffer is not updated

#### Scenario: Frame dimensions change between emissions

- **WHEN** the canvas size changes (rotation, fold, window resize) and a new frame is emitted
- **THEN** the emission SHALL NOT reuse the previously emitted bitmap
- **AND** the draw SHALL use the new frame's dimensions for the offset and scale computation

### Requirement: Map database selection

The system SHALL accept a map database path (the directory passed to `OSMScoutClient.openDatabase()`) and use that database for all rendering on the map screen.

- The map path SHALL be passed as a navigation argument to the map screen
- The system SHALL call `openDatabase()` before the first render and SHALL handle the case where the database cannot be opened

#### Scenario: Open valid map database

- **WHEN** the map screen receives a valid map database path
- **THEN** `OSMScoutClient.openDatabase()` is called and returns true
- **THEN** the initial render proceeds

#### Scenario: Open invalid map database

- **WHEN** the map screen receives an invalid or missing map database path
- **THEN** `OSMScoutClient.openDatabase()` returns false
- **THEN** the system displays an error message: "Could not open map database"

### Requirement: Safe bitmap lifecycle for sub-region blit

A pan SHALL NOT create bitmap views that share the front buffer's backing pixel storage, and SHALL NOT recycle anything the display may still draw.

- Panning SHALL be served by the overrun frame plus a display offset; no per-event region or crop bitmap SHALL be created for a pan
- The frame emitted to the UI SHALL be an independent copy (never sharing backing storage with the front buffer that the next render overwrites)
- The previously emitted bitmap SHALL be left to the garbage collector; it SHALL NOT be recycled while Compose may still be drawing it
- Code paths that exist only to copy a shifted sub-region for a pan SHALL be removed rather than left unreachable

#### Scenario: Panning reuses front-buffer region

- **WHEN** the user pans within the overrun margin
- **THEN** no region bitmap is created and no bitmap is recycled for the pan
- **AND** the front buffer remains valid while Compose may still be drawing the emitted frame

#### Scenario: Emitted frame does not alias the front buffer

- **WHEN** a native render completes and a frame is emitted
- **THEN** the emitted bitmap has its own pixel storage
- **AND** the next render's buffer write cannot change the pixels of the frame currently displayed

### Requirement: Basemap overlay rendering

When a basemap is loaded, the system SHALL render it as a background layer underneath regional map data; in viewports without regional map coverage, the basemap SHALL render on its own instead of a blank canvas.

#### Scenario: Regional map covers viewport

- **WHEN** the viewport is covered by an installed regional map
- **WHEN** a basemap is loaded
- **THEN** the render output draws regional map data on top of basemap data
- **AND** sea/land background comes from the basemap so regional water does not cover basemap land

#### Scenario: No regional map covers viewport

- **WHEN** the viewport is not covered by any installed regional map
- **WHEN** a basemap is loaded
- **THEN** the render output shows basemap borders, country names, and coastlines
- **AND** the render does not return an empty/blank result

#### Scenario: No basemap loaded

- **WHEN** no basemap is loaded
- **WHEN** the viewport is not covered by an installed regional map
- **THEN** the render output is unchanged from current behavior (blank outside regional coverage)

### Requirement: Stylesheets sourced from submodule at build time

The system SHALL package the stylesheets from the libosmscout submodule stylesheet directory (`app/src/main/cpp/libosmscout/stylesheets/`) into the APK as they exist at build time — the current submodule checkout state. The system SHALL NOT package a committed stylesheet snapshot. The APK asset root `stylesheets/` SHALL contain exactly the files of the submodule stylesheet directory (all `*.ost`, `*.oss`, and `include/` files).

- The build SHALL read the stylesheets from the submodule working tree, not from `app/src/main/assets/stylesheets/`
- A submodule bump SHALL change the APK stylesheet content without any additional copy step
- If the submodule stylesheet directory is missing, the build SHALL fail with an actionable error message

#### Scenario: Submodule bump changes APK content

- **WHEN** the submodule is updated to a commit whose `stylesheets/` content differs
- **THEN** the next build SHALL package the new content without any manual snapshot copy

#### Scenario: No committed snapshot involved

- **WHEN** the build runs with no `app/src/main/assets/stylesheets/` directory present
- **THEN** the APK SHALL still contain the full stylesheet set from the submodule

#### Scenario: Missing submodule fails the build

- **WHEN** the submodule stylesheet directory does not exist (fresh clone, submodule not initialized)
- **THEN** the build SHALL fail before compilation
- **THEN** the failure message SHALL instruct the user to run `git submodule update --init --recursive`

### Requirement: Stylesheet refresh on device

The system SHALL refresh the internal-storage stylesheet copy from bundled assets on every app start, so app updates deliver stylesheet changes without requiring data clearing or reinstall.

- Each bundled stylesheet SHALL be compared against the internal-storage copy (size + content hash) and copied when different
- Bundled stylesheet files missing from internal storage SHALL be copied (first launch and after file loss)
- Internal-storage stylesheet files no longer bundled SHALL be deleted (mirror semantics)
- Unchanged files SHALL NOT be rewritten (no unnecessary I/O)

#### Scenario: Existing install receives upstream changes

- **WHEN** the app is updated to an APK whose bundled stylesheets differ from the internal-storage copy
- **THEN** on next app start the changed stylesheets SHALL be copied to internal storage
- **THEN** the map SHALL render with the updated styles without any user action

#### Scenario: No-change startup is a no-op

- **WHEN** the app starts and the internal-storage stylesheets match the bundled assets
- **THEN** no stylesheet file SHALL be rewritten
- **THEN** the existing stylesheet directory path SHALL be returned unchanged

#### Scenario: Stylesheet file removed upstream

- **WHEN** a stylesheet file is removed from the bundled set after an app update
- **THEN** the corresponding internal-storage file SHALL be deleted on next app start

### Requirement: Forced overlay renders are never dropped by the blit fast-path

The overrun fast-path SHALL optimize pan and follow window shifts only: when the requested window is covered by the overrun frame it SHALL discard at most a pending non-forced render. A pending forced render (`forceFullRender=true` — route set/clear, favorites, search selection, stylesheet switch, epoch bump) SHALL survive a covering window shift and execute after the debounce, even when the camera never moved.

#### Scenario: Route set with unmoved camera renders
- **WHEN** a route calculation succeeds and `MapRenderer.setRoute` submits a forced render without a camera movement, and a subsequent non-forced render request is covered by the overrun frame
- **THEN** the pending forced render is retained
- **AND** the new route polyline is drawn on the next render pass without any user gesture

#### Scenario: Route cleared with unmoved camera renders
- **WHEN** `MapRenderer.clearRoute` submits a forced render and the next non-forced request is covered by the overrun frame
- **THEN** the forced clear render executes
- **AND** the old route polyline disappears without any user gesture

#### Scenario: Ordinary pan keeps the blit optimization
- **WHEN** the user pans within the overrun margin (no pending forced render)
- **THEN** the overrun frame still serves the display (shifted by the display offset)
- **AND** no full native render is scheduled
- **AND** no frame is emitted for the covered request

#### Scenario: Favorites stylesheet update not starved
- **WHEN** a forced render is pending and repeated covered requests arrive (e.g. follow-mode GPS ticks)
- **THEN** the pending forced render still executes after its debounce
- **AND** repeated covered requests cannot starve overlay changes indefinitely

### Requirement: New route appears without user interaction
After a reroute, the replacement route SHALL appear on the map promptly once the calculation succeeds, without requiring a pan, zoom, or rotation gesture.

#### Scenario: Reroute success refreshes the map
- **WHEN** navigation is active, a reroute calculation succeeds, and the map camera has not moved
- **THEN** the new route polyline replaces the old one on screen without any user gesture
- **AND** turn instructions and the drawn route refer to the same route

#### Scenario: Reroute failure keeps the last route
- **WHEN** a reroute calculation fails while navigating
- **THEN** the previously drawn route remains on screen (unchanged, per `reroute-route-visibility`)
- **AND** the failure is surfaced (log + snackbar, per `reroute-route-visibility`)

### Requirement: A stylesheet that cannot be loaded degrades the map instead of crashing the app

When a stylesheet cannot be loaded, the app SHALL keep running: the map area SHALL show no content for
the affected database instead of terminating the process, remaining content (other loaded databases,
overlays, route and guidance) SHALL continue to be drawn, and the failure SHALL be reported to the user
as specified for map styles. The app SHALL NOT add a per-frame cost for this guarantee: the state is
established when a stylesheet is loaded.

#### Scenario: Rejected stylesheet at startup
- **WHEN** the app starts and the stylesheet of a loaded database cannot be parsed
- **THEN** the app does not terminate
- **THEN** the map area draws no content for that database and the failure message is shown

#### Scenario: Remaining content still renders
- **WHEN** one of several loaded databases has no usable stylesheet while the others do
- **THEN** the other databases still draw their content in the same frame

#### Scenario: Recovery without a restart
- **WHEN** a valid stylesheet is loaded after a failed attempt (style switch or stylesheet refresh)
- **THEN** the map draws content again with that stylesheet without restarting the app

#### Scenario: Basemap stylesheet cannot be loaded
- **WHEN** the basemap's own stylesheet cannot be parsed while the map stylesheet loads successfully
- **THEN** the map content still renders, the basemap layer is absent for that frame, and the failure is reported
