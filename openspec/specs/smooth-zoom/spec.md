# Smooth Zoom Specification

## Purpose

Make zooming visually continuous by easing the map display between the start and end zoom levels whenever the magnification changes without an active pinch gesture, instead of snapping the front buffer from one rendered zoom level to the next.

## Requirements

### Requirement: Eased zoom animation on discrete zoom input

The system SHALL animate the displayed map from the current displayed scale toward the target magnification whenever the magnification changes without an active pinch gesture — discrete zoom input (zoom buttons, scroll wheel, keyboard plus/minus) as well as speed-driven auto-zoom commits during follow mode. The zoom animation SHALL scale the previously rendered front buffer with an ease-out curve; discrete input animates over approximately 200-300 ms, while auto-zoom commits animate over approximately 650 ms (slower, matching the driving zoom cadence so the easing does not read as pumping). The requested target magnification SHALL be recorded immediately when the animation starts; the displayed magnification reaches it across the transition.

The displayed frame SHALL NOT show a magnification farther than the window the frame in hand can serve (its overrun margin): a magnification change larger than that window SHALL be applied as a sequence of displayed steps, each step served by a native render at the magnification that step displays, with the last step landing exactly on the requested magnification. A change inside the serviceable window SHALL keep the single eased scale of one already-rendered frame.

#### Scenario: Zoom in button triggers animated transition

- **WHEN** the user taps the zoom in button while no pinch gesture is active
- **THEN** the displayed map SHALL scale up smoothly from the currently rendered magnification toward the target magnification over ~250 ms with an ease-out curve
- **AND** no single-frame jump between zoom levels SHALL be visible

#### Scenario: Scroll wheel zoom animates

- **WHEN** the user zooms with the scroll wheel
- **THEN** the displayed map SHALL animate toward the target magnification with an eased scale
- **AND** repeated wheel ticks during the animation SHALL be handled without visual snapping

#### Scenario: Auto-zoom commit animates at the slower duration

- **GIVEN** follow mode and auto-zoom are active and the vehicle is centered on screen
- **WHEN** the speed-derived auto-zoom commits a new fractional magnification
- **THEN** the displayed map SHALL ease from the currently rendered scale to the target magnification over approximately 650 ms with an ease-out curve
- **AND** the commit SHALL NOT appear as a single-frame jump in map content

#### Scenario: Magnification change larger than the serviceable window is applied across rendered frames

- **GIVEN** follow mode is active, the front buffer was rendered at magnification 13.0, and the window that frame can serve covers 0.25 magnification levels
- **WHEN** auto-zoom commits magnification 17.0
- **THEN** no displayed frame SHALL show a magnification farther than 0.25 levels from the magnification that frame was rendered at
- **AND** every step of the transition SHALL be served by a native render at the magnification it displays
- **AND** the last displayed frame SHALL be a native render at magnification 17.0
- **AND** the displayed map content SHALL show the geometry, road widths and label sizes of the magnification it shows, not of magnification 13.0 resampled

#### Scenario: Change inside the serviceable window keeps the single eased scale

- **WHEN** a committed magnification differs from the rendered one by less than the window that frame can serve
- **THEN** the display SHALL ease the rendered frame toward the committed magnification
- **AND** no additional native render SHALL be required for the transition

#### Scenario: Target magnification is recorded while the displayed scale still eases

- **WHEN** a zoom animation starts and the displayed scale has not yet reached the target
- **THEN** the recorded viewport magnification SHALL already be the target magnification
- **AND** the displayed scale SHALL NOT be recorded

### Requirement: Geographic anchor stays fixed during zoom animation

The geographical point under the zoom focal point SHALL be kept visually fixed: discrete zoom input via zoom buttons or keyboard SHALL anchor the screen center; scroll wheel input SHALL anchor the cursor position. While a follow mode is active, the point kept visually fixed SHALL be the vehicle marker's resolved follow anchor position on the map surface instead of the screen center, for every anchor preset, and it SHALL stay fixed for every frame of the animation.

#### Scenario: Button zoom keeps map center fixed

- **WHEN** the user taps a zoom button
- **THEN** the geographic point at the screen center SHALL remain at the same screen pixel for every frame of the animation

#### Scenario: Scroll wheel zoom keeps cursor point fixed

- **WHEN** the user zooms with the scroll wheel while the cursor is over the map
- **THEN** the geographic point under the cursor SHALL remain under the cursor for every frame of the animation

#### Scenario: Follow mode with an off-center anchor keeps the vehicle fixed

- **GIVEN** follow mode is active and the resolved follow anchor is bottom-center, so the vehicle marker is rendered below the surface center
- **WHEN** auto-zoom commits a zoom-in
- **THEN** the vehicle marker SHALL occupy the same screen pixel for every frame of the animation
- **AND** the map content under the marker SHALL NOT shift relative to the marker
- **AND** no correction jump of the vehicle marker or the map content SHALL appear when the frame at the target magnification lands

