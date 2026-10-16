# Proposal

## Why

The phone's routing status card spends 37 dp of its 145 dp height on two full-width progress
lines (`ProgressLine` in `NavigationStateOverlay.kt` — measured 2026-10-13, a quarter of the
card) that report the
same two values the stats row underneath them already prints as text: percent of distance
travelled and percent of travel time elapsed. The card sits on the bottom edge of a driving
screen, so every dp it keeps costs visible map, and the two bars are not readable shapes at
3 dp anyway — the driver reads the numbers, not the bars. The information can be carried by the
glyph that already labels each value, at no extra height.

## What Changes

- The two progress lines in the routing status card are **removed**, together with the private
  `ProgressLine` composable and the block's own padding.
- Each value glyph in `NavigationStatsRow` becomes a progress glyph: an arc drawn in a **fixed
  28 dp icon slot**, filled with the completed fraction of that value's dimension.
  - remaining time (`Icons.Default.Schedule`) — arc from `elapsedTimePercent`
  - remaining distance (`Icons.Default.Place`) — arc from `routeProgressPercent`
  - ETA — no arc (it shares the time dimension with remaining time), and it takes a distinct
    glyph (`Icons.Default.Flag`) so a plain glyph next to a ringed clock reads as a different
    value rather than as missing data.
- The arc keeps the bar's own palette (`primary` over `surfaceVariant`), its direction (fills
  with completed progress, `-90°` start), its clamping (`clampPercent`, 0-100) and its refresh
  rate (one recomposition per navigation-state emission — no ticker, no animation, no
  per-frame work).
- `NavigationStatsRow` gains `distanceProgressPercent: Int? = null` and
  `timeProgressPercent: Int? = null`. A `null` percent renders the glyph with no arc, in the
  same slot, so the three columns' text baselines stay aligned.
- All three hosts of the shared row render the ring: the phone status card, the phone details
  overlay and the car session surface. This is parity by construction (spec
  `cross-variant-ui-parity`), not a deviation — no platform constraint forces a difference here.
- A reroute that lowers a percent is **accepted** exactly as the bar accepts it today: the arc
  snaps to the new value on the next navigation-state emission.
- The card's existing details tap regions are unchanged: the road-name block keeps
  `navStatusDetailsRegion` (it now covers the road name alone) and the stats strip keeps
  `navStatusDetailsRegion`'s sibling, `navStatusDetailsStatsRegion`.
- **Not changing**: the progress arithmetic. `routeProgressPercent`, `elapsedTimePercent` and
  `clampPercent` (`ui/route/RouteProgress.kt`) are reused as they are, including their
  NaN/zero guards and their unit tests.

## Capabilities

### New Capabilities

None. This change alters how an existing capability presents a value; it introduces no
capability of its own.

### Modified Capabilities

- `navigation-status-details`: the requirement **"Route progress lines in routing status card"**
  is replaced by **"Route progress rings on the routing status row"** — two rings, one around
  the remaining-time glyph and one around the remaining-distance glyph, in place of the two
  full-width lines; every host of the row renders the same rings. The existing scenarios
  (visible only during navigation, distance reflects distance travelled with the step list's
  own total, time reflects elapsed time, no text label or percent value, clamped to 0-100) are
  carried over with ring wording, and one scenario is added for the invariant the fixed slot
  introduces: the three glyph slots occupy equal bounds, so a value whose percent is `null`
  keeps its glyph in place instead of shifting the row.

No other capability's requirements change. `navigation-state-display` (ETA and remaining
distance), `current-road-info` (the road name is the card's first row, above the stats row) and
`auto-navigation-hints` (the car's host travel estimate) describe content that this change
leaves in place; `auto`'s host-side "Trip progress bar" is the Car App Library template's own
element and is not touched.

## Impact

**Affected code**

- `app/src/main/java/com/naviveylin/ui/navigation/NavigationStateOverlay.kt` — delete the private
  `ProgressLine` composable and the progress `Column` block; add the progress-glyph slot; add the
  two percent parameters to `NavigationStatsRow`; change the ETA glyph.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — lines ~2259-2279: the two
  percents move from `NavigationStateOverlay`'s arguments to `NavigationStatsRow`'s (passed
  through the card), and the details-overlay call at ~2626 passes them too.
- `app/src/main/java/com/naviveylin/ui/navigation/NavigationDetailsOverlay.kt` — two new
  parameters on the shared row; the same ETA glyph change.
- `app/src/main/java/com/naviveylin/ui/map/CarSessionSurface.kt` — line ~150 passes the two
  percents, so the car session surface renders the rings.
- `app/src/test/java/com/naviveylin/ui/navigation/NavigationStateOverlayComposeTest.kt` — the two
  progress-line cases are retargeted to the ring's tags; new cases for a `null` percent, for the
  equal slot bounds, and for the drawn arc stroke in device pixels.
- Unchanged: `ui/route/RouteProgress.kt`, `RouteProgressTest.kt`, `MapCanvasViewModel.kt` and
  every caller that computes the percents.

**Android surface and build inputs**

- No manifest, permission, `CarAppService`/template, `:auto` module, native/JNI, CMake, vcpkg,
  stylesheet, resource-string or dependency change. The glyphs come from
  `androidx.compose.material:material-icons-extended`, already a dependency
  (`app/build.gradle.kts` ~line 1490).

**Specifications changed**

- `openspec/specs/navigation-status-details/spec.md` — requirement
  "Route progress lines in routing status card" replaced (delta spec in this change).

**Guidelines affected**

- `guidelines/UI.md` §8 "Phone navigation overlay sizing (driver-seat readability)" — the
  progress-lines sentence at line ~793 names `navStatusDetailsRegion` as covering "the road name
  and progress lines" and must be rewritten; the section's existing "Drawn dimensions are
  density-independent (phone overlays)" rule already governs the arc's stroke, and the new fixed
  slot gains one sentence with the measured saving next to it.
- `AGENTS.md` needs no change (no new module, task or command).

**Scope**

Phone UI change inside a composable shared with the phone details overlay and the car session
surface; the car inherits the presentation because it renders the same row. No Android Auto
template, AAOS flavour or projection path is touched. Parity intent: identical glyphs, identical
arc semantics and identical labels on both surfaces (`UI.md` §1).

**Additive or breaking**

Additive. It removes a redundant presentation of two values that remain visible as text in the
same row, and it adds no new value, permission or persistent state. Nothing is migrated: no
stored preference, no JSON file and no native state carries a progress percent.

**Rollback path**

Revert the change's diff — one composable plus its call sites and tests. There is no persisted
state, no schema and no release-versioning consequence, so the revert is complete on its own. The
spec delta is reverted with it.

**Open question carried into design**

The arc's slot size (28 dp with a 14 dp glyph, or 32 dp with 16 dp) trades readability against
the measured saving. It is settled by the measurement the change carries out (the card's own
`routingStatusCard` bounds), not by argument in this proposal; `design.md` records the options
and the measured outcome — 145.0 dp before, 120.0 dp after.
