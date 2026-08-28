## ADDED Requirements

### Requirement: About dialog provides link to OSM data licence
The about dialog SHALL provide a link to the OpenStreetMap data licence at https://www.openstreetmap.org/copyright and state that map data is available under the Open Database License (ODbL).

#### Scenario: OSM licence link visible
- **WHEN** the about dialog is open
- **THEN** the dialog SHALL show a link to the OSM data licence

#### Scenario: OSM licence link opens copyright page
- **WHEN** the user taps the OSM licence link
- **THEN** the system SHALL open https://www.openstreetmap.org/copyright in the device browser
