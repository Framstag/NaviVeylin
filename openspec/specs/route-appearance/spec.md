# route-appearance Specification

## Purpose

Defines how the active route polyline must look on the map so that it stays clearly distinguishable from the road classes it is drawn on, in both light and dark presentation, on every surface that renders the same map.

## Requirements

### Requirement: Active route stands out from road classes in daylight

In light presentation the active route polyline SHALL be drawn with an opaque fill and a contrasting casing (border) so that it is distinguishable from every road class of the active daylight stylesheet, including primary roads.

- The daylight fill SHALL be opaque: the road color underneath SHALL NOT blend through the route
- The daylight fill hue SHALL NOT be the hue of any road class of the daylight stylesheet (motorway, trunk, primary, secondary, tertiary, residential/road)
- The casing SHALL be drawn as a wider stroke under the fill, so the route is bordered on both sides
- The casing SHALL contrast with the daylight fill and with light map backgrounds (white residential roads, light land)
- The route SHALL keep the daylight hue along its whole length, including where it overlaps a primary road

#### Scenario: Route over a primary road in daylight

- **WHEN** the map renders the daylight style variant and the active route runs along a primary road (daylight primary color `#ec4044`)
- **THEN** the route is visually distinguishable from the primary road underneath
- **AND** the route's fill color is not the primary road color and is not a blend of it with a transparent fill

#### Scenario: Route over a white residential road in daylight

- **WHEN** the map renders the daylight style variant and the active route runs along a residential road (daylight color `#ffffff`)
- **THEN** the route's casing is visible as a border on both sides of the route fill
- **AND** the route outline does not disappear into the white road

#### Scenario: Route fill is opaque in daylight

- **WHEN** the active route overlaps a road of any color in the daylight style variant
- **THEN** the road color underneath does not show through the route fill

#### Scenario: Route is distinguishable from the imported GPX track

- **WHEN** the imported GPX track and the active route are visible at the same time
- **THEN** the route fill and the imported track color are different hues and the two polylines are distinguishable

### Requirement: Route keeps its established colors in dark presentation

In dark presentation the active route SHALL keep its established appearance: red fill with a white casing. The dark presentation SHALL NOT be changed by the daylight color rules.

#### Scenario: Route in dark presentation

- **WHEN** the map renders the dark style variant with an active route
- **THEN** the route fill is red
- **AND** the route casing is white

#### Scenario: Daylight and dark route differ

- **WHEN** the same active route is shown once in daylight and once in dark presentation
- **THEN** the route fill and casing colors differ between the two variants
- **AND** each variant satisfies its own contrast rules

### Requirement: Both surfaces draw the route with the same colors

The phone map and the Android Auto map SHALL draw the active route with identical fill and casing colors for the same presentation, since both render through the same stylesheet and select the day/night variant through the same stylesheet flag.

- The presentation variant SHALL be selected by the resolved day/night state that the surface already pushes to the renderer
- No surface SHALL override the route colors independently (no per-surface route palette)

#### Scenario: Phone and car route colors match

- **WHEN** the phone map and the Android Auto map are both in daylight presentation with the same route active
- **THEN** both draw the route with the same fill and casing colors

#### Scenario: Car night route matches the phone night route

- **WHEN** the Android Auto host reports night state and the phone is in dark presentation
- **THEN** both draw the route with the same red fill and white casing

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
- **THEN** the route is drawn with the shared opaque fill and the dark casing, not as a semi-transparent single-color polyline

### Requirement: Route appearance change alters no geometry or cost

Changing the route colors SHALL NOT change the route's rendered width, its priority relative to other map objects, its geometry, or the number of render passes needed to draw the map.

#### Scenario: Route width unchanged

- **WHEN** the route is drawn before and after the color change
- **THEN** the route's rendered width and its drawing priority class are the same

#### Scenario: No extra render pass

- **WHEN** a map frame is rendered with an active route
- **THEN** the route is still drawn in the single existing render pass, adding neither a second route draw nor a post-render overlay pass
