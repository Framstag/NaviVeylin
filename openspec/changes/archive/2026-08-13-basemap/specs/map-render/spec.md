# Map Render

## ADDED Requirements

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
