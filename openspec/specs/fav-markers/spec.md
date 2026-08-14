# fav-markers Specification

## Purpose

Renders all saved favorite locations as markers on the map using the existing Cairo rendering pipeline, and refreshes markers when favorites change.

## Requirements

### Requirement: All saved favorites render as map markers
The system SHALL render every favorite location from `FavoriteRepository` as a marker on the map. The markers SHALL be passed to the JNI renderer via `renderWithRouteAndPois(favoriteLats, favoriteLons)`.

#### Scenario: Favorites appear on map after loading
- **GIVEN** one or more favorite locations exist
- **WHEN** the map renders
- **THEN** a marker SHALL appear at each favorite location

#### Scenario: No markers when no favorites exist
- **GIVEN** no favorite locations exist
- **WHEN** the map renders
- **THEN** no favorite markers SHALL be rendered

### Requirement: Favorite markers update reactively
When favorites are added, deleted, or renamed through the repository, the marker data SHALL update and trigger a map re-render.

#### Scenario: Adding a favorite shows new marker
- **WHEN** a new favorite is added
- **THEN** a new marker SHALL appear on the map at the next render

#### Scenario: Deleting a favorite removes its marker
- **WHEN** a favorite is deleted
- **THEN** its marker SHALL disappear on the next render

### Requirement: Favorite markers use the `_favorite` type
The markers SHALL use the existing `_favorite` synthetic node type already defined in the libosmscout stylesheet, styled with higher priority than typical POI icons.

#### Scenario: Favorite marker visible at detail zoom
- **GIVEN** a favorite marker is within the viewport
- **WHEN** the map is rendered at detail zoom or higher
- **THEN** the favorite marker SHALL be visible

#### Scenario: Favorite marker hidden at low zoom
- **GIVEN** a favorite marker is within the viewport
- **WHEN** the map is rendered below detail zoom
- **THEN** the favorite marker SHALL NOT be visible
