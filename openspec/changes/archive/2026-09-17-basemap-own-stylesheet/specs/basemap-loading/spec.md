# Basemap Loading

## ADDED Requirements

### Requirement: Basemap renders with its own stylesheet

The basemap SHALL be rendered with a dedicated stylesheet (`basemap-render.oss`) that references only types present in the basemap database, instead of the user-selected main map style. Loading the basemap stylesheet SHALL NOT produce unknown-type warnings. The basemap stylesheet SHALL follow the same style flags as the main stylesheet (e.g. the `daylight` flag for dark mode). Switching the main map style SHALL NOT change the basemap's stylesheet.

#### Scenario: Basemap loads without unknown-type warnings

- **WHEN** the app loads the basemap database with its stylesheet
- **THEN** the basemap stylesheet references only types present in the basemap database
- **THEN** no "Unknown type" warnings are emitted for the basemap database

#### Scenario: Dark mode applies to the basemap

- **WHEN** the user enables dark mode
- **THEN** the basemap re-renders with its dark-mode colors

#### Scenario: Main style switch leaves basemap unchanged

- **WHEN** the user switches the main map style (e.g. from `standard` to `cycle`)
- **THEN** the basemap continues to render with `basemap-render.oss`

#### Scenario: Basemap labels persist at high zoom

- **WHEN** the user zooms into an area not covered by a regional map
- **THEN** basemap place labels remain visible at the same magnification levels as the standard stylesheet provides for those types
