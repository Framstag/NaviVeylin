# Adaptive Zoom Specification

## Purpose

Provide immediate visual feedback during zoom gestures by scaling the current front buffer as a placeholder while the high-quality native render completes in the background.

## Requirements

### Requirement: Zoom placeholder from scaled buffer

When the zoom level changes, the system SHALL immediately scale the current front buffer to the new magnification as a visual placeholder.

- On zoom in, the system SHALL extract the region around the zoom center from the front buffer and scale it up by `2^(newMag - oldMag)`
- On zoom out, the system SHALL scale the entire front buffer down by `2^(oldMag - newMag)` and center it
- The placeholder SHALL be displayed within the same frame as the zoom gesture
- The placeholder SHALL be replaced by the full-quality render when it completes
- The epoch SHALL be incremented on zoom change to discard stale zoom renders in the queue

#### Scenario: Zoom in shows scaled placeholder

- **WHEN** user pinches to zoom in from level 12 to level 13
- **THEN** the system immediately scales the current display by 2× around the pinch center
- **THEN** the user sees a pixelated but responsive zoomed view
- **WHEN** the full native render at level 13 completes
- **THEN** the placeholder is replaced with the high-quality render

#### Scenario: Zoom out shows scaled placeholder

- **WHEN** user pinches to zoom out from level 12 to level 11
- **THEN** the system immediately scales the current display by 0.5×
- **THEN** the user sees a shrunken view centered on the screen
- **WHEN** the full native render at level 11 completes
- **THEN** the placeholder is replaced with the high-quality render

### Requirement: Render timing metrics

The system SHALL measure and log native render duration for performance monitoring.

- Each render call SHALL record start and end timestamps
- The elapsed time SHALL be logged at DEBUG level
- If render time exceeds a configurable threshold (default 500ms), a WARNING SHALL be logged
- Render timing SHALL be available for adaptive quality decisions

#### Scenario: Slow render logged

- **WHEN** a native render takes 1200ms
- **THEN** a WARNING log entry is written with the elapsed time
- **THEN** the system may reduce overrun multiplier or quality for subsequent renders
