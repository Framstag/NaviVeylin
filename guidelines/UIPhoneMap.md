# Phone map UI Guidelines

Rules and constraints for the phone/tablet app's map and the surfaces that open over its
canvas — map modes, the re-center control, the route-planning card, overlay sizing and
layering, and the surface that must live while a car session is active.

This document carries the phone **map** sections of the former `UIPhone.md` under their own
numbers. The phone's own surfaces — the details dialog, the search dialog and the favourites
affordances — are `guidelines/UIPhone.md`.

**Read the section that owns your topic, not this whole document.** The section list is
`grep -n '^## ' guidelines/UIPhoneMap.md`; AGENTS.md's Documentation Map routes a kind of
change here.

---

## 7. Phone map modes (Browse / Free drive / Navigation)

Source: spec `map-modes`.

- **Rule: the phone map has three modes derived from one source of truth.** The phone map has an explicit mode model with three states, derived from a
  single source of truth (`MapMode` in `MapCanvasViewModel`):
  - **BROWSE** — follow off, north-up, last persisted viewport. The app
    ALWAYS starts in BROWSE; `followMode` is runtime state, never restored
    from settings (free drive is per-session intent).
  - **FREE_DRIVE** — follow on, auto-zoom on, heading-up, speed-based driving
    zoom. Entered with one tap on the compass button (short press).
  - **NAVIGATION** — route active; overrides follow mode. On navigation end
    the map returns to the mode active before navigation started.
- **Rule: the drive-mode toggle is a dedicated button below the compass.** The drive mode toggle is a dedicated button in the right-side widget column
  directly below the compass: car icon in BROWSE (tap → FREE_DRIVE), exit icon
  in FREE_DRIVE (tap → BROWSE). Hidden during NAVIGATION. The compass button
  keeps its orientation role; its short press re-centers via the mode-dependent
  re-center action.
- **Rule: exiting FREE_DRIVE stays at the current position.** Exiting FREE_DRIVE stays at the current position (follow off, north-up).
- **Rule: the location-options sheet never switches modes.** The sheet shows a header naming the current state and that
  state's options only — it never switches modes. BROWSE shows orientation;
  FREE_DRIVE and NAVIGATION share the driving section (auto-zoom + driving
  orientation). The sheet is reachable during navigation (gear in the nav
  right column).

## 7a. Phone map re-center button (per-mode)

Source: spec `map-modes` (drive suspension and reset; Browse re-center), spec `map-recenter-button`
(the control: icon, GPS-fix gate, placement).

- **Rule: the button appears only when a GPS fix is available and the map needs it.** The re-center
  button (crosshair/my-location icon, content description "Re-center on location") appears only when a
  GPS fix is available **and** the map needs it:
  - **FREE_DRIVE** — any manual pan/zoom/rotate suspends the drive preset (`driveSuspended`); the
    button appears and tapping it resets to the standard drive values (follow on, auto-zoom on,
    heading-up, driving zoom) and hides the button.
  - **BROWSE** — the button follows the **measured** offset between the map center and the vehicle,
    never a remembered interaction. It appears when the vehicle is more than the appear threshold off
    the center (screen pixels, so the value means the same visible displacement at every zoom) and has
    stayed beyond it for a short dwell; it hides below the smaller hide threshold, immediately.
  - **NAVIGATION** — a manual zoom suspends auto-zoom; tapping re-centers on the current position.
- **Rule: the BROWSE dwell completes on elapsed time, not on the next state change.** It is armed by
  the evaluation that first sees the offset, and the shared re-evaluation tick reveals the button once
  the dwell has run out, so the button appears with no further fix, pan or zoom — the standstill case
  the rule exists for. A pending dwell is discarded when the mode leaves BROWSE or the vehicle comes
  back inside the hide threshold, including an available fix that stops being usable (aged past the
  fix limit, or the location source switched off). Rotation about the center does not change the
  offset and therefore does not change its visibility. Tapping it puts the current GPS position at the
  center of the map, stays in BROWSE, and hides the button.
- **Rule: the BROWSE offset is derived from observed state.** The canvas metrics are part of the
  observed state the rule is derived from — published by the screen's first `onSizeChanged`, exactly
  like the viewport — so the first layout, a rotation or an unfold re-derives the offset and arms the
  dwell just as a fix or a pan does.
