## Context

See `proposal.md` — Why. Current state that shapes the approach:

- The anchor contract (`vehicle-position-presets`, unarchived) defines the grid as a fraction of the
  **surface**, stores **one** value per mode shared by phone and car, and is implemented as: phone
  `MapCanvasViewModel.followRenderTarget()` → `anchorCenter(...)` + the emitted frame viewport for
  the marker (`fix-phone-vehicle-anchor-framing`); AA `AutoMapRenderer.anchorCenterFor()`.
- Phone overlays that cover the map (`MapCanvasScreen` navigation layout): `NextTurnOverlay` (top,
  full width), `NavigationStateOverlay` (bottom, full width), the right widget column
  (`MapRightWidgetColumn`) and the free-driving `StreetNamePill` (bottom center). None of them report
  their size today; the only measured-overlay flow in the codebase is the route panel's covered
  height used by the route-fit (`route-overview-fit`).
- `AutoSettings` is the car-facing DTO; `AppSettings` is the shared `settings.json`. `AutoSettings`
  currently carries the *shared* anchor ids, and `AutoSettingsMapping` copies them both ways.
- The `0.1..0.9` preset band is exactly the overrun margin of the 1.2x frame
  (`fix-phone-vehicle-anchor-framing`, `guidelines/MapRendering.md` §1.1): the resolved anchor must
  stay inside it or the anchor-centered frame uncovers a strip of surface color.
- `guidelines/Design.md` §3 (state), §4 (threading), §6 (rendering) and the parity rule in
  `guidelines/UI.md` §8 apply. No new thread, dispatcher, dependency or native code.

## Goals / Non-Goals

**Goals:**

- A preset means "position inside the part of the map the driver can actually see" on the phone, with
  one resolved value shared by the render target and the marker.
- On Android Auto a preset never lands behind the host's panel, without changing the default framing.
- Phone and head unit keep independent values; the logical grid/labels/hierarchy stay shared.
- Zero-loss upgrade from the current single shared value, with no settings version bump.
- Keep the framing invariants of the previous change intact for any overlay geometry.

**Non-Goals:**

- Changing the 15-preset grid, ids, labels or the picker UIs (except which value they read/write).
- Measuring the phone's *own* AA-style indicators on the head unit (compass rose, speed badge): they
  are small and corner-anchored; the host's panel is the region that actually swallows a preset.
- Auto-hiding overlays, moving the navigation status card, or inset-aware *route overview* fitting
  (the route-fit flow keeps its own measured-panel handling).
- Animated/springy re-anchoring when an overlay appears or disappears (the next frame lands on the
  new resolved anchor; the commit path already renders on anchor change).

## Decisions

### D1 — Collision-remap: a preset moves only when it is covered

`resolveAnchorFraction(anchor, leftPx, topPx, rightPx, bottomPx, screenW, screenH)` keeps the preset
fraction exact unless the **marker footprint** (marker half-size plus a clearance padding, `0.06` of
the shorter surface side — a fraction so it tracks the density-scaled marker on every surface) at that
fraction falls inside a covered region (one of the measured insets). Per axis independently: no overlap
on an axis → keep the exact preset fraction; overlap → move to the nearest free position on that axis
(covered-region edge plus the same 10% margin the grid uses at the surface edge); both axes clamped to
`0.1..0.9`. The
ViewModel computes it once per commit (and on inset change) and publishes it as
`MapCanvasUiState.resolvedAnchor`; the follow render target (`followRenderTarget`) and the follow marker
projection in `MapCanvasScreen` both consume that published value.

Consequence (the device regression this corrects): the default center/center preset never collides — the
cards sit at the canvas edges, the widget column at the right edge, the pill at the bottom — so it resolves
to the exact (0.5, 0.5) in every mode and orientation. The earlier rescale draft moved it left (right
column) and up (bottom card/pill) merely because overlays were measured — the reported "vehicle left of
center with default anchors in portrait". Corner presets (e.g. bottom-right) move on both axes because
they collide with the card AND the column; a 70% preset is unmoved by the column because it is not covered.

Why not a remap into the visible rect: it shifts every preset — including the default — by geometry the
preset does not touch, which is exactly the reported defect. Collision-remap compensates only the presets
that would otherwise be hidden, and gives side presets the meaning "as far to that side as the visible
surface allows" — the same semantics Android Auto already uses for its host panel (D6).

Alternative A (map at draw time: keep the render target surface-relative and let the marker/blit
subtract the insets): a second derivation of the same fact, and it re-introduces the class of bug the
previous change fixed (two places applying one anchor). Rejected.

