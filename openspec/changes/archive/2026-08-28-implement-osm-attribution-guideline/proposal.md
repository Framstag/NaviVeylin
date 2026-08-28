## Why

NaviVeylin renders map data and computes routes from OpenStreetMap (OSM) data via libosmscout. OSM data is licensed under the Open Database License (ODbL), which requires attribution to OpenStreetMap and a clear statement that the data is available under the ODbL. The OpenStreetMap Foundation's Attribution Guidelines (adopted 2021-06-25) define the safe harbour for interactive maps and routing engines. The app currently shows no OSM attribution anywhere, so it is not compliant with the ODbL or the OSMF guidelines.

## What Changes

- Add a persistent attribution notice (e.g. "© OpenStreetMap contributors") in a corner of the map canvas on the main map screen, legible and WCAG-friendly per the OSMF guidelines.
- Make the attribution text link to https://www.openstreetmap.org/copyright, which states the ODbL and credits OSM's data sources.
- Allow the attribution to collapse/fade per the guidelines (auto-hide after five seconds or on map interaction), but keep the licence information reachable at all times via an "(i)"-style button on the map and/or an entry in the About dialog.
- Add an OSM data licence/attribution section to the About dialog linking to openstreetmap.org/copyright and the ODbL.
- Apply the same attribution to both distribution flavors (mobile and automotive/AAOS), since both render OSM-derived maps.

## Capabilities

### New Capabilities
- `osm-attribution`: Display of the OSM attribution notice on the map and access to OSM licence information, compliant with the OSMF Attribution Guidelines and the ODbL.

### Modified Capabilities
- `about-dialog`: Add a requirement that the About dialog provides a link to the OSM data licence (openstreetmap.org/copyright) and the ODbL, so licence information remains reachable when the map attribution is collapsed.

## Impact

- `app` module UI: new attribution overlay composable on the map canvas screen, "(i)" licence button, About dialog extension, string resources.
- Both flavors (`mobile`, `automotive`) — no flavor-specific divergence.
- No native/libosmscout changes, no new dependencies.
- Tests: Compose UI tests for attribution visibility, collapse behaviour, and licence link reachability.
