# smooth-zoom — delta

## ADDED Requirements

### Requirement: Double tap is the map's second discrete zoom-in input

The map canvas SHALL report a double tap — two taps inside the platform's double-tap window and touch slop, on
one point, while no pinch gesture is active — as a discrete zoom-in input beside the scroll wheel and the zoom
buttons. A drag, a pinch or a long press SHALL consume a pending tap, so it cannot pair with an earlier tap.
Two taps separated by more than the platform's double-tap window SHALL be two single taps and SHALL NOT be a
double tap.

#### Scenario: Two taps inside the platform's window are one double tap

- **WHEN** two taps land on the same point inside the platform's double-tap window and touch slop
- **THEN** the canvas SHALL report exactly one double tap, at the point the second tap lifted
- **AND** the taps SHALL NOT read as a long press, a pan or a pinch
- **Case**: `app: MapGestureComposeTest` "twoTapsInsideTheDoubleTapWindowReportOneDoubleTap"

#### Scenario: Taps beyond the platform's window are two single taps

- **WHEN** two taps on the same point are separated by more than the platform's double-tap window
- **THEN** no double tap SHALL be reported, so no zoom step is committed
- **Case**: `app: MapGestureComposeTest` "twoTapsBeyondTheDoubleTapWindowReportNoDoubleTap"

### Requirement: A double tap steps one level at its own point

A double tap SHALL commit the next magnification level above the level the viewport records, never above the
maximum magnification, and at the maximum it SHALL change neither the viewport nor the display. The geographic
point under the tap point SHALL stay at the same screen pixel for every frame of the eased transition, which
SHALL retrack from the currently displayed scale. The step SHALL go through the viewport commit a
zoom-button press uses, so it is a manual zoom (spec: `map-modes`).

#### Scenario: A double tap steps one level around the tap point

- **WHEN** the user double-taps the map canvas at a point, below the maximum magnification
- **THEN** the recorded magnification SHALL be one level above the recorded level
- **AND** the geographic point under the tap point SHALL be at the same screen pixel after the commit
- **AND** the displayed map SHALL ease from the currently rendered scale to the new level around that pixel
- **Case**: `app: MapCanvasDoubleTapZoomTest` "double tap steps one level and keeps the tap point fixed"

#### Scenario: A double tap at the maximum magnification changes nothing

- **GIVEN** the magnification is at the maximum
- **WHEN** the user double-taps the map canvas
- **THEN** the recorded magnification SHALL stay at the maximum
- **AND** the viewport centre SHALL NOT move
- **AND** no zoom transition SHALL start
- **Case**: `app: MapCanvasDoubleTapZoomTest` "double tap at the maximum magnification leaves the viewport alone"
