# Canvas Overrun Specification

## Purpose

Eliminate unnecessary native re-renders during small pan gestures by rendering a larger-than-screen buffer and blitting the visible sub-region when the viewport shifts within the overrun area.

## Requirements

### Requirement: Configurable canvas overrun

The system SHALL render the map at a configurable multiplier of the screen size.

- The default overrun multiplier SHALL be 1.2×
- The render width SHALL be `screenWidth × overrunMultiplier`
- The render height SHALL be `screenHeight × overrunMultiplier`
- The overrun multiplier SHALL be configurable at runtime
- The overrun buffer SHALL be centered on the viewport center

#### Scenario: Initial render uses overrun

- **WHEN** the map first renders on a 1080×1920 screen
- **THEN** the render target is 1296×2304 pixels (1.2×)
- **THEN** the visible 1080×1920 region is extracted from the center of the overrun buffer

#### Scenario: Overrun multiplier changed

- **WHEN** the overrun multiplier is changed from 1.2 to 1.5
- **THEN** subsequent renders use 1.5× screen dimensions
- **THEN** the next render uses the new multiplier

### Requirement: Sub-region blit for pan

When the user pans within the overrun buffer bounds, the system SHALL blit the visible sub-region from the overrun buffer instead of triggering a full native re-render.

- On each pan delta, the system SHALL compute the new viewport's position within the overrun buffer using Mercator projection
- If the new viewport rectangle is fully contained within the overrun buffer, the system SHALL blit the corresponding sub-region to the display
- If the new viewport extends beyond the overrun buffer, the system SHALL trigger a full re-render centered on the new viewport
- The sub-region blit SHALL complete within a single frame (no async wait)

#### Scenario: Small pan uses sub-region blit

- **WHEN** user pans 50 pixels right on a 1080×1920 screen with 1.2× overrun
- **THEN** the new viewport rectangle (offset by 50px) is within the 1296×2304 overrun buffer
- **THEN** the system blits the visible sub-region from the overrun buffer
- **THEN** no native render call is made

#### Scenario: Large pan triggers full re-render

- **WHEN** user pans 200 pixels right on a 1080×1920 screen with 1.2× overrun
- **THEN** the new viewport rectangle extends beyond the 1296×2304 overrun buffer
- **THEN** the system triggers a full native render centered on the new viewport
- **THEN** a new overrun buffer is created at the new center
