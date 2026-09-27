# Design — fix-browse-recenter-visibility

Motivation and scope: see `proposal.md` (Why / What Changes / Impact). Behavioral contract:
`specs/map-modes/spec.md` ("Browse re-center") and `specs/map-recenter-button/spec.md`.

## Context

Current state, only what the approach depends on:

- `MapCanvasViewModel` (`app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt`, ~3700 lines)
  owns the viewport (`viewport: MapViewport` — center lat/lon, magnification, angle), the GPS
  marker position (`gpsMarkerLat/Lon`, from the `locationService.location` collector at ~L1002) and
  the canvas size + overlay insets (`setMapOverlayInsets` ~L445). All three inputs of the new rule
  are already there.
- The button predicate is `MapCanvasViewModel.shouldShowReCenterButton(mode, driveSuspended,
  browseDrifted)` (~L3523), called from three places in `MapCanvasScreen.kt` (~L1340 portrait,
  ~L1476 landscape, ~L1947 navigation branch), each already guarded by
  `state.gpsFixQuality != GpsFixQuality.NONE`. `browseDrifted` is a sticky flag with two writers
  (`disengageFollowMode` ~L2899, `updateMagnification` ~L3441) and four clearer sites.
- Screen-to-geo projection is pure Kotlin in `:core` (`ProjectionUtils.viewport`,
  `ProjectedViewport.screenToGeoRotated`); the inverse for the marker lives in
  `LocationMarkerOverlay.kt` (~L65). No native call is involved.
- `resolveAnchorFraction` / `resolveAnchorAxis` (`core/src/main/java/com/naviveylin/core/VehicleAnchor.kt`,
  ~L119-172) map an anchor preset into the area not covered by overlays. `VehicleAnchor.kt:240`
  already documents browse mode as the identity case for that mapping.
- Scale reference (`ProjectionUtils.computeScale`, `REFERENCE_DPI = 96`): on a 420 dpi phone one
  screen pixel is ~1.09 m at magnif 15, ~0.27 m at 17, ~0.034 m at 20 (`MAX_MAG = 20`). That number
  drives decision D3.

## Goals / Non-Goals

Goals: the button reflects the *measured* state of the map (is the vehicle at the center?), it is
testable at the ViewModel level, and the browse rule cannot contradict the anchor framing.