- **Rule: BROWSE framing is not anchor-framed.** The "Vehicle position" presets configure the driving
  modes only (`UIPhone.md` §17); an off-center browse re-center would immediately re-show the button the
  user just dismissed.
- **Rule: the button's placement keeps it clear of the covered bottom edge.** Bottom-left in
  BROWSE/FREE_DRIVE; while navigating it is anchored directly above the routing status bar, because
  the screen-bottom area is covered by `NavigationStateOverlay`.
- **Rule: there is no automatic re-engage.** A manual interaction stops follow/auto-zoom in the driving
  modes until the driver taps the button. In BROWSE the map never follows, so the button is the
  standing "go to my position" offer and returns as soon as the vehicle is off the center again.
- **Rule: the re-center button is phone-only.** The car display has its own follow behavior via the car
  MapController and is unaffected (no phone-style auto zoom).

The derivations behind the dwell, the thresholds and the offset's source (and why the standstill case
needs the tick) are `UIPhone.why.md` §7a.

## 7b. Route planning holds the camera (change `route-planning-session`)

Source: spec `route-planning-session`.

- **Rule: while a route-planning session is open, the session owns the viewport.** Opening it suspends
  the FREE_DRIVE preset (the same suspension a manual interaction causes), a position fix moves
  nothing, and a follow re-engage (`onToggleFollowMode(true)`) is refused until the session ends. The
  mode stays BROWSE or FREE_DRIVE as derived — there is no fourth mode. Ending the session leaves the
  preset suspended, so the re-center button is what restores the standard drive values.
- **Rule: the session's own camera moves are the only ones while it is open.** The route-overview fit
  for the height the card reports (re-run when that height changes while a route is reviewed) and the
  analysed step's manoeuvre are the only camera moves; the route-overview suppression while
  navigating/following still applies.
- **Rule: the phone card has two fixed heights, max and min.** **Max** (at most 45 % of the screen
  height) is the full card: location fields, the route's **step list** (scrolling inside the card), the
  vehicle selector while no route is calculated, and the pinned actions; the map keeps at least 55 %
  for the route. **Min** (at most 18 %, one control row, hugging its content) is only the analysed
  step — its instruction, "i / n" and the two step controls, with the step name as the way back to max.
  Without a route the card stays in max. A session that should free the map is **ended**, not hidden:
  ending returns the covered height to zero and hands the map back.
- **Rule: the card is the phone's only session surface.** No summary dialog, no hidden anchor and no
  route-ready pill — the third anchor and its pill were removed, because the pill was a dead end (the
  history is `UIPhone.why.md` §7b).
- **Rule: the card's controls are one glyph, one meaning.** The **`^`/`v` toggle** collapses to min and
  expands back, and the **`X` close control ends the session** in both anchors — in max beside the
  labelled `End analysis` action, in min as the step row's close control (min renders no header).
  Pressing the close never leaves a smaller surface behind: the card and the session end together. In
  the docked wide panel the anchor toggle has no effect on the panel's size.
- **Rule: selecting a step analyses it and collapses the card to min.** Tapping the step name in min
  brings the list back with the analysed step unchanged. While a field is being edited the card takes
  the max height, because the search results need the room.
- **Rule: the step navigator moves the analysed step exactly as a list-row tap does.** Its controls are
  disabled at the ends; with nothing analysed the indicator names no step. Wide layouts keep the side
  panel with the selectable list instead.
- **Rule: a step row's distance and duration are the values of the run that ends at its manoeuvre.**
  Its own step's leg plus the legs of every step the row absorbed, taken from the route's structured
  steps, formatted by the app with `formatDistanceNumber` + `formatStepDurationText` — never a native
  description's `[x km, y min]` text and never a value measured between two route nodes; the rows of a
  step list therefore add up to the route's total distance and duration.
- **Rule: the route list is derived from the route's structured steps, never from description text.**
  A *row* is not a step: the panel derives rows from `RouteEntry.steps` with one pure function
  (`routeRows` in `app/ui/route/RouteRows.kt`), so the start, the arrival, every turn, roundabout,
  motorway and street-name manoeuvre earns a row, consecutive straight-on turns collapse into one, and
  an `informative` step becomes data on the row it precedes instead of a row of its own. A row's text
  is rebuilt from the manoeuvre's own values through `:core` resources (`ManoeuvreLabels`) in the device
  language, and its glyph comes from the manoeuvre's kind and move (`navSymbolFor`) rather than from
  matching prose. A row **owns a polyline range, not a point**: it covers the vertices from the previous
  row's boundary up to its own, the first row starts at the route's first vertex and the last ends at
  the route's last, so a highlight has no gap where a merged step's manoeuvre sits inside the run. A
  route that carries no steps lists **no rows** and says so — nothing is parsed back into structure, and
  `RouteEntry.descriptions` is not read by the phone's route surfaces any more.
