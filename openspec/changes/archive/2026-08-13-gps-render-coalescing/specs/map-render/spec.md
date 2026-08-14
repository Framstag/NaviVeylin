:delta: true

# Map Render

## ADDED Requirements

### Requirement: Safe bitmap lifecycle for sub-region blit

When a sub-region of the front buffer is copied to `_frontBufferFlow`, the system SHALL NOT recycle a bitmap that shares the front buffer's backing pixel storage.

#### Scenario: Panning reuses front-buffer region

- **WHEN** `trySubRegionBlit` or `blitSubRegion` creates a region view from the front buffer
- **THEN** the region is copied to an independent bitmap before any `recycle()` call
- **AND** the front buffer remains valid while Compose may still be drawing it
