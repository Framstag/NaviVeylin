# Spec Delta

## ADDED Requirements

### Requirement: The route-calculation strip has one place on the navigation surface

The app-drawn strip that reports a route calculation in progress SHALL be drawn inside the host-reported stable area when one is known, along the surface's top edge, clear of the right-edge visualisation column (compass rose, speed badge, speed-limit sign) and clear of the attribution. It SHALL take the palette of the presentation actually applied to that surface, and it SHALL NOT be an interactive control: no host gesture reaches it.

#### Scenario: The strip sits inside the host's stable area

- **WHEN** the host has delivered a stable area for the navigation surface
- **THEN** the strip SHALL be drawn entirely inside it
- **Case**: `auto: CalculationStripTest.theStripSitsInsideTheStableArea`

#### Scenario: The strip clears the existing surface elements

- **WHEN** the strip, the compass rose, the speed badge, the speed-limit sign and the attribution are all drawn
- **THEN** the strip SHALL NOT overlap any of them
- **Case**: `auto: CalculationStripTest.theStripClearsTheIndicatorColumnAndTheAttribution`

#### Scenario: The strip follows the applied presentation

- **WHEN** the surface renders the dark variant and the strip is drawn
- **THEN** the strip SHALL use the dark presentation's palette
- **AND** the same strip SHALL use the daylight palette on a surface rendering the daylight variant
- **Case**: `auto: CalculationStripTest.theStripPaletteFollowsTheAppliedPresentation`

#### Scenario: The strip is not a control

- **WHEN** the driver pans or taps over the strip's area
- **THEN** the surface SHALL behave as it does over the rest of the map, and the strip SHALL NOT be treated as a tappable element
- **Case**: `auto: CalculationStripTest.theStripInstallsNoGestureHandling`