- **Rule: live guidance uses the same step model and the same wording.** The engine publishes
  `RouteStep[]` and a step ahead; the surface announces the manoeuvre with `core/guidanceStep`, and the
  overlay's second row is the following manoeuvre of that list when it lies within
  `core/HINT_WINDOW_METERS` (200 m) of the announced one. The published step's own `nextNext` is **not**
  read by a guidance surface: live guidance fills it with the next described route node, so a node hint
  would say "then in 10 m, some street" and would carry no roundabout exit. The next-turn overlay, the
  notification, the HUD, the car instruction panel and the car trip all word their manoeuvre from
  `core/ManoeuvreLabels` and draw their glyph from `turnTypeFor(step)`. The second row is that following
  manoeuvre (icon and wording of *that* manoeuvre, distance = the gap between the two), so a
  roundabout's exit ordinal is read before the ring is entered; a motorway change is drawn as a keep,
  never as a plain turn. The car's host next-step row is the same following manoeuvre with no distance
  window (`core/nextManoeuvreFrom`). The car route-description rows keep the *distance from the route
  start* (`distanceFromStart`); every live surface shows the *remaining* distance.
- **Rule: the analysis is the session's, not the route's.** The analysed step, the camera anchor that
  moves onto it and the highlighted segment live exactly as long as the session, so every exit ends all
  three — Start Navigation above all, and End while navigation runs — while the route itself stays
  drawn, because navigation owns it from Start Navigation on. A route that arrives with **no session
  open** carries no analysis at all: the panel publishes its geometry, steps and anchors, and no
  analysed step, so no later event of a drive re-lights a mark nobody selected.
- **Rule: the card's actions are pinned in their own band at the bottom edge of max.** The two actions
  of a state share one row, and that band's height **is the band's own content at the current font
  scale**, never a constant reservation: the band is measured first and the scrolling content above it
  yields the space (`Modifier.weight(1f, fill = false)` on the scroll region, so the card still hugs a
  short route and still stops at its cap). Reviewing a route, that row is **`Start/Ziel ändern` +
  `Navigation starten`**, and while a field is being edited the first slot becomes `Berechnen` again.
  The clear action keeps a row of its own only in the docked panel — on the phone the session's cancel
  exit clears the route.
- **Rule: neither the card nor the panel nests a scroll.** Nested vertical scrolling is forbidden inside
  the card: a list rendered with its own scroll is measured with an infinite height — both the card and
  the docked panel scroll as a whole. The card sits at the screen's bottom edge
  (`BoxWithConstraints(fillMaxSize)` + `align(BottomCenter)`).
- **Rule: the right-side control column is inset by the height the card reports.** The phone's
  right-side control column (compass, speed, location options, zoom) is inset so it sits fully above the
  overlay in every state. The rule is one value: `MapCanvasUiState.overlayCoveredPx`, the same number
  the overview fit uses.

The measurements and owner findings behind the pinned band's height, the removed third anchor and the
right column's inset are `UIPhone.why.md` §7b.


## 8. Phone navigation overlay sizing (driver-seat readability)

Source: specs `map-speed-widget`, `compass-button`, `next-turn-overlay`.

- **Rule: every phone map overlay is readable from the driver seat.** All phone map overlays SHALL be
  readable from the driver seat; the minimums are current speed 24sp bold, speed-limit sign 64dp with
  28sp digits, turn distance 32sp bold, turn description/destination 22sp, and the next-next hint
  20sp (smaller than the primary instruction).
- **Rule: Android Auto sizes text via the host template.** Parity between the surfaces applies to
  labels and hierarchy, not to pixel sizes.

§8 is an index. Its sub-sections carry the rules under this number: the tap targets and the
navigation status card (§8b), the overlay controls and their density (§8c), and the cross-variant
map presentation (§8d). The measurements each bound was arrived at, the palettes' contrast
derivations and the history of the folds that produced them are `UIPhone.why.md` §8b and §8c, and — for
the cross-variant presentation — `UI.why.md` §8d.

