# Design

## Context

See `proposal.md` — Why. The state that shapes the approach:

- `NavigationStatsRow` (`app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt`)
  is one composable with three hosts: the phone routing status card
  (`NavigationStateOverlay`, composed from `MapCanvasScreen.kt` ~line 2259), the phone's expanded
  route description (`NavigationDetailsOverlay.kt` ~line 113) and the car session surface
  (`CarSessionSurface.kt` ~line 150). Its three columns are ETA, remaining time and remaining
  distance — each an `Icon` (16 dp, `onSurfaceVariant`) over a `Text` (`titleMedium`, bold).
- The two progress values are computed by the caller and passed in as `Int?`:
  `routeProgressPercent(total, remaining)` and `elapsedTimePercent(start, eta, now)`
  (`app/src/main/java/com/naviveylin/ui/route/RouteProgress.kt`), both clamped to 0-100 by
  `clampPercent` and already unit-tested in `RouteProgressTest.kt`. The card renders the two
  bars only when **both** percents are non-null; the other two hosts pass none.
- The row's height is floored by the card's 48 dp stop control
  (`NavigationStateOverlay.stopNavigation`). The icon slot is 16 dp and the value's text line under
  it is taller than its `titleMedium` role suggests — the slot growing to 28 dp grew the whole row
  by 12 dp (measured 2026-10-13, `NavigationStateOverlayComposeTest.cardHeightStaysWithinTheBound`:
  48 dp → 60 dp), so the row is text-driven, not control-driven.
- The card measured **145.0 dp** with the two progress lines and **120.0 dp** without them
  (same case, same date) — the baseline this design's saving is quoted against.
- The card's own height is already measured in composition
  (`Modifier.onSizeChanged { overlayBottomInset = it.height }`, `MapCanvasScreen.kt:2277`) and
  feeds the vehicle anchor's visible-area inset (`MapCanvasScreen.kt:2326-2329`) — the card's
  height is not cosmetic, it is an input to where the marker is painted.
- Precedent for a density-dependent drawn dimension and for proving it:
  `guidelines/UI.md` §8 "Drawn dimensions are density-independent (phone overlays)" and
  `app/src/test/java/com/naviveylin/ui/map/CompassNeedleStrokeTest.kt`.

## Goals / Non-Goals

**Goals:**

- Remove the two full-width progress lines and their padding from the card.
- Carry both percents on the glyphs that already label the two values, without adding a row,
  a label or a percent text.
- Keep the three hosts of the shared row identical in behaviour, glyph and label order (spec
  `cross-variant-ui-parity`).
- Prove the space claim by measurement rather than by an estimate in a document.

**Non-Goals:**

- No change to how a percent is computed, clamped or refreshed; `RouteProgress.kt` and its tests
  are untouched.
- No change to the card's tap regions, its clickable/stop-control separation or its
  `testTag` contract beyond the two progress tags.
- No animation, no per-frame work, no new state holder, no new dependency, no new string
  resource, no car template or host API.
- Not shrinking any text or glyph to buy space: a driver-seat element that is already legible is
  not made smaller to save 4 dp.

## Decisions

### D1 — The ring is drawn in a fixed icon slot inside the stats row

Chosen: the value's glyph keeps its position and its `contentDescription`, and an arc is drawn
in the same fixed-size slot around it, on every column.

Alternatives:

| # | alternative | why not |
|---|---|---|
| B | the arc **replaces** the glyph (icon-less ring) | drops the distance/time identity the spec requires ("the two rings are told apart from the plain glyph") and the glyph's accessibility label; a bare ring also has to be read as "which value is this?" every time |
| C | one progress line along the card's **top edge** (or its border) | costs no height, but associates the percent with the card rather than with a value, and two lines on one edge composite into one shape |
| D | keep the bars, shrink them (1 dp, no spacer) | still two rows, still the icon+bar+dead-space pattern, saves ~20 dp of the ~37 and leaves a 1 dp line nobody can read at driving distance |

The slot is fixed at **28 dp with the glyph at 16 dp** (unchanged glyph size): the arc's stroke
is 3 dp, centred on a 12.5 dp radius, so the slot's inner clear diameter is 22 dp and a 16 dp
glyph fits with 3 dp of clearance.

**Measured outcome** (2026-10-13, Robolectric mdpi, `NavigationStateOverlayComposeTest.cardHeightStaysWithinTheBound`):
the card fell from **145.0 dp to 120.0 dp** — 25 dp, 17 %. That is less than the 37 dp the removed
block measures, because the row grew by 12 dp (48 dp → 60 dp) around the larger slot: the row's
height is driven by the value's text line, not by the 48 dp stop control the layout code suggests.
The saving is quoted from the measurement, not from the arithmetic.

