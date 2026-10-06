# Spec Delta

## MODIFIED Requirements

### Requirement: Bounding box zoom calculation

For area objects, the system SHALL compute a magnification that fits the object's bounding box within the current viewport dimensions with configurable padding, and the fitted bounding box SHALL stay inside the visible viewport at the magnification that is applied, down to the area-favorites magnification floor: a whole-level magnification and a rotated viewport SHALL NOT push the object's extent outside the visible area.

#### Scenario: Area fits viewport
- **WHEN** object bounding box is computed
- **THEN** magnification is calculated so the bounding box plus padding fills no more than 80% of the viewport

#### Scenario: Whole-level rounding does not crop the object
- **WHEN** the object's exact fit magnification lies more than half a level above a whole level, so rounding to whole levels would enlarge the fitted bounding box past the viewport, and the user selects that area favorite
- **THEN** the applied magnification SHALL be stepped out until the whole bounding box (plus padding) is inside the visible viewport

#### Scenario: Rotated viewport still contains the object
- **WHEN** the map viewport is rotated and the user selects an area favorite whose bounding box is fitted
- **THEN** the whole bounding box SHALL be inside the visible viewport at the applied magnification

#### Scenario: The area-favorites floor bounds the fit
- **WHEN** the object's extent does not fit inside the visible viewport even at the area-favorites magnification floor
- **THEN** the applied magnification SHALL be that floor — the object is not fitted further out — and no magnification below it SHALL be used

#### Scenario: Magnitude clamped to valid range
- **WHEN** computed magnification is outside valid range (4–18)
- **THEN** magnification is clamped to the nearest valid value
