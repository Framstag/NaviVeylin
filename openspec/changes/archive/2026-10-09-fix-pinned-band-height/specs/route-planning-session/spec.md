# Spec Delta

## MODIFIED Requirements

### Requirement: Planning card content and its pinned actions

In **max** the card SHALL carry the location fields, the route's step list (scrolling inside the card) and the session actions, and in **min** only the analysed step, its position "i of n", the two step controls and the session's End control (min renders no header, so its exit lives in this row), with the step name as the way back to max. The session actions SHALL stay reachable without scrolling: in max they SHALL be pinned in their own band at the card's bottom edge, and two actions of the same state SHALL share one row. The pinned band SHALL take the height its own content needs at the **current font scale** and the card's scrolling content SHALL give that space up — a fixed reservation for the band squeezed the labelled session action once a large system font scale grew the band's labels (font scale 2.0: measured on the device as the action leaving the card entirely, and on the host as the action 42.7 dp tall where its own content needs 53.3 dp — `TODO.md` §138). While a route is reviewed the two location fields SHALL collapse into one read-only line (tapping it opens the fields for editing), so the route's list starts on screen; the vehicle selector is a pre-calculation control and SHALL give its room to the list once a route exists. The overlay SHALL start a route on its **current step**, the first step the native layer gave a distance or a time for: the start line carries neither, and presenting it left the min overlay's instruction detail empty (owner finding, 2026-10-03). The secondary clear action SHALL keep a row of its own only in the docked layout.

#### Scenario: The actions stay reachable

- **WHEN** a route is calculated and the card is at either anchor
- **THEN** the primary session action SHALL be visible without scrolling the card
- **AND** the card's other content SHALL scroll above it

#### Scenario: The route line gives way to editing

- **WHEN** a route is reviewed and the card is in max
- **THEN** the start and destination SHALL appear as one read-only line
- **AND** the route's step list SHALL start on screen
- **WHEN** the user taps that line
- **THEN** the editable fields SHALL appear with the destination field focused
- **AND** the edit state SHALL stay open until the field loses focus

#### Scenario: The card reports the height it has

- **WHEN** the card is in max or min
- **THEN** it SHALL report the covered pixel height it actually occupies to the map screen
- **AND** the route overview fit and the right-side control column's inset SHALL use that number
- **Case** `com.naviveylin.ui.route.RoutePanelOverlayAnchorTest#the card reports the covered height it occupies` (the report), `com.naviveylin.ui.map.MapCanvasViewModelRouteFitTest#reportedCardHeight_fitUsesTheFreeMapArea` and `com.naviveylin.ui.map.MapLayerStackComposeTest#the right-side control column sits fully above the session card` (the fit and the inset using it); the screen's wiring between them is the device task 5.1

#### Scenario: The overlay never covers the map controls

- **WHEN** the session's card is in max or min on the phone
- **THEN** the right-side control column (compass, speed, location options, zoom) SHALL sit fully above the card
- **AND** its stack order and spacing SHALL be unchanged
- **Case** `com.naviveylin.ui.map.MapLayerStackComposeTest#the right-side control column sits fully above the session card` (the disjointness, with the screen's inset wiring reproduced host-side), `com.naviveylin.ui.map.MapRightWidgetColumnTest#standardColumnShowsCompassSpeedGearAndZoom` and `#zoomSitsBelowAllOtherControls` (the order and the spacing)

#### Scenario: The pinned band keeps the height its content needs

- **WHEN** the card is in max on a calculated route and the system font scale is 2.0
- **THEN** the labelled session action SHALL keep its own height — at least its 48 dp tap target — and its bounds SHALL lie inside the card
- **AND** the card's scrolling content SHALL give up that space instead of the band
- **AND** the card SHALL stay within its 45 % share of the screen height
- **Case** `com.naviveylin.ui.route.RoutePanelActionBandScaleTest#the labelled End action keeps its tap target inside the card at font scale 2`