## 8b. Tap targets and the navigation status card

Source: specs `navigation-status-details`, `route-planning-session`.

- **Rule: an overlay's control never sits inside its container's tap target.** The control's hit
  area and any surrounding "open the details" area SHALL be disjoint, and the control's hit area
  SHALL be at least 48 dp in each dimension. The control SHALL be the only node carrying a click
  action whose bounds overlap its own, and it SHALL carry its own label — the rule is held against
  **every** actionable target, not against the container's two detail regions (`UIPhone.why.md` §8b
  records the fold that made this the rule). A shared row that two hosts put a tap area on passes
  that area in as one parameter (`NavigationStatsRow.leadingModifier`) rather than duplicating the
  control per host.
- **Rule: progress is a ring around the value's glyph, never a row of its own.** The routing status
  card SHALL carry the driver's progress as a ring around the glyph of the value it belongs to — the
  remaining-distance glyph (travelled share of the route) and the remaining-time glyph (elapsed share
  of the estimated travel time), filled clockwise from twelve o'clock with the completed fraction —
  and SHALL NOT spend a row on progress lines.
- **Rule: the value slots are one fixed size and share one top edge.** The three value slots SHALL be
  one fixed size sharing one top edge (28 dp slot, 16 dp glyph, `NavStatSlot`/`NavStatGlyph`), so a
  value whose progress is unknown keeps its place instead of shifting the row.
- **Rule: the arc's stroke is one density-independent width.** The arc's stroke SHALL be a
  density-independent 3 dp (`NavStatRingStroke`) — the same rule as every other drawn dimension in
  this section.
- **Rule: the ring carries no progress semantics.** The glyph keeps the content description that
  names its value and the value itself is text in the same row; a `progressBarRangeInfo` would
  lengthen every TalkBack traversal with a percent the design deliberately does not print. The card
  never holds a stale ring: a reroute that lowers a percent moves the arc on the next
  navigation-state update.
- **Rule: the arrival-time glyph stays plain.** The arrival time SHALL keep a distinct plain glyph
  (`Icons.Default.Flag`), never a ring — two ringed clocks would show one percent twice.
- **Rule: one session-aware stop path ends navigation.** Every stop affordance SHALL end navigation
  through the screen's single `stopNavigation` lambda, which calls
  `navigationViewModel.stopNavigation()` and lets the session decide the route's fate. A stop control
  never calls `clearRouteFromMap()` itself and never decides session policy; the routing status card,
  the expanded details view and the panel's Stop Navigation all take that same lambda.
- **Rule: one owner per band.** The phone's bottom band carries exactly one card: while a
  route-planning session's card is shown, the navigation status card SHALL NOT be composed, and it
  SHALL return when the session's surface closes. An overlay that must win a band it shares with
  another composer is not raised above it — the other card yields (the shared band is §11's, and the
  session card is composed in the modal band, so the chrome yields to it as well).

## 8c. Overlay controls and their density

Source: changes `fix-roundabout-exit-number`, `fix-compass-needle-stroke-density`,
`compass-day-night-palette`.

- **Rule: a roundabout exit's turn icon names the exit it takes.** A roundabout exit's turn icon SHALL
  name the exit it takes — the number is inside the ring — and SHALL stay legible at the icon's
  minimum size: the phone overlay draws it at 64 dp for the next turn and 36 dp for the next-next
  hint, the routing status row's step icons at 28 dp. The digit's size follows the ring and is shrunk
  to fit rather than clipped, and the artwork comes from one implementation (`ManeuverSymbols` in
  `:core`, drawn at the pixel size the surface needs), so the phone and the car cannot drift apart.
  The instruction wording stays identical on both variants.
- **Rule: drawn dimensions are density-independent.** A stroke width, a radius and marker geometry
  SHALL be expressed in dp and converted at draw time (`.dp.toPx()`), never as a raw device-pixel
  count: the same number is a different visual size on every screen.
- **Rule: the compass button is larger than the other overlay buttons.** The compass button SHALL be
  larger than the other overlay buttons (56dp layout / 48dp visual against 48dp / 40dp), so it reads
  at a glance.
- **Rule: the compass needle indicates geographic north in every orientation mode.** The needle SHALL
  indicate geographic north in every orientation mode (north-up and heading-up alike), rotated from
  the map angle alone with the single `ProjectionUtils.compassRotationDegrees` convention shared with
  the Android Auto compass rose. The needle takes no vehicle-bearing input; there is no
  travel-direction triangle — the travel direction is shown by the vehicle marker arrow and by the
  map's own rotation.
