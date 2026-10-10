# Spec Delta

## ADDED Requirements

### Requirement: Compass needle stroke is density-independent

The compass needle's stroke SHALL be a density-independent size: its drawn width SHALL be the same visual width
on every screen density, measured in dp, so the width the needle has on a 1x screen is the width it has on every
denser screen. A stroke drawn at a fixed device-pixel count thins in dp as density grows (the defect recorded in
`TODO.md` §72: a 3 px stroke is 3 dp at 1x and 0.86 dp at 3.5x) and SHALL NOT be drawn that way.

#### Scenario: Needle stroke keeps its width in dp at 1x and 4x

- **WHEN** the same compass needle is drawn at a 1x density and at a 4x density (the screen density being the
  only difference)
- **THEN** the drawn stroke width in dp SHALL be the same at both densities, within the ±1 pixel the
  antialiased edge adds (at most 1.0 dp at 1x)
- **AND** the drawn stroke width in pixels SHALL grow with the density — a raw-device-pixel stroke keeps the same
  pixel count at every density, so it fails both parts
