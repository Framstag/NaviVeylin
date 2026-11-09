# Spec Delta — auto/free-driving

## MODIFIED Requirements

### Requirement: Free driving entered from map menu

The system SHALL provide a "Free driving" entry in the Android Auto map menu that opens a full-screen
navigation-style view without a destination. While a frame lands, the view SHALL be a full-screen
`NavigationTemplate` with no `NavigationInfo` and no content slot; while the renderer's frame-wait budget is
spent with no frame landed, the view SHALL state that the map is unavailable in the host content slot
instead, and SHALL return to the content-slotless template on the first frame that lands.

#### Scenario: Free driving opened from map menu

- **WHEN** the user selects "Free driving" in the map menu content on the Android Auto map screen
- **THEN** the system shows a full-screen free-driving view
- **Case**: `MapTemplateFactoryTest#freeDrivingRowInvokesCallback`

#### Scenario: Free driving view fills the surface

- **WHEN** the free-driving view is shown and a frame of it has landed
- **THEN** the system renders a full-screen `NavigationTemplate` with no `NavigationInfo` and no content
  slot, so no host-rendered panel/box overlays the map (`MapWithContentTemplate`'s mandatory content slot
  showed a "Free driving" box; `MapTemplate`'s required item list showed a "No items" placeholder). The map
  surface is drawn by the app; the earlier belief that the emulator host locks the `NavigationTemplate`
  surface predates the renderer fixes (the old `drawToSurface` had no `finally`-unlock, so one draw exception
  leaked the lock and every later `lockCanvas` threw "already locked"). The renderer detects a dead surface
  (`isValid` check + failed `lockCanvas`), stops hammering it, releases it per the car-app API contract, and
  asks the host for a fresh one (capped `invalidate`). All indicators (compass rose, speed, limit sign,
  street name) are drawn on the surface by the app; the host draws its own compass (cosmetic overlap
  accepted)
- **Case**: `FreeDrivingScreenHostTest#theTemplateIsTheBareNavigationTemplateCarryingItsActions`

#### Scenario: A free-driving view with no frame states that the map is unavailable

- **GIVEN** a started free-driving screen whose requested frames were never drawn, so the frame-wait budget
  is spent (`AutoMapRenderer.frameWaitExhausted`, `FRAME_WAIT_BOUND_MS` = 4 000 ms)
- **WHEN** the host asks the screen for its template
- **THEN** the screen SHALL answer `MapWithContentTemplate` carrying one disabled `map_unavailable` row in
  its content slot and the strips it already had, because the surface's only channel for that notice runs
  inside a frame the screen never drew
- **AND** the first frame that lands SHALL return it to the `NavigationTemplate` above
- **Case**: `FreeDrivingScreenHostTest#aScreenWhoseFrameWaitBudgetIsSpentCarriesTheNoticeInTheHostSlot`

#### Scenario: Compass rose centered above speed readout

- **WHEN** the free-driving view is visible
- **THEN** the compass rose drawn on the map surface is horizontally centered above the speed readout, with a
  gap from the right map edge
- **Case**: `SurfaceIndicatorsTest#roseCenteredAboveSpeedBadge`

#### Scenario: Free driving does not start turn-by-turn navigation

- **WHEN** the user selects "Free driving"
- **THEN** the system does not change the active navigation state (`isNavigating` remains false) and no route
  is computed
- **Case**: none — the screen is constructed without a `NavigationViewModel` at all
  (`FreeDrivingScreen(carContext, resolvedDark)`, `FreeDrivingScreenHostTest#newScreen`), so the navigation
  state machine is structurally unreachable from it rather than asserted; the entry the view does make is
  covered by `FreeDrivingScreenHostTest#anEnteredScreenVotesTheFreeDrivingFlagOn`.