#### Scenario: Follow mode anchors discrete zoom input on the vehicle

- **GIVEN** follow mode is active with a resolved follow anchor other than the surface center
- **WHEN** the user taps a zoom button or uses the keyboard shortcut
- **THEN** the vehicle marker's screen pixel SHALL remain unchanged for every frame of the animation
- **AND** the map SHALL NOT ease around the surface center

### Requirement: Retracking on rapid zoom input

While a zoom animation is running, further zoom input SHALL retrack the running animation smoothly from the currently displayed scale toward the new target magnification. The system SHALL NOT snap back to the start scale of the previous animation. A stepped magnification change SHALL retrack through this rule: the render of the next step retracks the running animation toward that step's magnification.

#### Scenario: Zoom out tapped during zoom in animation

- **WHEN** a zoom in animation is halfway to its target and the user taps the zoom out button
- **THEN** the displayed scale SHALL smoothly reverse from its current value toward the new target magnification
- **AND** the animation SHALL NOT jump back to the zoom in animation's start scale

#### Scenario: Repeated scroll wheel zoom

- **WHEN** the user zooms with the scroll wheel several times in quick succession
- **THEN** each tick SHALL retrack the running animation toward the new target without a snap

#### Scenario: Consecutive auto-zoom commits retrack

- **GIVEN** an auto-zoom animation is running toward a slower-speed target (zooming in)
- **WHEN** the vehicle accelerates and auto-zoom commits a new lower target while the animation is still in progress
- **THEN** the animation SHALL retrack from the currently displayed scale toward the new target
- **AND** the display SHALL NOT snap back to the previous animation's start scale

#### Scenario: Stepped magnification change retracks per step

- **GIVEN** a magnification change is being applied as a sequence of rendered steps and the current step's easing is still in progress
- **WHEN** the next step's render lands
- **THEN** the displayed scale SHALL retrack from its current value toward the next step's magnification
- **AND** the display SHALL NOT snap back to the first step's start scale

### Requirement: Animation continues until native render completes

The system SHALL run the zoom animation independent of the native render pipeline. The debounced native render SHALL be queued while the animation plays. When the render at the target magnification completes, the overlay phase of the animation SHALL end and the rendered frame SHALL be shown as specified in the zoom-transition-scaling capability. If the render completes before the animation finishes, the rendered frame SHALL be displayed immediately at the target magnification.

#### Scenario: Render completes before animation ends

- **WHEN** the native render at the target magnification finishes while the zoom animation is still in progress
- **THEN** the rendered frame SHALL be shown at the exact target magnification from that point on
- **AND** the animation SHALL no longer scale the rendered frame

#### Scenario: Render completes after animation ends

- **WHEN** the zoom animation finishes before the native render completes
- **THEN** the display SHALL hold the front buffer at the target scale until the render completes
- **AND** the render completion SHALL replace the display as specified in the zoom-transition-scaling capability

### Requirement: Zoom animation composes with follow mode

The system SHALL compose the zoom animation with the smooth-follow display extrapolation. While both are active, the zoom animation SHALL scale the front buffer and the follow-mode display loop SHALL pan it; neither SHALL disable the other.

#### Scenario: Follow mode active during button zoom

- **WHEN** follow mode is engaged and the user taps a zoom button
- **THEN** the display SHALL animate the zoom toward the target magnification
- **AND** the follow-mode extrapolation SHALL continue offsetting the displayed center during the animation

#### Scenario: Follow mode not interrupted by zoom animation

- **WHEN** a zoom animation completes while follow mode is engaged
- **THEN** follow-mode extrapolation SHALL continue without a jump in the displayed position

### Requirement: Viewport records final magnification

The system SHALL persist the viewport with the target magnification of the zoom animation during and after the animation. The animated displayed scale SHALL NOT be written to the persisted viewport.

#### Scenario: App paused during animation

- **WHEN** the app is paused while a zoom animation is running
- **THEN** the persisted viewport magnification SHALL be the animation's target magnification or the currently rendered magnification, never an intermediate animated scale

### Requirement: Zoom animation never exposes uncovered map area

The displayed frame SHALL cover the whole map surface for every frame of a zoom animation. The displayed scale SHALL stay inside the region the frame in hand actually covers, or the displayed frame SHALL be a native render at the committed magnification.

#### Scenario: Large zoom-out does not expose surface background

- **GIVEN** the front buffer covers the surface with the margin of the overrun buffer
- **WHEN** the committed magnification is lower than the rendered one by more than that margin can serve
- **THEN** no frame of the animation SHALL show a strip of bare surface background at any edge
- **AND** the display SHALL either step through rendered frames or show a native render at the committed magnification