- **Rule: the compass palette is per-presentation, never a theme role.** Its colours SHALL come from
  a per-presentation palette, never from a theme colour role: the fill is the GPS-fix hue family
  (red / yellow / green) in the tone of the active presentation (light `#FFCDD2` / `#FFF9C4` /
  `#C8E6C9`, dark `#93000A` / `#5C4300` / `#1B4A24`), and the needle, the "N" and the rim share one
  on-fill symbol colour (`#1F1F1F` light, `#E8EAED` dark). The hue family is presentation-independent;
  only the tone changes. The presentation comes from the resolved dark-mode value
  (`MapCanvasUiState.isDarkPresentation`), never from `isSystemInDarkTheme()`, which would bypass a
  manual On/Off.
- **Rule: the speed badge uses the standard overlay card container.** The speed badge SHALL use the
  standard overlay card container (theme surface at 0.92 alpha, 12dp rounded) — the same treatment as
  the turn card and routing status — in the NORMAL state, with dark (`onSurface`) text, never
  white-on-light. At or beyond the speed limit plus the overspeed warning delta
  (`current >= max + delta`) the badge SHALL flip to the fixed warning red `#E53935` fill at the same
  0.92 alpha and 12dp rounding, with white text; the white text appears only together with the red
  warning fill.
- **Rule: a status hue dims to a dark tone of its own family.** A colour that carries status through a
  fixed hue family SHALL dim by switching to a dark tone of its own hue family, never by keeping a
  light-tone status fill in dark presentation, and never by taking the dark scheme's role for that hue
  (M3's dark `error` is a light pink that would fail contrast with white text).

## 8a. Daylight map palette (base map)

Source: spec `daylight-map-palette` (change `daylight-palette-route-and-roads`). One stylesheet
serves both surfaces, so phone and Android Auto cannot diverge here.

- **Rule: the daylight road fills are the standard stylesheet's named fills.** Daylight road fills
  (`standard.oss`; `winter-sports.oss` carries the same two blues) are motorway `#7d7af5`, trunk
  `#a3a1f5`, primary `#f58b8b`, secondary `#fdd08a`, tertiary `#fef271`. The night branch and
  `cycle.oss` are out of scope.
- **Rule: a constant derived from a fill is tuned for its own job, not derived blindly.** The
  stylesheet's `lighten`/`darken` are plain lerps toward white/black, so a lighter base weakens
  everything derived from it. Highway shields — which draw WHITE text — SHALL use their own darker
  constant `darken(base, 0.45)` (`#454387` / `#5a5987` / `#874c4c`). `thinXColor` (the
  `SIZE ... <0.45mm:3px` hairline branch) SHALL use `lighten(base, 0.2)` and is judged on **1.6:1 plus
  a 25-degree hue separation from land**, not on the text threshold. `motorwayJunctionLabelColor`
  SHALL use `lighten(base, 0.3)`, its junction label relying on `style: emphasize` (the Cairo painter
  strokes a white glyph halo).
- **Rule: the palette's legibility is guarded from the packaged stylesheets.** A guard case SHALL
  compute the colours from the **packaged** stylesheets, evaluating the stylesheet's own
  `lighten`/`darken` expressions, so a colour edit that re-breaks legibility fails in unit tests
  instead of on a device (`DaylightPaletteContrastTest`; `StylesheetHexColorCaseTest` guards the
  lowercase-hex rule of §8d).

The measured contrast ratios each constant was chosen against — the former fills' failures, the new
fills' 5.97 / 8.97 / 8.95 / 14.57, and the hairline's 1.6:1-plus-hue convention — are
`UIPhone.why.md` §8a.

## 10a. Phone surface while a car session is active

While a car session is live, the phone does not show the map: it shows the
**car-session surface** (`CarSessionSurface`). This is the one place where the
phone surface yields to the car — everywhere else a car session is advisory
only (§the `car-session-presence` spec: the phone is *informed, not
disabled*). The map is the exception because the car is already drawing it and
the phone's second copy costs exactly the memory a drive on a loaded device is
short of (`guidelines/MapCar.md` §18 has the numbers).

- **Rule: the car-session surface states the pill's own label as its headline.** The car-session pill's own label
  (`car_session_active_indicator`) as its headline — the pill is the compact
  form of the same claim, shown only once the user overrides the suspension, so
  one screen never claims the same thing twice.
- **Rule: the car-session surface shows the shared guidance summary.** The guidance summary from the SHARED navigation state, in
  the labels the car surface uses for the same state: the maneuver through
  `TurnInstructionLocalizer.shortDescription` (the very call the car's
  `NavigationTemplateMapper` cue uses) and the phone's own stats row for
  arrival time / remaining time / remaining distance, which is fed by the shared
  engine state and formats through the same `com.naviveylin.core` helpers the
  car's `distanceForDisplay` delegates to. A free drive has no maneuver to
  summarize; the statement and the map action are then the whole surface.
- **Rule: the car-session surface's one action is a per-session *Show map here* override.** It lifts the
  suspension for the rest of that session only — the session's end clears it, so
  a later session suspends again by default. The override is deliberately not
  persisted: a stored "never suspend" would give the saving up forever.
- **Rule: a suspension resets no map state.** Nothing about the map state is reset by a suspension:
  mode, viewport and magnification are untouched, so the resumed map renders at
  the state the shared engine holds *now*, not at the state the suspension froze.
- **Rule: every car-session transition is one diagnostics record.** Every transition (suspend / override / lift) is one record on
  the diagnostics stream under the `SESSION` tag, carrying the presence edge
  that caused it and the storage the release gave up.

---

## 11. Phone overlay layering (the band stack)

The phone map screen composes everything into **five named bands**, and the band an element is
composed in is the only thing that decides the stacking (`MapLayer` in `MapCanvasScreen.kt`, spec
`map-canvas-screen` — Phone map overlay layer stack).

| Band | z | Holds |
|---|---|---|
| Map | 0 | canvas bitmap, analysed-route highlight, GPS marker, loading/error state |
| Chrome | 10 | **everything that belongs to the map**: compass, speed widget, zoom controls, re-center button, street-name pill, turn card, routing status card, OSM attribution, car-session indicator |
| Menu | 20 | the map menu scrim and panel |
| Modal | 30 | the surfaces that open over the map: search dialog, location details, favorites sheet, route-planning session surface, navigation details |
| Snackbar | 40 | snackbar messages |

- **Rule: chrome is part of the map.** A surface that opens over the map SHALL be composed above the
  *whole* chrome band — never between the map and its chrome. Where a surface and a control overlap,
  the surface owns the pixels and the taps.
- **Rule: a new element picks a band.** Adding an on-map control means composing it in the chrome band;
  adding a surface means the modal band. Nothing else decides the order, so no element has to reason
  about its neighbours, and declaring two bands in a different source order changes nothing.
- **Rule: the band values are spaced by ten.** A band can be inserted later without renumbering;
  `MapLayerBandsTest` asserts the order, and the membership is restated in the spec.
- **Rule: the session surface owns the screen while it is open.** The route-planning card and docked
  panel are modal, so the compass / speed / zoom column is covered where the card paints over it — its
  bottom band in portrait MAX, the whole right side when docked in landscape. The turn card is *not*
  covered, because the card never reaches it. A covered control is fixed by **placement** (a card cap
  or width that leaves the right column free), never by a z-order exception — an exception re-opens the
  class this rule closes.
- **Rule: a contract the whole band shares is provided for the whole band.** A value the band's members
  read from a `CompositionLocal` — the overlay-width probe the widget column reports its measured width
  through (`MapCanvasScreenChromeBand.kt`) — SHALL be provided once around the band's content, never
  around one branch of it: the chrome band composes one right-side widget column from several mutually
  exclusive branches, so a provider on one branch silently falls back to the local's default for the
  others (change `fix-nav-column-width-probe`).
- **Rule: back priority is registration order, not band order.** Compose dispatches a back gesture to
  the handler registered last. The menu band is therefore composed *before* the modal band so an open
  surface keeps the gesture, and the surfaces inside the modal band keep their relative order
  (`map-canvas-screen` — System back dismisses topmost overlay).
- **Rule: Material dialogs and bottom sheets are a second mechanism.** `AlertDialog` and
  `ModalBottomSheet` are presented in their own window and are always above every band here, including
  the snackbar. That asymmetry is deliberate: window-level surfaces keep the platform's dismiss and
  inset behaviour, and the bands cover the rest.

