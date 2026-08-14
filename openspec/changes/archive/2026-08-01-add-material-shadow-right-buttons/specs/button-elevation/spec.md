## Purpose

Map overlay icon buttons on the right side of the map canvas SHALL display a Material 3 elevation shadow to create visual depth against the map surface.

## ADDED Requirements

### Requirement: Right-side buttons have Material 3 elevation
All icon buttons in the right-side overlay column SHALL have a visible Material 3 elevation shadow when in their default (resting) state.

#### Scenario: Menu button has elevation
- **WHEN** the map canvas is displayed
- **THEN** the menu button (three-dot icon) in the top-right overlay column SHALL render with a Material 3 elevation shadow

#### Scenario: Search button has elevation
- **WHEN** the map canvas is displayed
- **THEN** the search button in the top-right overlay column SHALL render with a Material 3 elevation shadow

#### Scenario: Favorites button has elevation
- **WHEN** the map canvas is displayed
- **THEN** the favorites button in the top-right overlay column SHALL render with a Material 3 elevation shadow

#### Scenario: Location options button has elevation
- **WHEN** the map canvas is displayed
- **THEN** the location options (settings gear) button in the right-side overlay column SHALL render with a Material 3 elevation shadow

#### Scenario: Zoom in button has elevation
- **WHEN** the map canvas is displayed
- **THEN** the zoom in (+) button in the right-side overlay column SHALL render with a Material 3 elevation shadow

#### Scenario: Zoom out button has elevation
- **WHEN** the map canvas is displayed
- **THEN** the zoom out (−) button in the right-side overlay column SHALL render with a Material 3 elevation shadow

### Requirement: Elevation uses Material 3 elevation system
Button elevation SHALL use `Modifier.shadow()` from Compose UI or the standard Material 3 `IconButtonDefaults.elevation` API.

#### Scenario: Elevation applied via shadow modifier
- **WHEN** inspecting the button's elevation configuration
- **THEN** it SHALL use `Modifier.shadow(elevation, shape)` or `IconButtonDefaults.filledIconButtonElevation()` / `IconButtonDefaults.filledTonalIconButtonElevation()` as appropriate for the button variant

### Requirement: Pressed state reduces elevation
Buttons SHALL reduce their elevation when pressed to provide visual feedback.

#### Scenario: Pressed state has lower elevation
- **WHEN** a user presses a right-side overlay button
- **THEN** the button's elevation SHALL decrease from its resting value to a lower pressed value

**Note**: Current implementation uses `Modifier.shadow()` which applies static elevation. Dynamic press-state elevation requires the Material 3 `IconButtonDefaults.*Elevation()` API, which is not available in the current Compose BOM (2024.12.01). Accepted as a known limitation.
