# Zoom Transition Scaling

## Purpose

Make placeholder tiles shown during pinch-to-zoom scale smoothly between the previous zoom level and the target zoom level so the user sees no jumps or mismatched content before the native render finishes.

## ADDED Requirements

### Requirement: Zoom placeholder uses continuous scale factor

The system SHALL compute a placeholder scale factor from the previous rendered magnification and the current gesture magnification.

- The scale factor SHALL be `currentMagnification / lastRenderedMagnification`.
- The placeholder SHALL be drawn with that scale factor centered on the pinch focal point.
- The placeholder SHALL be updated on every zoom gesture frame.

#### Scenario: Pinch zoom in shows larger placeholder

- **WHEN** the user spreads two fingers to zoom in
- **THEN** the placeholder image SHALL scale up around the pinch center
- **THEN** map content under the pinch center SHALL stay visually fixed

#### Scenario: Pinch zoom out shows smaller placeholder

- **WHEN** the user pinches two fingers together to zoom out
- **THEN** the placeholder image SHALL scale down around the pinch center
- **THEN** map content under the pinch center SHALL stay visually fixed

### Requirement: Placeholder origin matches geographic anchor

The system SHALL choose the placeholder draw origin so the geographic point under the pinch focal point remains at the same screen pixel throughout the gesture.

- The draw origin SHALL offset the scaled front buffer by the pinch focal point minus the scaled position of the focal point on the front buffer.

#### Scenario: Panning while zooming keeps anchor stable

- **WHEN** the user pinches and pans at the same time
- **THEN** the geographic point under the active focal point SHALL remain under the finger

### Requirement: Native render replaces placeholder at exact target magnification

When the zoom gesture ends and the debounce expires, the system SHALL trigger a native render at the target magnification.

- The placeholder SHALL remain visible until the native render completes.
- On render completion, the front buffer SHALL swap atomically and the placeholder SHALL be replaced by the freshly rendered tiles.

#### Scenario: Render completes after zoom out

- **WHEN** the user finishes a pinch zoom out
- **THEN** the system debounces for 200 ms
- **THEN** a native render is queued at the target magnification
- **WHEN** the render completes
- **THEN** the screen shows tiles matching the target zoom with no visible jump


