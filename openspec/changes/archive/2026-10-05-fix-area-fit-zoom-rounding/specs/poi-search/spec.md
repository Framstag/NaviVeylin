# Spec Delta

## MODIFIED Requirements

### Requirement: Details via single click
Selecting a POI result with a single click SHALL open the location details dialog for that POI, center the map on the POI, and zoom so that the current location and the selected POI are both visible, with visual markers shown at both the current location and the POI. The zoom SHALL fit both positions so that they stay inside the visible map area at the applied magnification: a whole-level magnification and a rotated viewport SHALL NOT push either of them outside it, and the area-favorites magnification floor SHALL NOT prevent the fit.

#### Scenario: Single click opens details and centers the map
- **WHEN** the user clicks a POI result
- **THEN** the location details dialog opens and the map centers on the POI

#### Scenario: Zoom fits current location and POI with markers
- **WHEN** the user clicks a POI result and a current location is available
- **THEN** the zoom level is adjusted so both the current location and the POI are visible, and visual markers are shown at both positions

#### Scenario: Whole-level rounding keeps both positions visible
- **WHEN** the current location and the selected POI would fit at a magnification whose rounding to whole levels enlarges the fitted extent past the visible map area
- **THEN** the applied magnification SHALL be stepped out until both the current location and the POI are inside the visible map area

#### Scenario: Rotated viewport keeps both positions visible
- **WHEN** the map viewport is rotated and the user clicks a POI result while a current location is available
- **THEN** both the current location and the POI SHALL be inside the visible map area at the applied magnification

#### Scenario: Distant POI zooms out below the area-favorites floor
- **WHEN** the user clicks a POI result whose distance from the current location does not fit at the area-favorites magnification floor
- **THEN** the magnification SHALL zoom out below that floor as needed so both the current location and the POI are visible, clamped only to the render-stability minimum

#### Scenario: No current location available
- **WHEN** the user clicks a POI result and no current location is available
- **THEN** the map centers on the POI with the zoom level unchanged, and a marker is shown at the POI

#### Scenario: Details without description
- **WHEN** the user clicks a POI result and no object description is available
- **THEN** the details dialog opens without a description section and without an error

## ADDED Requirements

### Requirement: Embedded result map fit never clips a result

The embedded POI result map SHALL fit the extent it displays — every result position, plus the current position when a fix is available — so that all of them stay inside the embedded viewport at the magnification applied when the map is first shown: a whole-level magnification SHALL NOT push a result position outside it. The embedded map's viewport SHALL remain independent of the main map's viewport.

#### Scenario: Every result stays inside the embedded map
- **WHEN** a POI search returns entries whose extent would fit at a magnification whose rounding to whole levels enlarges the fitted extent past the embedded map
- **THEN** the applied magnification SHALL be stepped out until every result position is inside the embedded map

#### Scenario: Current position stays inside the embedded map
- **WHEN** a POI search returns entries and a current-position fix is available
- **THEN** the current position SHALL be inside the embedded map at the applied magnification, alongside every result position

#### Scenario: Fitting the embedded map leaves the main map alone
- **WHEN** the embedded map fits its results
- **THEN** the main map's center and magnification SHALL remain unchanged
