# dark-mode — delta

## ADDED Requirements

### Requirement: Which app-drawn elements stay visible in dark presentation

Dark presentation SHALL keep a clearly visible tone only for app-drawn elements that carry navigation-essential
information: the vehicle's own position marker and the active route. Every other app-drawn element SHALL dim to a
dark tone of its own hue family, a status-carrying overlay included. The exempt set SHALL stay small and SHALL be
enumerable from this specification, so a dark map shows few loud elements.

#### Scenario: The navigation-essential elements keep their dark tone

- **WHEN** dark presentation is active and the map draws the vehicle marker or the active route
- **THEN** the marker SHALL use its dark casing with its lighter dark-presentation core
- **AND** the route SHALL keep its own dark-presentation colours
- **AND** neither SHALL be dimmed by this requirement
- **Case**: `auto: AutoMapRendererMarkerDrawCacheTest` "aDarkPresentationChangeRebuildsThePalette"

#### Scenario: An overlay that carries no nav-essential information dims

- **WHEN** dark presentation is active and an app-drawn overlay that is neither the vehicle marker nor the route
  is drawn
- **THEN** it SHALL be drawn in a dark tone of its own hue family
- **AND** it SHALL NOT keep the tone it is drawn in under light presentation
- **Case**: `app: LocationMarkerOverlayPaletteTest` "theDrawnAccuracyRingUsesADarkerToneOfTheSameHueFamily",
  `auto: AutoMapRendererMarkerDrawCacheTest` "theAccuracyRingBranchesOnTheDarkPresentation"

#### Scenario: No overlay keeps a light literal while dark presentation is active

- **WHEN** every app-drawn overlay colour of this palette is resolved for dark presentation
- **THEN** each SHALL resolve to a different value than it does under light presentation
- **AND** none SHALL still be the presentation-independent literal it was before this palette existed
- **Case**: `core: GpsMarkerPaletteTest` "everyOverlayColourBranchesOnThePresentation"
