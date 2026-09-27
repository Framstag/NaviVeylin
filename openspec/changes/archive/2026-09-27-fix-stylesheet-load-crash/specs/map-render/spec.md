# Spec Delta

App-visible behaviour only. The client-side rendering guarantee (a rejected configuration is never
handed to the painter) is specified in the libosmscout repository
(`openspec/changes/client-style-load-resilience`, capability `client-java-style-switching`).

## ADDED Requirements

### Requirement: A stylesheet that cannot be loaded degrades the map instead of crashing the app

When a stylesheet cannot be loaded, the app SHALL keep running: the map area SHALL show no content for
the affected database instead of terminating the process, remaining content (other loaded databases,
overlays, route and guidance) SHALL continue to be drawn, and the failure SHALL be reported to the user
as specified for map styles. The app SHALL NOT add a per-frame cost for this guarantee: the state is
established when a stylesheet is loaded.

#### Scenario: Rejected stylesheet at startup
- **WHEN** the app starts and the stylesheet of a loaded database cannot be parsed
- **THEN** the app does not terminate
- **THEN** the map area draws no content for that database and the failure message is shown

#### Scenario: Remaining content still renders
- **WHEN** one of several loaded databases has no usable stylesheet while the others do
- **THEN** the other databases still draw their content in the same frame

#### Scenario: Recovery without a restart
- **WHEN** a valid stylesheet is loaded after a failed attempt (style switch or stylesheet refresh)
- **THEN** the map draws content again with that stylesheet without restarting the app

#### Scenario: Basemap stylesheet cannot be loaded
- **WHEN** the basemap's own stylesheet cannot be parsed while the map stylesheet loads successfully
- **THEN** the map content still renders, the basemap layer is absent for that frame, and the failure is reported