#### Scenario: Zoom-in keeps full coverage

- **WHEN** the animation zooms in
- **THEN** the displayed bitmap SHALL cover the whole surface at every frame

### Requirement: The render pin lives only as long as the walk

The magnification walk SHALL pin the magnification the render pipeline uses only while that walk is pending. A magnification committed by any other path — a camera fit, a mode preset, a re-center — SHALL reach the render pipeline while no walk is pending, without that path having to end a walk first: the next frame SHALL be rendered at the committed magnification. A walk that has landed SHALL NOT keep a render magnification of its own.

#### Scenario: A landed walk does not pin a later magnification

- **WHEN** a walk has landed at magnification A and no walk is pending
- **AND** another path then commits magnification B
- **THEN** the magnification the render pipeline uses SHALL be B
- **AND** the magnification of the landed walk SHALL NOT be used again
- **Case**: `core: ZoomWalkTest` "a landed walk does not pin a later committed magnification"

#### Scenario: A pending walk keeps its step

- **WHEN** a walk is pending and the frame of its current step has not landed yet
- **THEN** the magnification the render pipeline uses SHALL be that step
- **AND** it SHALL NOT be the magnification the viewport records for the walk's target
- **Case**: `core: ZoomWalkTest` "a pending walk still pins its step"

#### Scenario: A camera fit committed while no walk is pending is rendered at the fit's magnification

- **GIVEN** the map has rendered the last frame of a completed walk
- **WHEN** the route overview fit commits its magnification while no walk is pending
- **THEN** the next frame SHALL be rendered at the fit's magnification
- **AND** the fitted route SHALL be inside the frame
- **Case**: `app: MapCanvasViewModelRouteFitTest.fitAfterALandedWalkIsRenderedAtTheFitMagnification`

### Requirement: Double tap is the map's second discrete zoom-in input

The map canvas SHALL report a double tap — two taps inside the platform's double-tap window and touch slop, on
one point, while no pinch gesture is active — as a discrete zoom-in input beside the scroll wheel and the zoom
buttons. A drag, a pinch or a long press SHALL consume a pending tap, so it cannot pair with an earlier tap.
Two taps separated by more than the platform's double-tap window SHALL be two single taps and SHALL NOT be a
double tap.

#### Scenario: Two taps inside the platform's window are one double tap

- **WHEN** two taps land on the same point inside the platform's double-tap window and touch slop
- **THEN** the canvas SHALL report exactly one double tap, at the point the second tap lifted
- **AND** the taps SHALL NOT read as a long press, a pan or a pinch
- **Case**: `app: MapGestureComposeTest` "twoTapsInsideTheDoubleTapWindowReportOneDoubleTap"

#### Scenario: Taps beyond the platform's window are two single taps

- **WHEN** two taps on the same point are separated by more than the platform's double-tap window
- **THEN** no double tap SHALL be reported, so no zoom step is committed
- **Case**: `app: MapGestureComposeTest` "twoTapsBeyondTheDoubleTapWindowReportNoDoubleTap"

### Requirement: A double tap steps one level at its own point

A double tap SHALL commit the next whole magnification level above the level the viewport records — the snapped
step the zoom-in button takes, so a fractional level a pinch left behind commits the next integer one — never
above the maximum magnification, and at the maximum it SHALL change neither the viewport nor the display. The
geographic point under the tap point SHALL stay at the same screen pixel for every frame of the eased
transition, which SHALL retrack from the currently displayed scale. The step SHALL go through the viewport
commit a zoom-button press uses, so it is a manual zoom (spec: `map-modes`).

#### Scenario: A double tap steps one level around the tap point

- **WHEN** the user double-taps the map canvas at a point, below the maximum magnification
- **THEN** the recorded magnification SHALL be the next whole level above the recorded level (a recorded `10.4`
  commits `11`, a recorded `10.6` commits `12`, as the zoom-in button does)
- **AND** the geographic point under the tap point SHALL be at the same screen pixel after the commit
- **AND** the displayed map SHALL ease from the currently rendered scale to the new level around that pixel
- **Case**: `app: MapCanvasDoubleTapZoomTest` "double tap steps one level and keeps the tap point fixed"

#### Scenario: A double tap at the maximum magnification changes nothing

- **GIVEN** the magnification is at the maximum
- **WHEN** the user double-taps the map canvas
- **THEN** the recorded magnification SHALL stay at the maximum
- **AND** the viewport centre SHALL NOT move
- **AND** no zoom transition SHALL start
- **Case**: `app: MapCanvasDoubleTapZoomTest` "double tap at the maximum magnification leaves the viewport alone"
