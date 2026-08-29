# map-render

## MODIFIED Requirements

### Requirement: Render map to Compose canvas

The system SHALL render a downloaded libosmscout map onto a Compose `Canvas` using the JNI `OSMScoutClient.render()` method.

- The render target SHALL be `screenWidth × canvasOverrun` by `screenHeight × canvasOverrun` pixels, where `canvasOverrun` defaults to 1.2
- Rendering SHALL execute on a dedicated render coroutine with a debounce mechanism
- The render pipeline SHALL use double buffering: back buffer for rendering, front buffer for display
- On render completion, the pixel buffer SHALL be split into 256×256 tiles and stored in the LRU tile cache
- The back buffer SHALL be atomically swapped with the front buffer on render completion
- The front buffer SHALL be blitted to the Compose Canvas, extracting the visible screen-sized sub-region from the center
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
- **THEN** the visible screen-sized region is extracted and displayed on the Compose Canvas

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