The documented fallback, if a smaller card outweighs the arc's size: a 24 dp slot with a 14 dp
glyph puts the row at 24 dp + the text line and the card at roughly 116 dp — about 4 dp more than
the measured 120 dp, not the full 37 dp the arithmetic promised. It is a two-constant change
(`NavStatSlot`, `NavStatGlyph`) plus the geometry case's bound. `UI.md` §8 records whichever value
is measured.

### D2 — Which columns carry a ring, and what the arc means

Chosen: the **remaining-time** glyph (percent of estimated travel time elapsed) and the
**remaining-distance** glyph (percent of distance traveled); the arc fills with the **completed**
fraction, `-90°` start, sweep `360 * percent / 100`.

Alternatives: all three columns ringed (ETA and remaining time are the same dimension, so two
glyphs would show one percent twice); remaining time only (drops a value the spec's own
scenarios treat as its own); and an arc that **drains** with the remaining fraction (matches the
number printed under it, but inverts the semantics of the bar it replaces and makes a full ring
mean "arrived").

ETA therefore keeps a plain glyph and takes a glyph distinct from the ringed clock
(`Icons.Default.Flag`, from `androidx.compose.material:material-icons-extended`, already a
dependency at `app/build.gradle.kts` ~line 1490): a plain `Schedule` beside a ringed `Schedule`
reads as "no data" rather than as a different value.

### D3 — A hand-drawn arc, not a Material progress component

Chosen: a `Canvas` in the slot drawing a full 360° track arc (`surfaceVariant`) and the value arc
(`primary`), `Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)`, mirroring the palette and
direction of the `ProgressLine` it replaces.