Alternative B (clamp/snap an anchor that falls under an overlay): this IS the design. The earlier draft
rejected the clamp because a blanket visible-area mapping keeps "the whole grid meaningful"; the mapping
costs the default framing, and on-device verification (vehicle left of center with default anchors) showed
that cost is unacceptable. Grid meaning is preserved anyway: non-covered presets keep exact fractions and
preset ordering inside the visible area is preserved. Adopted.

Alternative C (shrink the map canvas so overlays never overlap it): changes the whole phone layout
(the overlays are designed as translucent cards over a full-bleed map) and does nothing for a preset
that simply *is* under a card. Rejected.

### D6 — Android Auto: the host panel is a forbidden band, same collision rule as the phone

`AutoMapRenderer.anchorCenterFor` resolves the preset with `clampAnchorOutOfPane(anchor, paneLeft,
paneRight, W, H)`: a preset inside the host's panel band (40% of the width on the leading edge, left in
LTR and right in RTL — `PANE_FRACTION`, side from `isHostPaneOnRight(carContext)`) moves to the nearest
free position (band edge + the grid's 10% margin); every other preset, including the default
center/center, keeps its exact fraction. This is the same collision rule as the phone (D1), specialized
to the panel: the panel spans the surface height, so the clamp is horizontal-only in practice, and the
two paths can share one 4-inset collision function (phone passes the measured top/bottom/right, AA passes
the pane as left/right).

Why a forbidden band rather than a remap: with a 40% leading pane, a remap maps the default 0.5 to 0.7
(and every preset shifts), which contradicts the "default anchors reproduce today's framing"
scenario both AA capabilities specify and moves the vehicle on the head unit for users who never
touched the setting. The band rule changes only the presets that are actually covered — the defect the
phone report and the head-unit panel exhibit — and gives the side presets the meaning "as far to that
side as the visible surface allows".

Alternative A (mapping on AA too, and reword the AA default scenarios to "center of the visible
area"): consistent with the phone's former rescale, but it silently re-frames the default for every
head-unit user and replaces the driver's own anchor intent with an implicit pane compensation. Rejected
(and the phone now shares the band rule instead of the rescale).

Alternative B (keep the pane compensation for navigation/free driving as well, next to the user's
anchor): two competing shifts, and the earlier change deliberately replaced the hardcoded pane offset
with the driver's choice. Rejected.

### D7 — AA overlays that represent map content subtract the blit offset

`AutoMapRenderer` remembers the offset the displayed frame was blitted by (`blitOffsetX/Y`, set in
`blitToSurface` and reset for a fresh render in `drawToSurface`) and `markerScreenPosition()` (used by
`drawGpsMarker`) as well as `drawDestinationMarker` subtract it. Without that, an overlay projected
against the frame viewport sits ahead of the map content by the blit offset between commits (drifts,
then snaps on the next commit) — the AA counterpart of the phone marker defect, recorded in `TODO.md`
§7 and fixed here.

Alternative A (project the vehicle marker against `anchorCenter(display)` as on the phone): works for
the vehicle only; the destination pin is not anchored to the vehicle, it is map content, so it needs
the offset compensation anyway. Having one mechanism for both is simpler to reason about. Rejected.

Alternative B (leave the destination pin as is): it visibly slides off its place between commits — the
same bug class, so it is fixed together.

### D2 — Measure the overlays, do not hardcode their heights

The four overlay composables report their size through `Modifier.onSizeChanged` and the screen pushes
`viewModel.setMapOverlayInsets(top, bottom, right)`; leaving a mode zeroes the corresponding inset.
`StreetNamePill` counts as a bottom inset in free driving.

Alternative A (hardcode from the layout constants, e.g. `0.20·H` for the routing status): the cards
size from their content (distance/ETA rows, reroute/off-route banners, lane row) and from the system
font scale, so a constant is wrong on exactly the devices with the largest text. Rejected.

Alternative B (measure only in navigation, ignore free driving): leaves the street-name pill covering
a bottom anchor in free driving — the same defect in a smaller form. Rejected.

### D3 — Per-surface values live in `AppSettings`; the car DTO carries the car's

`AppSettings` gains `autoRoutingAnchorId`/`autoFreeDrivingAnchorId` (`String? = null`);
`AutoSettings.routingAnchorId/freeDrivingAnchorId` map to/from those fields; the phone keeps
`routingAnchorId`/`freeDrivingAnchorId`. The car's effective value is `autoX ?: phoneX`, so
pre-split settings need no write: the fallback *is* the migration, and the first anchor chosen on the
car freezes the car's own value.

Alternative A (four plain fields with a migration write on load): needs a store→write→reload step,
version flag and a test for the partial-failure path; the nullable fallback delivers the same result
read-only. Rejected.

Alternative B (separate settings files/keys per surface): splits the "same file, same schema" model
the AA settings rely on (`AutoSettingsProvider` ↔ `AppSettings`) for no benefit. Rejected.

Alternative C (keep one value, document that the car inherits the phone's): this is the reported bug
(a good phone position is usually wrong on the head unit). Rejected.

### D4 — Fraction-based `anchorCenter`, enum overload preserved

`anchorCenter(lat, lon, fx, fy, mag, W, H, dpi, angle)` is the primitive;
`anchorCenter(lat, lon, anchor, ...)` delegates with the enum's fractions. The AA path therefore keeps
its exact behavior (identity mapping) while the phone passes the resolved fraction.

Alternative (a separate phone-only anchor-center variant): two implementations of the mirror trick —
the kind of duplication that produced the original double-anchor defect. Rejected.

### D5 — Verification

- `core`: collision table (no insets → identity for all 15 presets; CENTER resolves to exactly (0.5, 0.5)
  under the full navigation inset set — the device regression; BOTTOM_CENTER moves up with fx unchanged;
  a 90%-width preset moves left only when covered while a 70%-width preset stays exact; BOTTOM_RIGHT moves
  on both axes; footprint/padding boundary case; extreme insets → still clamped to `0.1..0.9`; monotonicity
  in each inset for the colliding presets), per-surface settings round-trip through `AutoSettingsMapping`,
  `autoX ?: phoneX` fallback and the freeze-after-car-selection case.
- phone: ViewModel test that `resolvedAnchor` tracks the insets and that the follow render target
  projects the vehicle to the resolved fraction; `FollowAnchorFramingTest` extended so the marker's
  screen position equals the content position at the *resolved* anchor for inset cases.
- Compose: the navigation layout publishes non-zero top/bottom insets; browse publishes zeros.
- Threading/lifecycle: the inset setters are main-thread Compose callbacks writing volatile state into
  the ViewModel (the collector reads it on its own main-thread context); no new coroutine, dispatcher
  or native call (`guidelines/Design.md` §4).
- On-device: navigation with bottom-center under the routing-status card (vehicle visible), top-center
  under the turn card, 90% width next to the widget column; phone vs AA value independence; upgrade
  check that an existing setting is still honored on both surfaces.

## Risks / Trade-offs

- [The resolved anchor moves when an overlay appears (route starts, reroute banner, larger font)] →
  only a covered preset moves and it lands on the nearest free position (one anchor change, no
  incremental drift); the default preset is never affected; the framing invariant (0.1..0.9) bounds the
  shift; verified by the inset-change test.
- [Phone/AA divergence confuses users who expect one setting] → labels/hierarchy stay identical and
  each row names its surface ("Vehicle position (navigation)" on the phone sheet, the same rows on the
  car, each marking its own value); `guidelines/UI.md` documents the deviation and its reason.
- [The car's inherited value could silently differ from the phone's after the phone changes its
  anchor post-split] → intended: the car inherits only until it has its own value; the fallback is
  spelled out in the spec scenarios and pinned by tests.
- [Measuring on every recomposition could thrash the ViewModel] → `onSizeChanged` fires only on a
  real size change; the setter stores the values and re-resolves only when they differ.
- [A clamped resolved anchor could hide the overlay-coverage intent for absurd insets (e.g. keyboard
  covering half the screen)] → the clamp protects the render invariant and is documented; with the
  shipped overlays the resolved values sit well inside the band (e.g. 0.9 → ~0.77).

## Migration Plan

1. `core`/settings: per-surface fields + mapping + fallback (tests).
2. Resolved-fraction plumbing: `resolveAnchorFraction` (collision-remap semantics, design D1 — the
   2026-09-16 correction that replaced the initial visible-area rescale after the on-device report of a
   left-shifted default), ViewModel inset setter + published value, render target and marker consuming
   it, overlay `onSizeChanged` wiring.
3. Docs (`MapRendering.md` §1.1, `UI.md` §8), suite, flavor builds.
4. Archive order: `fix-phone-vehicle-anchor-framing` → `fix-north-up-orientation-angle` →
   **`vehicle-position-presets`** → this change → `compass-always-north-phone` (this change modifies
   requirements that `vehicle-position-presets` adds).
5. Rollback: revert the resolution (identity mapping, one shared value again) — the per-surface fields
   stay readable and harmless.

## Open Questions

None.
