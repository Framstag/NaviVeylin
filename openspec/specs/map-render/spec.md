# Map Render Specification

## Purpose

Render a downloaded libosmscout map onto a Compose Canvas using the JNI render() method.

## Requirements

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

#### Scenario: Initial map render on screen entry

- **WHEN** user navigates to the map screen with a downloaded map
- **THEN** the system creates back and front buffers at overrun size
- **THEN** the system calls `OSMScoutClient.render()` with overrun dimensions
- **THEN** on completion, the back buffer is swapped to front
- **THEN** the visible screen-sized region is extracted and displayed on the Compose Canvas

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

When a sub-region of the front buffer is copied to `_frontBufferFlow`, the system SHALL NOT recycle a bitmap that shares the front buffer's backing pixel storage.

#### Scenario: Panning reuses front-buffer region

- **WHEN** `trySubRegionBlit` or `blitSubRegion` creates a region view from the front buffer
- **THEN** the region is copied to an independent bitmap before any `recycle()` call
- **AND** the front buffer remains valid while Compose may still be drawing it

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
