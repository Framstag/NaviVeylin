# auto/map-pan Specification

## Purpose
TBD - created by archiving change fix-routing-pan-jump. Update Purpose after archive.

## Requirements

### Requirement: Speed-band crossing while panned

The system SHALL keep follow mode and speed-driven auto-zoom suspended when the vehicle speed crosses a speed-band boundary while the map is panned, so a speed change during a pan can never re-engage follow or change the zoom.

#### Scenario: No zoom change across a speed band

- **WHEN** the vehicle speed crosses a speed-band boundary while the map is panned
- **THEN** the map zoom does not change

#### Scenario: Follow stays suspended across a speed band

- **WHEN** the vehicle speed crosses a speed-band boundary while the map is panned
- **THEN** the viewport stays at the panned position and follow mode remains suspended
