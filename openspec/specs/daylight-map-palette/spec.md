# daylight-map-palette Specification

## Purpose
Defines the daylight presentation's map colour contract: the road fill colours that carry the blue/red/orange road classes, the colours derived from them, and the minimum legibility each must keep so that street labels and highway shields stay readable. It covers the light presentation only; the dark presentation is defined by `dark-mode`.

## Requirements

### Requirement: Daylight road fills keep black street labels legible

Every daylight road fill of the blue/red/orange road-class scheme SHALL reach at least 4.5:1 against the daylight way label colour, because way labels are drawn after way fills and street `WAY.TEXT` carries no halo, so the road fill is the label's backdrop.

- The threshold applies to the fill of every road class the scheme colours: motorway, trunk, primary, secondary, tertiary
- The threshold applies wherever the class is drawn at full width, in every bundled style that uses the scheme
- A road fill lighter than the one it replaces SHALL NOT drop a class below the threshold

#### Scenario: Street label on a motorway fill in daylight

- **WHEN** the map renders the daylight style variant and a motorway is drawn at full width
- **THEN** the way label colour reaches at least 4.5:1 against the motorway fill

#### Scenario: Street label on every coloured road class

- **WHEN** the map renders the daylight style variant with a motorway, a trunk, a primary, a secondary and a tertiary road visible
- **THEN** the way label colour reaches at least 4.5:1 against each of their fills

#### Scenario: A non-default style using the scheme

- **WHEN** a selectable style other than the default uses the blue/red/orange road-class scheme
- **THEN** its fills meet the same threshold and it does not keep a darker fill for the same class

### Requirement: Highway shields keep white shield text legible

A highway shield SHALL draw its text in white on a shield background that reaches at least 4.5:1 against white, and that background SHALL be distinguishable from the road fill the shield labels.

- The shield background SHALL be at least 2:1 against the road fill it labels, so the shield still reads as an object on the road
- The shield background MAY be darker than the road fill and need not equal it
- The threshold applies to the motorway, trunk and primary shields

#### Scenario: Shield text legibility in daylight

- **WHEN** the map renders a daylight highway shield at the zoom where shields are shown
- **THEN** the white shield text reaches at least 4.5:1 against the shield background

#### Scenario: Shield reads as an object on the road

- **WHEN** a daylight shield is drawn on its road
- **THEN** the shield background is at least 2:1 against the road fill
- **AND** the shield background is not the road fill itself

#### Scenario: Lighter road fill does not lighten the shield

- **WHEN** a road class fill is lightened in daylight
- **THEN** its shield background is derived so that the white-text threshold still holds, independently of the new fill value

### Requirement: Low-zoom road rendering stays visible on land

The colour a road class is drawn with at the zoom levels below full width SHALL remain distinguishable from the daylight land colour, so the class does not fade into the background as a hairline.

- The colour SHALL reach at least 1.6:1 against the daylight land colour, and SHALL keep the road class hue, at least 25 degrees away from the land hue, so the hairline is distinguishable by hue as well as by luminance. Luminance contrast alone is not the right measure for a sub-millimetre line: the palette already draws its `thinSecondaryColor` and `thinTertiaryColor` hairlines at 1.22:1 and 1.08:1, so 1.6 is above the palette's own convention rather than a relaxation of it
- The threshold is lower than the road-fill text threshold because this rendering is a hairline, not text, and because the low-zoom rendering is deliberately a lighter tint than the fill so the map reads lighter at small scales
- The low-zoom colour SHALL remain lighter than the full-width fill of the same class
- The threshold applies to every road class the scheme colours

#### Scenario: Motorway hairline on land in daylight

- **WHEN** the map renders the daylight style variant at a zoom where the motorway is drawn below full width
- **THEN** the motorway's low-zoom colour reaches at least 1.6:1 against the land colour

#### Scenario: Hairline keeps its road class hue

- **WHEN** the map renders the daylight style variant at a zoom where a coloured road class is drawn below full width
- **THEN** that class's low-zoom colour is at least 25 degrees away in hue from the land colour
- **AND** the class is therefore distinguishable from the land by hue, not only by luminance contrast

#### Scenario: Low-zoom colour follows a lighter fill

- **WHEN** a road class fill is lightened in daylight
- **THEN** the low-zoom colour of that class is derived from the new fill and still reaches 1.6:1 against land
- **AND** it remains lighter than the new full-width fill

### Requirement: Motorway junction labels stay legible on land

A daylight motorway junction label SHALL be drawn with the emphasis style, so it carries a halo, and its own colour SHALL reach at least 1.9:1 against the daylight land colour.

- The emphasis style is required because the label is drawn over varying land cover, where its own contrast alone is not a sufficient guarantee
- The colour SHALL NOT be derived by lightening the motorway fill by a step that drops it below the threshold

#### Scenario: Junction label on light land

- **WHEN** the map renders a daylight motorway junction label over the land colour
- **THEN** the label carries the emphasis halo
- **AND** the label colour reaches at least 1.9:1 against the land colour

### Requirement: Daylight palette is shared by every surface and by every style using the scheme

The daylight road palette SHALL be defined once in the shared stylesheet, so that a given road class has the same fill on every surface and in every bundled style that uses the scheme.

- No surface SHALL override a road fill colour independently
- A style that adopts the scheme SHALL use the same fills for the same classes, so switching map style does not change what a motorway looks like
- Phone and Android Auto SHALL show identical road fills for the same presentation, because both render through the same stylesheet and select the variant through the same `daylight` style flag

#### Scenario: Phone and car road fills match

- **WHEN** the phone map and the Android Auto map are both in daylight presentation with the same stylesheet and the same map data
- **THEN** every road class is drawn with the same fill on both surfaces

#### Scenario: Switching style keeps the road fills

- **WHEN** the user switches from one daylight style that uses the scheme to another that uses it
- **THEN** each road class keeps its fill colour

### Requirement: Daylight palette change alters no geometry, priority or pass count

Changing the daylight road and route colours SHALL NOT change any road's or the route's rendered width, drawing priority, geometry, or the number of render passes needed to draw a map frame.

#### Scenario: Road widths unchanged

- **WHEN** a map frame is rendered before and after the palette change
- **THEN** each road class's rendered width and drawing priority class are the same

#### Scenario: No extra render pass

- **WHEN** a map frame is rendered in daylight with an active route
- **THEN** it is still drawn in the same single render pass, adding no second route draw, no post-render overlay pass and no additional line style

### Requirement: Daylight palette change does not alter the dark presentation

The dark presentation's road fills, route fill and route casing SHALL be unchanged by the daylight palette rules.

#### Scenario: Night road fills unchanged

- **WHEN** the map renders the dark style variant
- **THEN** every road class fill and the route fill and casing keep the values they had before the daylight palette change

#### Scenario: Day and night differ

- **WHEN** the same map extent is rendered once in daylight and once in dark presentation
- **THEN** the daylight fills are lighter than the night fills for every road class the scheme colours