Alternatives: Material 3's determinate `CircularProgressIndicator(progress = ..., trackColor =
...)` — less code, but it publishes a `ProgressBarRangeInfo` semantics node that this design
deliberately does not want (D6) and that we would then have to suppress with
`clearAndSetSemantics`, it brings its own minimum-size constraints and cap treatment, and its
`progress: Float` overload is on a deprecation path in the Material version in use; and a
`Modifier.drawBehind` on the `Icon` itself — cheapest, but it hides a 28 dp slot inside a 16 dp
child and makes the equal-slot-bounds requirement an accident of layout rather than a statement.

The existing file already hand-draws its other indicators (`CompassButton`'s `Canvas`), so this
is the local convention.

### D4 — The percent is a parameter; the callers keep computing it

Chosen: `NavigationStatsRow` gains `distanceProgressPercent: Int? = null` and
`timeProgressPercent: Int? = null`; `NavigationStateOverlay` forwards them to the row;
`MapCanvasScreen` passes the same two expressions it computes today to both the card and the
details overlay; `CarSessionSurface` passes them from the shared `NavigationState`.

Alternatives: have the row accept `totalDistance`/`navigationStartTimeMillis` and compute the
percents itself — it would need four more parameters, a clock read inside the composable, and it
would duplicate arithmetic that is already unit-tested in `RouteProgress.kt`; or a small
`ProgressPercents` data class parameter — one more type for two nullable ints, and the two
values belong to two different columns, so a per-value parameter is clearer at the call site.

The two percent values stay nullable and independent (today they are all-or-nothing), so a value
without progress renders its glyph with no arc in the same slot.

### D5 — The arc snaps; no animation and no held value on reroute

Chosen: the arc is drawn at whatever percent the composition was recomposed with, and a reroute
that lowers a percent moves the arc on the next navigation-state emission, exactly as the bar
does today.

Alternatives: `animateFloatAsState` over the percent — during driving it recomposes every frame
for a secondary cue that the driver reads at most once a second, and it makes the card's
`onSizeChanged`/anchor interplay noisier; holding the previous percent while `isRerouting` is
true — the flag exists on the card, but it would show a progress value the state no longer
supports, i.e. a knowingly false number next to true ones; and recolouring the arc during the
reroute — a colour that means "recalculating" on a driver-facing overlay is a new vocabulary for
no measured gain.

Refresh cadence is unchanged: the percents come from `NavigationState` fields, so the ring
updates as often as the numbers beside it and no ticker is added.

### D6 — The arc carries no progress semantics

Chosen: the arc is drawn with no semantics; the glyph keeps the `contentDescription` it has today
(`R.string.eta`, `R.string.remaining_time`, `R.string.distance`).

Alternatives: `Modifier.progressSemantics(percent / 100f)` (or `ProgressBarRangeInfo`) so
TalkBack can announce the percent — genuinely useful for a screen-reader user, and the argument
against it is not that the spec forbids a *visible* percent but that the ring is a secondary cue
whose value is already available as text in the same row: the announcement would read the row as
"ETA, 12:45, remaining time, 23 min, 42 percent, remaining distance, 12.3 km" and lengthen
every traversal. Recorded as a deliberate omission in `UI.md` §8 rather than an accident; if it
is ever wanted, it is one modifier and one scenario.

## Risks / Trade-offs

- [A 3 dp arc on a 28 dp ring may not read at driver distance] → the value is also printed as
  text in the same row, so a faint arc degrades to decoration rather than to missing information;
  the stroke is measured in pixels at 1x and 4x (the `CompassNeedleStrokeTest` pattern) and the
  rendered ring is read on device before the change is called done. Raising the stroke to 4 dp is
  a one-constant change.
- [The row grows as the slot grows, so the saving is 25 dp rather than the removed block's 37 dp]
  → measured, not assumed: 145.0 dp → 120.0 dp with the bound asserted between the two numbers
  rather than pinned to either; the 24 dp fallback is documented with the ~4 dp it would return,
  and the device run records the number the same way.
- [A ring drawn with a raw float stroke reintroduces the `3f`-vs-`3.dp.toPx()` defect] →
  `UI.md` §8's drawn-dimension rule plus the raster case that asserts the same stroke in dp at
  1x and 4x, with the numbers printed into the JUnit XML.
- [The card's height is an input to the vehicle anchor's visible area] → the height change is
  measured and the anchor recomputes from `overlayBottomInset` on every size change; a smaller
  card only widens the visible map area, and the anchor test suite
  (`MapLayerStackComposeTest`, the nav-column probe) is re-run.
- [Three hosts, one row: a host that forgets to pass the percents silently shows no rings] →
  the car surface and the details overlay each get a case asserting the rings are present, so
  the omission is a test failure rather than an invisible difference between surfaces.
- [Renaming the two `testTag`s (`distanceProgressLine`/`timeProgressLine`) breaks any test that
  relies on them] → those are exactly the two cases retargeted in this change; no other test or
  probe references them.

## Migration Plan

No data migration: no stored progress value, no preference, no JSON file, no native state, no
DB schema. The change is three compositions and their call sites plus tests, so the rollback is
`git revert` of the change's diff — nothing persists across it, and the spec delta is reverted
with it.

Order of work: the delta spec's scenarios drive the tests first (ring presence, ring absence,
equal slots, the card's height bound), then the composable change, then the guideline update with
the measured numbers.

## Verification

**Unit / Compose (Robolectric, no `@Config(sdk=)` — the JNI stub classloader rule,
`Build.md` §6):**

- `NavigationStateOverlayComposeTest` — rings present with percents, absent with `null`
  (retargeted from the two bar cases), the three slots of equal size and vertical alignment, and
  the card's measured height bound (this last one is red on HEAD at the measured 145.0 dp).
- `NavigationStateOverlayComposeTest` also keeps the existing stop-control and details-region
  cases: the region bounds change when the progress block goes, and the case that the stop
  control's hit area stays outside both regions must stay green.
- `NavigationDetailsOverlayTest` — the same row inside the expanded view carries the rings.
- `CarSessionSurfaceComposeTest` — the car host carries the rings with the same labels.
- New raster case (`@GraphicsMode(NATIVE)`, `CompassNeedleStrokeTest`'s measurement style, with
  the run of arc-coloured pixels across the ring printed into `system-out`): the arc's stroke is
  the same width in dp at 1x and 4x.
- `RouteProgressTest` and every existing suite: untouched, re-run as the regression check.

**Device (emulator with maps, `device-check` / `provision-phone-emulator`):**

- Card height before/after from the card's own `onSizeChanged` inset, or from
  `adb shell uiautomator dump` bounds of the `navStatusDetailsRegion` node — a height in dp, never
  a coordinate (`Logging.md`, the coordinate rule; the diagnostics line carries numbers such as
  magnification, pixel and accuracy, never a position).
- One screenshot of a running navigation session with the ring at a known percent, read with the
  `pixel-check` skill, to confirm the arc is drawn, is round-capped and is not clipped by the
  28 dp slot.
- The stationary-emulator limit stated honestly: the emulator cannot produce a moving route
  progress, so the *changing* percent is verified by the Compose cases (a percent parameter in,
  a ring out) and the *rendered* arc at a fixed percent by the screenshot. A GPX replay is not
  required for this change and is not claimed.

## Open Questions

None. The arc's slot size is selected (28 dp) with a documented fallback whose trigger is the
measured card height, and the measurement is a task in this change rather than an open question —
it cannot change the spec, the approach or the task breakdown.
