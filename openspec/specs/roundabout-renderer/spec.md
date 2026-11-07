# roundabout-renderer Specification

## Purpose

Renders roundabout navigation symbols as Compose Canvas drawings that visually represent the roundabout geometry — showing the roundabout as a circle, exit positions at correct angles, and highlighting the exit the driver should take.

## Requirements

### Requirement: Roundabout rendered as circle with exits

The roundabout renderer SHALL draw the roundabout as a circle with exit markers positioned around its circumference. Exit count and positions SHALL be configurable.

#### Scenario: Roundabout with 3 exits
- **WHEN** the renderer receives a roundabout with 3 exits
- **THEN** it SHALL draw a circle with 3 exit markers evenly distributed around the circumference
- **AND** one exit SHALL be visually highlighted as the selected exit

#### Scenario: Roundabout with 4 exits
- **WHEN** the renderer receives a roundabout with 4 exits
- **THEN** it SHALL draw a circle with 4 exit markers evenly distributed

#### Scenario: Roundabout with 2 exits
- **WHEN** the renderer receives a roundabout with 2 exits
- **THEN** it SHALL draw a circle with 2 exit markers

#### Scenario: Roundabout with 6+ exits
- **WHEN** the renderer receives a roundabout with 6 or more exits
- **THEN** it SHALL draw a circle with all exit markers, scaling down proportionally to fit

### Requirement: Exit position reflects actual geometry

Exit positions on the roundabout SHALL be placed at angles that reflect the actual road geometry, not just evenly spaced. When angle data is available from the route step ahead, exits SHALL be positioned accordingly.

#### Scenario: Exits at specific angles
- **WHEN** the renderer receives exit angle data (e.g., exit 1 at 45°, exit 2 at 180°, exit 3 at 315°)
- **THEN** exit markers SHALL be placed at those specific angles on the circle

#### Scenario: No angle data — evenly spaced fallback
- **WHEN** the renderer receives no angle data
- **THEN** exit markers SHALL be evenly distributed around the circle

### Requirement: Selected exit visually distinct

The exit the driver should take SHALL be visually emphasized — different color, size, or style from non-selected exits.

#### Scenario: Selected exit highlighted
- **WHEN** the renderer receives a selected exit index
- **THEN** that exit marker SHALL be drawn in the primary/accent color
- **AND** non-selected exits SHALL be drawn in a muted color

#### Scenario: No selected exit
- **WHEN** the renderer receives no selected exit index
- **THEN** all exit markers SHALL be drawn in the same style

### Requirement: Entry direction indicator

The roundabout renderer SHALL show the entry direction — where the driver enters the roundabout — as a distinct marker or gap on the circle.

#### Scenario: Entry direction shown
- **WHEN** the renderer receives entry angle data
- **THEN** the entry point SHALL be marked on the roundabout circle

### Requirement: Roundabout composable API

The renderer SHALL expose a `@Composable` function that accepts exit count, exit angles, selected exit index, entry angle, size, and color parameters.

#### Scenario: Composable renders roundabout
- **WHEN** the composable is called with exit count and selected exit
- **THEN** it SHALL draw the roundabout visualization

#### Scenario: Composable with all parameters
- **WHEN** the composable is called with exit count, exit angles, selected exit, entry angle, size, and color
- **THEN** it SHALL draw the full roundabout visualization with all data applied
### Requirement: Roundabout exit symbol shows the exit number

The renderer SHALL draw a roundabout-exit symbol that carries the exit ordinal: a ring with the number of the
exit to take in its centre. The number SHALL be legible at the smallest size any surface draws the symbol,
and SHALL be the same number the instruction text names for that exit. A surface with no exit ordinal SHALL
draw the existing roundabout leave arrow instead of this symbol.

#### Scenario: The exit number is drawn on the symbol

- **WHEN** the renderer receives a roundabout exit symbol with exit ordinal 3
- **THEN** it SHALL draw a ring whose centre carries the digit 3
- **Case**: `core: ManeuverSymbolsTest` "the roundabout exit glyph carries the exit number"

