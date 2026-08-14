## ADDED Requirements

### Requirement: Render map to Compose canvas

The system SHALL render a downloaded libosmscout map onto a Compose `Canvas` using the JNI `OSMScoutClient.render()` method.

- The render target SHALL be 864×1152 pixels (3:4 portrait aspect), scaled to fill the screen via `drawImage` with `dstSize`
- Rendering SHALL execute on a background thread (`Dispatchers.Default`) to avoid blocking the UI thread
- The resulting ARGB pixel buffer SHALL be converted to `android.graphics.Bitmap` with `Config.ARGB_8888`, then wrapped as Compose `ImageBitmap`
- The map SHALL re-render when the viewport center or zoom level changes
- A loading indicator SHALL be shown while rendering is in progress
- If `render()` returns null or throws, the system SHALL display an error state with a retry option

#### Scenario: Initial map render on screen entry

- **WHEN** user navigates to the map screen with a downloaded map
- **THEN** the system calls `OSMScoutClient.render()` with the stored viewport parameters
- **THEN** the rendered map bitmap is displayed on the Compose Canvas

#### Scenario: Render error shows retry

- **WHEN** `OSMScoutClient.render()` returns null
- **THEN** the system displays an error message and a "Retry" button
- **WHEN** user taps "Retry"
- **THEN** the system calls `render()` again with the same parameters

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
