# render-projection-dpi Specification

## Purpose
Define how each drawing surface's physical DPI reaches the native renderer, so a frame is always projected at the DPI of the display it is drawn on — regardless of which other surfaces have been active in the same process before it.

## Requirements

### Requirement: Render requests carry the projection DPI

Every native map render request SHALL carry the physical DPI of the display whose frame is being produced, and the render SHALL be projected with that value.

- The value SHALL come from the requesting surface: the phone's display metrics for the phone canvas, the DPI delivered with the car surface by the Car App Library surface callback for the car surface
- A surface SHALL pass its own DPI on every render request it issues — full renders and per-tile renders alike
- The projection of a frame SHALL depend only on its own request's DPI; no frame SHALL be projected with a DPI configured by another surface
- Both surfaces SHALL pass their DPI the same way (parity); the platform constraint that forces different values is that the phone canvas and the car surface are physically different displays with different densities

#### Scenario: Phone frame rendered while a car session is active

- **WHEN** a phone map render request is issued while an Android Auto session is active in the same process
- **THEN** the frame is projected at the phone display's DPI
- **THEN** the resulting map content scale matches the phone renderer's own overlay and gesture projection

#### Scenario: Car frame rendered after the phone UI ran

- **WHEN** a car surface render request is issued after the phone UI has rendered in the same process
- **THEN** the frame is projected at the car surface's delivered DPI

#### Scenario: Per-tile render carries the same DPI as the frame

- **WHEN** a surface renders a single geographic tile
- **THEN** the tile request carries the same DPI as that renderer's full-frame requests
- **THEN** the composed frame's tiles and its overlays agree on scale

### Requirement: No process-global projection DPI

The system SHALL NOT keep a process-global projection DPI that influences rendering, and the activity of one surface SHALL NOT change the scale of a frame drawn by another.

- A surface SHALL NOT be required to re-apply a DPI before rendering, and a rendered frame SHALL NOT depend on which surface rendered last
- A DPI change on the car surface SHALL affect the car surface's own frames only

#### Scenario: Surface switch leaves the other surface's scale intact

- **WHEN** the user navigates on the car surface and then renders on the phone canvas in the same process
- **THEN** the phone frame's geographic scale matches the phone's own DPI
- **THEN** no screen re-entry, map re-initialisation or process restart is needed to make it match

#### Scenario: Car surface replacement

- **WHEN** the host delivers a car surface with a different DPI
- **THEN** subsequently rendered car frames use the new value
- **THEN** frames rendered on the phone canvas are unaffected

### Requirement: Carrying the DPI adds no render cost

Carrying the projection DPI with a render request SHALL NOT add a native render call, a second render pass, or a per-frame allocation beyond passing a scalar value.

#### Scenario: One native render per frame

- **WHEN** a frame is rendered
- **THEN** exactly one native render call is issued for the frame (or one per missing tile in tile mode)
- **THEN** no additional native render is triggered by the DPI value itself
