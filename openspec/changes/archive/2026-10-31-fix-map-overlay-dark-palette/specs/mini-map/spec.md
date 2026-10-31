# mini-map — delta

## ADDED Requirements

### Requirement: Current-position marker palette and distinctness

The mini map's current-position marker SHALL draw its dot, its accuracy halo and its casing from the marker
palette's presentation branch. The dot SHALL keep a clearly visible tone in dark presentation because it carries
the user's own position, and its hue SHALL stay distinct from the colours the mini map gives the primary,
additional and selected object markers.

#### Scenario: The dot's hue is not an object-marker colour

- **WHEN** the mini map draws the current-position marker, in either presentation
- **THEN** the dot's colour SHALL differ from the primary, additional and selected object-marker colours
- **AND** it SHALL NOT be the theme role the primary object marker is drawn with
- **Case**: `app: MiniMapCurrentPositionPaletteTest` "theDrawnDotIsNotTheObjectMarkerPrimary",
  `core: GpsMarkerPaletteTest` "theLightDotIsNotAnObjectMarkerColour"

#### Scenario: The dot stays visible in dark presentation

- **WHEN** dark presentation is active and the mini map draws the current-position marker
- **THEN** the dot SHALL be drawn in a tone of its own hue family that is brighter than its halo
- **AND** it SHALL differ from the tone the same dot is drawn with under light presentation
- **Case**: `core: GpsMarkerPaletteTest` "theDotIsBrighterThanItsHaloInDarkPresentation",
  `app: MiniMapCurrentPositionPaletteTest` "theDrawnDotChangesWithThePresentation"

#### Scenario: The halo and the casing dim in dark presentation

- **WHEN** dark presentation is active and the mini map draws the current-position marker
- **THEN** its accuracy halo and its casing SHALL be drawn in a dark tone of their own hue family
- **AND** the marker SHALL NOT keep the white casing rings it is drawn with under light presentation
- **Case**: `core: GpsMarkerPaletteTest` "everyOverlayColourBranchesOnThePresentation"