#### Scenario: Two exits produce two different symbols

- **WHEN** the renderer draws an exit symbol for ordinal 1 and one for ordinal 3
- **THEN** the two symbols SHALL differ
- **AND** neither SHALL be blank
- **Case**: `core: ManeuverSymbolsTest` "the roundabout exit glyph differs per ordinal"

#### Scenario: Ordinals above a single digit are drawn

- **WHEN** the renderer receives a roundabout exit symbol with an ordinal of 10 or more
- **THEN** it SHALL draw the full number without clipping it or overflowing the ring
- **Case**: `core: ManeuverSymbolsTest` "a two-digit exit ordinal is drawn inside the ring"

#### Scenario: The number is legible at the smallest surface size

- **WHEN** the renderer draws the exit symbol at the 48 px edge the notification's large icon and the car
  bitmap use (`ManeuverSymbols.BITMAP_SIZE`) and at the 64 dp the phone overlay draws
- **THEN** the digit's rendered height SHALL be at least 0.16 of the symbol's edge
- **AND** the digit SHALL fit inside the ring's inner diameter, including for a two-digit ordinal
- **AND** an ordinal beyond two digits SHALL still be drawn inside the ring rather than clipped, though its
  legibility SHALL NOT be asserted as a bound: it is size-dependent (measured 0.250 of the edge at 48 px
  against 0.143 at the phone overlay's 168 px for ordinal 112), so the legible floor holds over the ordinals
  a roundabout exit carries and the drawing-inside-the-ring invariant holds over every ordinal
- **Case**: `app(device): RoundaboutExitGlyphDeviceTest` "the exit number is legible at the smallest surface size" — measured: 48 px digit 10×13 / 11×13 / 14×13 px against a 16 px ring inner diameter for ordinals 1 / 3 / 12, and 13×32 / 20×33 / 44×32 px against 57 px at the phone overlay's 64 dp (168 px on the measured device). Every height is above the 8 px floor at 48 px; the two-digit ordinal is inside the ring on both surfaces; ordinal 112 measures 14×12 px there and 48×24 px / 0.143 of the edge at 168 px, inside the ring at both. No size needed adjusting.

#### Scenario: No ordinal falls back to the plain leave arrow

- **WHEN** a roundabout exit manoeuvre carries no exit ordinal
- **THEN** the surface SHALL draw the roundabout leave arrow it draws today
- **AND** it SHALL NOT draw a ring with an invented or placeholder number
- **Case**: `app: NextTurnOverlayTest` "a roundabout exit without an ordinal keeps the plain leave arrow"

#### Scenario: The symbol is not the ring-with-exit-markers drawn from a ring total

- **WHEN** the renderer is asked for the exit ordinal symbol
- **THEN** it SHALL NOT place a marker per exit around the ring
- **AND** the number of markers SHALL NOT be inferred from the ordinal
- **Case**: `core: ManeuverSymbolsTest` "the exit ordinal symbol carries no per-exit markers"

### Requirement: Phone and car roundabout symbols stay one artwork

The numbered roundabout-exit symbol SHALL be drawn by one shared implementation used by the phone overlays,
the phone notification and the car bitmap surfaces, so a fix to the number's geometry reaches every surface
at once. Where a car surface cannot use it — the host draws the manoeuvre icon itself — the deviation SHALL be
the icon only: the instruction wording SHALL stay identical on both variants.

#### Scenario: One artwork for phone and car bitmaps

- **WHEN** the phone next-turn overlay and the car hint's large icon show the same roundabout exit
- **THEN** both SHALL use the same symbol implementation
- **AND** both SHALL carry the same number
- **Case**: `auto: ManeuverGlyphsTest` "the car hint icon uses the shared exit symbol"

#### Scenario: Labels stay at parity where the glyph cannot

- **WHEN** the car host draws its own numbered roundabout on the instruction panel
- **THEN** the instruction wording SHALL still be the same string the phone shows
- **AND** only the icon SHALL differ
- **Case**: `auto: NavigationTemplateMapperTest` "the host-numbered roundabout keeps the shared cue text"

