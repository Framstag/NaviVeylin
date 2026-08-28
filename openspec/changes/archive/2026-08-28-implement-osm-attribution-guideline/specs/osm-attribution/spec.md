## Purpose

Displays the OpenStreetMap attribution notice on the map and provides access to OSM licence information, satisfying the OSMF Attribution Guidelines and the ODbL.

## ADDED Requirements

### Requirement: Map displays OSM attribution notice
The map canvas SHALL display an attribution notice crediting OpenStreetMap with the text "© OpenStreetMap contributors" in a corner of the map, visible without requiring user interaction, and legible with sufficient contrast against the map background.

#### Scenario: Attribution visible on map load
- **WHEN** the map canvas is displayed
- **THEN** the attribution notice SHALL be visible in a corner of the map

#### Scenario: Attribution is legible
- **WHEN** the attribution notice is displayed
- **THEN** the text SHALL be readable with sufficient contrast against the map background

### Requirement: Attribution links to OSM copyright page
The attribution notice SHALL link to https://www.openstreetmap.org/copyright, which states the Open Database License and credits OpenStreetMap's data sources.

#### Scenario: Tap attribution opens copyright page
- **WHEN** the user taps the attribution notice
- **THEN** the system SHALL open https://www.openstreetmap.org/copyright in the device browser

### Requirement: Attribution may collapse but licence info stays reachable
The attribution notice MAY auto-hide after five seconds or on map interaction, but the OSM licence information SHALL remain reachable at all times via an "(i)"-style button on the map.

#### Scenario: Attribution auto-hides after five seconds
- **WHEN** the map is displayed without user interaction for five seconds
- **THEN** the attribution notice MAY fade out

#### Scenario: Licence info reachable after collapse
- **WHEN** the attribution notice is hidden
- **THEN** the user SHALL be able to reach the OSM licence information via an "(i)"-style button on the map

### Requirement: Attribution shown on all distribution flavors
Both the mobile and automotive (AAOS) distribution flavors SHALL display the OSM attribution notice on the map.

#### Scenario: Attribution on automotive head unit
- **WHEN** the app runs on an Android Automotive OS head unit
- **THEN** the map SHALL display the OSM attribution notice

### Requirement: Attribution shown in Android Auto variant
The Android Auto projection map SHALL display the OSM attribution notice on the map surface, and the OSM licence information SHALL be reachable from the car app.

#### Scenario: Attribution on car map surface
- **WHEN** the car app displays the map (free driving or navigation)
- **THEN** the attribution notice SHALL be visible on the map surface

#### Scenario: Licence info reachable in car app
- **WHEN** the user opens the About screen in the car app
- **THEN** the OSM licence information SHALL be shown
