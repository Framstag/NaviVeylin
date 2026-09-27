# Delta: map-render

## MODIFIED Requirements

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