Non-goals (design-level boundaries, on top of the proposal's scope): no off-screen direction
indicator for a vehicle outside the viewport; no change to the street-pill placement rule that
still keys off the free-driving anchor in browse (`MapCanvasScreen.kt` ~L1806); no change to the
marker projection or to the render/follow pipeline.

## Decisions

### D1 — The rule lives in the ViewModel as a derived state field, fed by one collector

`browseDrifted` is replaced by `browseReCenterVisible: Boolean` in `MapCanvasUiState`, recomputed by
a single collector over the existing UI state (viewport + marker position + canvas size), emitting
only on a visibility change — the same idempotent-emit discipline `publishResolvedAnchor`
(~L468-483) already uses to avoid a feedback loop on its own emission. The screen keeps calling
`shouldShowReCenterButton`, with the third parameter now being the derived value.

*Alternatives considered.*
- **Composable-local (`remember` + `LaunchedEffect`)** — fits `guidelines/Design.md` §3 ("purely
  presentational logic belongs in the composable layer") and needs no ViewModel emission. Rejected:
  the hysteresis latch must survive canvas recomposition and a config change (rotation recreates the
  composable, but not the ViewModel), and the rule would then only be testable through a Compose
  test. The existing `MapReCenterButtonOverlayTest.kt` shows the cost — it re-declares the predicate
  inputs (`var browseDrifted = false`, L32) because it cannot reach them from the screen. A compose
  test also runs under Robolectric, where this project's native-stub classloader rule applies.
- **Pure function called from the screen, latch hoisted into the ViewModel** — splits one rule across
  two layers and two lifetimes for no gain.

Precedent: `driveSuspended` is the same shape for the driving modes; the change replaces an
interaction flag with a measurement, it does not introduce a new state mechanism.

### D2 — Offset = Euclidean screen-pixel distance between the vehicle and the canvas center

The vehicle is projected with the displayed viewport and compared against the canvas center
(fx = fy = 0.5) of the same frame, i.e. the same projection the marker overlay uses.

*Alternatives considered.*
- **Geo/angular distance** (e.g. "more than 25 m off center") — zoom-independent, but wrong at both
  ends: at magnif 20 a 200 px pan is ~7 m, so panning the map away from the vehicle would not show
  the button; at magnif 4 the vehicle can be 2 km off center, still sub-pixel, and the button would
  appear for an invisible displacement.
- **Per-axis offsets** (|dx| or |dy| over a threshold) — a diagonal displacement can exceed the
  Euclidean threshold while each axis stays under it, so the button would appear late.
- **Chosen: pixel distance** — "off center by more than N screen pixels" is what the user sees, and
  it degrades gracefully: at low zoom a large real displacement is a small visible one, which is
  exactly when no cue is needed.

The reference point is the canvas center, not the visible-area-mapped center: for the center
fraction `resolveAnchorAxis` returns 0.5 unchanged unless an overlay band overlaps the marker
footprint at the center (a right/left inset larger than ~⅓ of the width). A rotation about the
center does not change the distance, which is asserted by a spec scenario.

### D3 — Appear at 24 px sustained for 1 s; hide below 12 px, immediately

Named constants in `MapCanvasViewModel` (`RECENTER_SHOW_OFFSET_PX = 24.0`,
`RECENTER_HIDE_OFFSET_PX = 12.0`, `RECENTER_DWELL_MS = 1_000L` — no magic numbers inline, per
`guidelines/Build.md` conventions).

- 24 px is ~26 m at magnif 15 and ~209 m at magnif 12: the button appears when the displacement is
  visible, not when a metric line is crossed.
- The dwell is mandatory, not cosmetic. At magnif 17-20 the pixel offset is GPS noise — 2-10 m of
  stationary jitter is 7-300 px, i.e. larger than any threshold pair — because one pixel is
  ~0.034 m at magnif 20. No fixed pair of thresholds is stable there; a sustained-offset
  requirement is. The dwell is measured as `now - firstExceededMs >= RECENTER_DWELL_MS`, evaluated
  on every recompute from `System.currentTimeMillis()` — **no timer, no delayed coroutine; the
  timestamp resets whenever the offset drops below the appear threshold.** The hide edge has no
  dwell, so pressing re-center hides the button immediately.
- Consequence to accept: with a stale viewport and a 1 Hz fix rate, the button can appear up to
  ~1 s after a pan stops if no further fix arrives. Panning itself emits viewport changes, so in
  practice the dwell elapses during the gesture.
- *Alternatives considered.* **Fixed geo threshold** — see D2. **Accuracy-relative band** (appear
  beyond twice the projected GPS accuracy radius) — self-explaining, but the projected accuracy
  radius grows with zoom exactly as the position noise does, so it is the noise band and does not
  stabilize high-zoom flicker either. **Instant on gesture, dwell only on position-driven offsets** —
  removes the ~1 s delay after a pan, at the cost of a "why did the offset change" input threaded
  through the recompute; rejected as added coupling for a sub-second difference.

### D4 — Browse re-center sets the viewport center to the GPS position

`recenterInBrowse()` (~L3014) becomes: if the mode is BROWSE and a fix exists, `viewport.centerLat/Lon
= fix`, clear the visibility, re-render. It no longer calls `followRenderTarget` (~L494) or consults
`resolvedAnchor`.

*Why (beyond the spec's wording).* Any other target makes the change self-contradictory: with a
non-center anchor preset (e.g. "Bottom center") an anchor-framed re-center leaves the vehicle off
the center by design, the new condition would immediately re-show the button, and the button could
never be dismissed — the exact "button never hides" trap. `MapCanvasViewModelVehicleAnchorTest.kt`
`browseRecenterCommitsTheAnchorCenteredFrame` (L370) asserts the old behavior with
`MIDDLE_FAR_RIGHT` and is rewritten to assert centering plus a hidden button.

*Alternatives considered.* Keep the anchor framing for browse and measure the offset against the
resolved anchor instead → contradicts the chosen rule and the user-visible meaning of "centered";
or keep the anchor framing and suppress the button while the vehicle sits at the anchor → the
predicate then depends on configuration, which is harder to reason about and to test.

### D5 — The anchor presets keep applying to the driving modes only

No code change to `followRenderTarget`, `publishResolvedAnchor`, the anchor pickers or
`SettingsStorage`. Browse stops consulting them, which matches the durable spec
(`location-options-ui` — "Vehicle anchor position controls" lists a routing and a free-driving
entry, not a browse one). `guidelines/UI.md` §1 "Vehicle position setting (follow-mode anchor)"
already scopes the setting to follow modes; §7a is updated in this change because it documents the
removed `browseDrifted` behavior.

### D6 — Removal is complete, not masked

`browseDrifted` disappears from the state class; the BROWSE branches of `disengageFollowMode()`
(~L2899) and `updateMagnification()` (~L3441) are deleted. In BROWSE `disengageFollowMode()` becomes
a no-op — its FREE_DRIVE (follow-engagement framing, ~L2883) and NAVIGATION paths are untouched, and
the zoom/rotate call sites in `MapCanvasScreen.kt` keep calling it only for their driving-mode
effect. No compatibility shim is left behind for the flag.

## Risks / Trade-offs

- **[The rule is now a live predicate, so the button is visible most of the time in browse.]** Browse
  does not follow, so any pan, zoom or vehicle movement sets it. → Intended (proposal; the old
  `map-recenter-button` spec's "follow mode off → show" said the same). Mitigation for the visual
  risk: the button is covered by the favorites sheet, search dialog and route panel exactly as today
  (they are separate composables layered above the canvas), so no new collision is introduced.
- **[High-zoom flicker]** GPS noise at magnif ≥ 17 exceeds the threshold band. → Dwell on the appear
  edge (D3), plus a unit test that feeds a noisy stationary fix sequence and asserts no toggle.
- **[Dwell regression to the reported bug]** A dwell that never elapses (offset oscillating in and
  out of the appear band) would leave the user with no button again — the original complaint. →
  Timestamp reset only on dropping *below the appear threshold*, and a test that a sustained
  off-center position shows the button within the dwell; the hide threshold is strictly smaller, so
  the two bands cannot deadlock.
- **[Spec ordering vs the unarchived sibling change]** `browse-drive-modes` (20/23 tasks, not yet
  archived) contributes `map-modes` requirements that are not in the durable spec yet, including an
  older "Browse re-center" with the `browseDrifted` wording. This change's delta therefore ADDs
  "Browse re-center" (the durable `openspec/specs/map-modes/spec.md` has only "Map mode model"). →
  Archive `browse-drive-modes` first, then this change's ADDED requirement replaces that wording;
  do not archive this one first, or its ADDED text is overwritten by the older requirement.
- **[Anchor test reversal]** `browseRecenterCommitsTheAnchorCenteredFrame` encodes the old contract.
  → Rewritten in the same commit, so the suite never reports a false regression.
- **[Diagnostics]** A rule that is invisible in a screenshot needs a signal. → Log the offset and the
  visibility transition through `DiagnosticsLog` in the existing style
  (`DiagnosticsLog.log("RECENTER", "off=…px vis=…")`, compare `MapCanvasViewModel.kt:2374`), plus a
  `Log.d` line under the class TAG, so on-device verification has evidence.

## Migration Plan

1. Land and archive `browse-drive-modes` first (its code is already in the tree; only its on-device
   tasks remain), so this change's `map-modes` delta modifies a requirement that exists.
2. Apply this change: state field + derived rule + `recenterInBrowse` simplification + the three
   call sites, then `guidelines/UI.md` §7a, then the tests.
3. On-device verification per `tasks.md` (phone; mag 20 stationary; rotation; driving in browse).
4. **Rollback**: revert the change. `browseDrifted` returns, browse re-center uses the anchor framing
   again. No persisted format is touched (`browseDrifted` is runtime UI state; `AppSettings` and the
   favorites/search JSON are untouched), so no migration and no data loss either way.

## Open Questions

None. The threshold and dwell values are calibrated on-device in the verification task and are
named constants, so tuning them does not change the specs, the approach or the task breakdown.
