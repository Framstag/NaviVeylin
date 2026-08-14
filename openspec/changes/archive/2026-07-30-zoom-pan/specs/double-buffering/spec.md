## Purpose

Eliminate visual flicker and stale-map artifacts by rendering to an offscreen buffer and atomically swapping to the display buffer only when a complete render is available.

## ADDED Requirements

### Requirement: Double-buffered render pipeline

The system SHALL maintain two pixel buffers: a back buffer (render target) and a front buffer (display target).

- All native `OSMScoutClient.render()` calls SHALL write into the back buffer
- On render completion, the back buffer SHALL be atomically swapped with the front buffer
- The front buffer SHALL be the only buffer read for display on the Compose Canvas
- Buffer swap SHALL be protected by a lock to prevent concurrent read/write
- If the canvas size changes, both buffers SHALL be reallocated to match

#### Scenario: Initial render fills back buffer

- **WHEN** the first render completes
- **THEN** the back buffer contains the rendered pixels
- **THEN** the back buffer is swapped to front
- **THEN** the Canvas displays the front buffer content

#### Scenario: Consecutive renders do not flicker

- **WHEN** a new render starts while a previous render is still in progress
- **THEN** the front buffer continues to display the previous render
- **THEN** the new render writes to the back buffer
- **WHEN** the new render completes
- **THEN** the buffers are swapped
- **THEN** the Canvas displays the new content without a blank frame

### Requirement: Stale render detection

The system SHALL discard render results that are no longer relevant due to viewport changes during rendering.

- Each render job SHALL carry an epoch identifier
- On completion, the epoch SHALL be compared against the current epoch
- If the epochs differ, the render result SHALL be discarded (not swapped to front)
- The epoch SHALL be incremented on every viewport change (pan, zoom, rotation, overlay update)

#### Scenario: Stale render discarded

- **WHEN** a render job is queued at epoch N
- **WHEN** the user pans the map before the render completes (epoch becomes N+1)
- **WHEN** the render job completes
- **THEN** the result is discarded because epoch N != current epoch N+1
- **THEN** the front buffer is not updated
