# Spec Delta — auto-map-renderer

## ADDED Requirements

### Requirement: Marker drawing allocates no per-frame objects

The car map renderer's marker drawing (the GPS position marker and the destination marker) SHALL
create its drawing objects — path outlines, paints, shaders and blur filters — on first use and reuse
them across frames. They SHALL be rebuilt only when an input that defines their geometry or palette
changes: the surface bounds, the surface density, or the day/night presentation. The rendered marker
SHALL keep the geometry, size and palette contract of the marker requirements above.

#### Scenario: Repeated frames allocate nothing

- **WHEN** the GPS marker is drawn on N consecutive frames with unchanged surface bounds, density and presentation
- **THEN** no new drawing object SHALL be created for those frames
- **AND** the marker SHALL be drawn at the same position, size and palette as before the change

#### Scenario: Presentation change rebuilds the palette

- **WHEN** the host day/night state changes while the car map draws markers
- **THEN** the marker's palette objects SHALL be rebuilt for the new presentation
- **AND** the next drawn frame SHALL use the dark-presentation or daylight palette for the new state

#### Scenario: Surface bounds or density change rebuilds the geometry

- **WHEN** the car surface is resized, or recreated at a different display density
- **THEN** the geometry-dependent drawing objects SHALL be rebuilt for the new bounds and density
- **AND** the marker SHALL keep its density-aware size on the new surface

#### Scenario: Destination marker follows the same rule

- **WHEN** the destination marker is drawn on consecutive frames with unchanged inputs
- **THEN** no new drawing object SHALL be created for those frames
- **AND** the pin and its label SHALL be drawn as before
