# Spec Delta — route-appearance

## MODIFIED Requirements

### Requirement: Active route stands out from road classes in daylight

In light presentation the active route polyline SHALL be drawn with a contrasting casing (border) under a fill so that it is distinguishable from every road class of the active daylight stylesheet, including primary roads. The daylight fill MAY be translucent, but the rendered route SHALL remain opaque with respect to the road beneath it: the casing SHALL be opaque and drawn as a wider stroke under the fill, so the composited colour of the route's centre does not depend on the road colour underneath it.

- The road colour underneath SHALL NOT determine the route's rendered centre colour, whether or not the fill itself is translucent
- The daylight fill hue SHALL NOT be the hue of any road class of the daylight stylesheet (motorway, trunk, primary, secondary, tertiary, residential/road): the hue separation SHALL be at least 25 degrees
- The casing SHALL be drawn as a wider stroke under the fill, so the route is bordered on both sides
- The casing SHALL contrast with light map backgrounds (white residential roads, light land) by at least 3:1
- The route SHALL keep the daylight hue along its whole length, including where it overlaps a primary road

#### Scenario: Route over a primary road in daylight

- **WHEN** the map renders the daylight style variant and the active route runs along a primary road (daylight primary color `#f58b8b`)
- **THEN** the route is visually distinguishable from the primary road underneath
- **AND** the route's composited fill is at least 25 degrees away in hue from the primary road color

#### Scenario: Route over a white residential road in daylight

- **WHEN** the map renders the daylight style variant and the active route runs along a residential road (daylight color `#ffffff`)
- **THEN** the route's casing is visible as a border on both sides of the route fill
- **AND** the casing reaches at least 3:1 against the white road, so the route outline does not disappear into it

#### Scenario: Route fill is opaque in daylight

- **WHEN** the active route overlaps a road of any color in the daylight style variant
- **THEN** the road color underneath does not show through the route's rendered centre
- **AND** the centre colour is produced by the route's own fill over its opaque casing, so it is identical for every road colour beneath

#### Scenario: Route is distinguishable from the imported GPX track

- **WHEN** the imported GPX track and the active route are visible at the same time
- **THEN** the route fill and the imported track color are different hues and the two polylines are distinguishable

### Requirement: Every map style draws the route with the shared route colors

Every bundled map stylesheet that draws the active route SHALL draw it through the shared route rule, so a user switching map style keeps the same route colors.

- A selectable style SHALL NOT carry its own route fill or casing colors
- A style that draws the active route SHALL draw it with the same fill and casing colors as every other such style for the same presentation

#### Scenario: Switching map style keeps the route colors

- **WHEN** a route is active and the user switches the map style to another style that draws a route
- **THEN** the route keeps the shared daylight fill and casing colors
- **AND** the route does not fall back to a style-specific color

#### Scenario: Non-default styles have a route casing

- **WHEN** the map renders a daylight variant of a non-default style that draws a route
- **THEN** the route is drawn with the shared fill and the shared opaque casing, not as a semi-transparent single-color polyline without a casing

## ADDED Requirements

### Requirement: Daylight route keeps street labels legible

In light presentation the composited colour of the active route fill SHALL keep enough contrast against the stylesheet's way label colour for a street name drawn on the route to stay readable. This is required because the renderer draws way labels after way fills, so a label lying on the route is painted on top of the route fill, and street `WAY.TEXT` carries no halo.

- The contrast between the daylight way label colour and the route's composited fill SHALL be at least 4.5:1
- The contrast between the way label colour and the route's casing colour need not meet that threshold, because way labels are laid along the way's centreline, whose displayed width exceeds the casing band
- The requirement holds along the route's whole length and on every surface that draws the route

#### Scenario: Street label on the route in daylight

- **WHEN** the map renders the daylight style variant and a way label is drawn along a road the active route covers
- **THEN** the label colour reaches at least 4.5:1 against the route's composited fill
- **AND** the label is readable without the label style gaining a halo

#### Scenario: Label contrast holds where the route crosses a dark road

- **WHEN** the active route crosses or follows a road whose fill is itself dark in daylight
- **THEN** the label's contrast against the route's composited fill is unchanged, because that colour does not depend on the road underneath

#### Scenario: Both surfaces show the same label legibility

- **WHEN** the phone map and the Android Auto map are both in daylight presentation with the same active route and the same stylesheet
- **THEN** both draw the route with the same composited fill and neither surface overrides the route colours

### Requirement: Translucent daylight route keeps a readable edge

Where the daylight route fill is translucent and lies over its opaque casing, the resulting centre colour SHALL stay distinguishable from the casing, so the casing still reads as a border rather than being swallowed by the fill.

- The composited centre SHALL be lighter than the casing
- The centre SHALL be at least 1.25:1 against the casing
- The route's rendered width and the fill-to-casing width ratio SHALL be unchanged by the fill's translucency

#### Scenario: Route centre reads as distinct from its casing

- **WHEN** the map renders the daylight style variant with an active route whose fill is translucent
- **THEN** the composited centre colour is lighter than the casing
- **AND** the centre reaches at least 1.25:1 against the casing

#### Scenario: Route width unchanged by translucency

- **WHEN** the route is drawn with a translucent fill and an opaque casing
- **THEN** the route's rendered width and the ratio between the casing width and the fill width are the same as before the fill became translucent
