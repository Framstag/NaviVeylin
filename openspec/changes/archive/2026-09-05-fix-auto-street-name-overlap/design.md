# Design: Fix Auto Street-Name Overlap with Host ETA Card

## Context

See proposal.md — Why. Current state: `StreetNameLabel` anchors to
`stableArea.bottom - 16dp`; both Auto screens conflate the host's stable and
visible areas (`if (stableArea.isEmpty()) stableArea.set(visibleArea)` in
`NavigationScreen.kt` and `FreeDrivingScreen.kt`). Per the `SurfaceCallback`
contract, the stable area accounts for occlusions "as if always present"
while the visible area is the *current* guaranteed-visible region — the two
are not interchangeable. On the user's head unit the ETA card (bottom-left)
covers the label, so the delivered stable area is empty, full-surface, or
stale.

## Goals / Non-Goals

**Goals:**
- Label never covered by the host ETA card during navigation.
- Correct behavior even when the host delivers no stable area.
- Free driving keeps its current look (no ETA card there).

**Non-Goals:**
- No host-rendered placement (`TravelEstimate.setTripText`) — rejected, see
  Decisions.
- No change to street-name resolution, rendering pipeline, or phone UI.
- No change to the ETA card itself.

## Decisions

### D1: Anchor to `min(stable.bottom, visible.bottom)` instead of stable-only

Track `stableArea` and `visibleArea` as separate rects in both screens; the
label's bottom edge is the smaller of the two bottoms, falling back to the
surface bottom only when both are empty.

- **Alternative A (chosen)**: min of both bottoms. Correct whenever the host
  delivers either area correctly — the visible area excludes the ETA card
  while it is shown, the stable area excludes it "as if always present".
- **Alternative B (current)**: stable-only. Fails when the stable area is
  empty (fallback to visible area is wrong), full-surface, or stale.
- **Alternative C**: fixed bottom reserve only. Wastes space on hosts that
  deliver correct geometry; still a guess.

Rationale: A is correct on every host that delivers at least one correct
area, which is the common case; the reserve (D3) covers the rest.

### D2: Cap label width at ~360 dp and ellipsize the text

The label is horizontally centered; a 520 dp cap reaches the ETA card zone
(bottom-left) and the map action strip (bottom-right) on typical ~800 dp
screens. Cap at ~360 dp and ellipsize the text so the *text* cannot overflow
the pill into the ETA card zone — the current code caps the pill but draws
overflowing text centered. Ellipsize is implemented manually (measure +
truncate + "…"): `TextUtils.ellipsize` is a no-op under Robolectric, so the
truncation would be untestable in unit tests.

- **Alternative A (chosen)**: 360 dp cap + ellipsize.
- **Alternative B**: keep 520 dp — reaches the ETA card zone.
- **Alternative C**: no cap, rely on the vertical anchor only — does not fix
  horizontal reach.

### D3: Small bottom reserve (~24-32 dp) only while navigating

The screens know when a travel estimate is present (they set it in
`NavigationTemplateFactory`). `NavigationScreen` passes a reserve of
~24-32 dp; `FreeDrivingScreen` passes 0 (no ETA card). The reserve is a
safety margin for hosts that deliver neither area correctly.

- **Alternative A (chosen)**: small reserve, navigation only.
- **Alternative B**: no reserve — relies entirely on host geometry.
- **Alternative C**: large reserve (~80 dp) — pushes the label too high on
  hosts with correct geometry.

### D4: Label API takes stable + visible bounds and a reserve

`StreetNameLabel.geometry` gains `visibleBounds: Rect` and
`bottomReserveDp: Float` (default 0); `draw` forwards them. Both screens pass
their tracked rects. No new components, no threading changes — the rects are
written in `SurfaceCallback` (main thread) and read in the overlay drawer
(same thread), so no synchronization is needed. Lifecycle: rects already
reset on surface destroy.

### D5: Host ETA card trip text when the map label is not safe

On-device finding: the user's head unit delivers **both** the stable and the
visible area as the full surface (or empty), so `min(stable.bottom,
visible.bottom)` resolves to the surface bottom and only the D3 reserve
lifts the label — it stays under the ETA card. The host never excludes the
card from either area, so no canvas anchor can fix the overlap on that
host. The guaranteed fallback (documented in Risks, now implemented):
render the street name **inside the host ETA card** via
`TravelEstimate.setTripText` — the host positions the card, so the name is
never covered.

- `StreetNameLabel.isMapLabelSafe(stable, visible, surfaceHeight, density)`:
  true when at least one delivered area clears the surface bottom by a
  safety margin (~48 dp) — i.e. the host excluded the card. Empty or
  full-surface areas → false.
- `NavigationScreen` draws the map label **only when safe**; when unsafe it
  sets `setTripText(streetName)` on the travel estimate instead (the map
  label is hidden — a half-covered pill looks broken).
- Safety flips (area callbacks) and street-name changes while unsafe
  invalidate the template so the card text follows the road.
- Free driving keeps the map label unconditionally (no ETA card there).

- **Alternative A (chosen)**: hybrid — map label when safe, trip text when
  not. Correct on every host: correct geometry → label on map; broken
  geometry → name in card.
- **Alternative B**: trip text always. Simpler, but the name appears in the
  card even when the map label would be fine (redundant on correct hosts).
- **Alternative C**: large fixed reserve (~100 dp) while navigating. Keeps
  the label on the map but floats it high on hosts with correct geometry
  (rejected in D3).

## Risks / Trade-offs

- [Host delivers both areas as full surface] → Mitigation: reserve + width
  cap still protect the common case; if a host is found where the label is
  still covered, `TravelEstimate.setTripText` remains the guaranteed
  host-rendered fallback (documented, not implemented).
- [Reserve pushes label higher than ideal on correct hosts] → Mitigation:
  keep it small (24-32 dp); free driving unaffected.
- [Ellipsized street names lose tail characters] → Mitigation: 360 dp fits
  most names; truncation only for very long names, and the pill stays clear
  of the ETA card (spec requirement wins over full text).

## Migration Plan

Single commit, additive behavior fix. Rollback: revert the anchor logic —
the label returns to the previous (covered) behavior. No data, manifest, or
API changes.

## Verification

- Unit tests: `StreetNameLabelTest.kt` — min-bottom logic, empty-bounds
  fallback, width cap + ellipsize, reserve; screen tests — visible area
  tracked separately, no stable-area fallback.
- On-device: AA emulator + head unit — navigate with a travel estimate,
  confirm the label sits above the ETA card; free driving unchanged; logcat
  `NaviVeylin` for surface geometry.

## Open Questions

- Exact reserve value (24 vs 32 dp) — tune on the emulator; does not change
  the spec or approach.
