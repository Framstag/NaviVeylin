# Spec Delta

## ADDED Requirements

### Requirement: The render pin lives only as long as the walk

The magnification walk SHALL pin the magnification the render pipeline uses only while that walk is
pending. A magnification committed by any other path — a camera fit, a mode preset, a re-center —
SHALL reach the render pipeline while no walk is pending, without that path having to end a walk
first: the next frame SHALL be rendered at the committed magnification. A walk that has landed SHALL
NOT keep a render magnification of its own.

#### Scenario: A landed walk does not pin a later magnification

- **WHEN** a walk has landed at magnification A and no walk is pending
- **AND** another path then commits magnification B
- **THEN** the magnification the render pipeline uses SHALL be B
- **AND** the magnification of the landed walk SHALL NOT be used again
- **Case**: `core: ZoomWalkTest` "a landed walk does not pin a later committed magnification"

#### Scenario: A pending walk keeps its step

- **WHEN** a walk is pending and the frame of its current step has not landed yet
- **THEN** the magnification the render pipeline uses SHALL be that step
- **AND** it SHALL NOT be the magnification the viewport records for the walk's target
- **Case**: `core: ZoomWalkTest` "a pending walk still pins its step"

#### Scenario: A camera fit committed while no walk is pending is rendered at the fit's magnification

- **GIVEN** the map has rendered the last frame of a completed walk
- **WHEN** the route overview fit commits its magnification while no walk is pending
- **THEN** the next frame SHALL be rendered at the fit's magnification
- **AND** the fitted route SHALL be inside the frame
- **Case**: `app: MapCanvasViewModelRouteFitTest.fitAfterALandedWalkIsRenderedAtTheFitMagnification`
