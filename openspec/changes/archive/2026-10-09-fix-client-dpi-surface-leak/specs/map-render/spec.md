# Spec Delta

## ADDED Requirements

### Requirement: Phone renders carry the display DPI

The phone map render path SHALL pass the phone display's physical DPI with every native render request it issues, and SHALL NOT configure a client-wide render DPI.

- The value SHALL be taken from the display's metrics (`DisplayMetrics.densityDpi`), the same source the phone renderer uses for its overlay and gesture projection
- The DPI SHALL be passed for full renders and for the per-tile renders of the tile path
- Entering or re-entering the map screen SHALL NOT be required for the phone's frames to be projected correctly

#### Scenario: Full render carries the display DPI

- **WHEN** the phone renders a full frame
- **THEN** the render request carries the phone display's DPI
- **THEN** the frame's geographic scale matches the phone renderer's overlay projection

#### Scenario: Tile render carries the display DPI

- **WHEN** the phone composes a frame from geographic tiles
- **THEN** each tile render request carries the same display DPI as the full-frame requests

#### Scenario: Map initialisation configures no client-wide DPI

- **WHEN** the phone map screen initialises the map database
- **THEN** no client-wide render DPI is configured
- **THEN** the first rendered frame is still projected at the phone display's DPI
