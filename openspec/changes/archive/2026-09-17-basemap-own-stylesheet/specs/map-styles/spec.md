# Map Styles

## MODIFIED Requirements

### Requirement: All bundled styles are selectable

The app SHALL offer every top-level `*.oss` stylesheet bundled with the application as a selectable map style, in addition to `standard.oss`, except `basemap-render.oss` which is the basemap's internal stylesheet and SHALL NOT be offered as a user-selectable map style. The selection list MUST be derived from the stylesheets actually present in the bundled application assets, so a future submodule stylesheet addition is picked up without app changes.

#### Scenario: All bundled styles listed

- **WHEN** the user opens the map style picker
- **THEN** the picker lists every top-level `*.oss` stylesheet bundled with the app, including `standard.oss`

#### Scenario: Basemap stylesheet not offered

- **WHEN** the user opens the map style picker
- **THEN** `basemap-render` is not listed as a selectable style

#### Scenario: New upstream stylesheet appears

- **WHEN** a new top-level `*.oss` file is added to the bundled stylesheets and the app is updated
- **THEN** the picker lists the new style without any app code change
