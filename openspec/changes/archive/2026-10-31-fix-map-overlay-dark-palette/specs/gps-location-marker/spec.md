# gps-location-marker — delta

## ADDED Requirements

### Requirement: Accuracy circle follows the resolved presentation

The accuracy circle's fill and border SHALL be drawn from the marker palette's presentation branch: a light tone
in light presentation and a dark tone of the same hue family in dark presentation, both read from one shared value
so the phone overlay and the Android Auto renderer draw the same ring. The circle's geometry — its radius, its
stroke width and the minimum-radius rule — SHALL NOT branch on presentation.

#### Scenario: Dark presentation draws the dark ring

- **WHEN** dark presentation is active and a poor GPS fix draws the accuracy circle
- **THEN** the circle's fill and border SHALL be the dark tone of the ring's hue family
- **AND** they SHALL differ from the tones the same circle is drawn with under light presentation
- **Case**: `app: LocationMarkerOverlayPaletteTest` "theDrawnAccuracyRingChangesWithThePresentation",
  `auto: AutoMapRendererMarkerDrawCacheTest` "theAccuracyRingBranchesOnTheDarkPresentation"

#### Scenario: Phone and car draw the same ring

- **WHEN** the phone overlay and the Android Auto renderer draw the accuracy circle for the same presentation
- **THEN** both SHALL use the same fill and border value
- **AND** neither surface SHALL carry a hue the other does not
- **Case**: `auto: AutoMapRendererMarkerDrawCacheTest` "theRingPaintsTakeTheSharedPaletteValues",
  `app: LocationMarkerOverlayPaletteTest` "theLightRingKeepsItsShippedTone"

#### Scenario: The accuracy circle's geometry does not branch

- **WHEN** the accuracy circle is drawn for the same fix and viewport in either presentation
- **THEN** its drawn radius and its stroke width SHALL be identical
- **AND** only the fill and border colours SHALL differ
- **Case**: `app: LocationMarkerOverlayPaletteTest` "theRingRadiusAndStrokeWidthAreIdenticalInBothPresentations"
