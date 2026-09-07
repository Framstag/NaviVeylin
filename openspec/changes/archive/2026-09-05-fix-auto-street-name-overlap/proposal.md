# Fix Auto Street-Name Overlap with Host ETA Card

## Why

During AA routing, the current street name is drawn at the bottom center of the
map surface (`StreetNameLabel`), anchored to the host's stable area. On real
head units the host's travel-estimate (ETA) card — a view in the bottom-left
corner, not a full-width bar — partially covers the label. The stable area
delivered by the host is unreliable: it can be empty (the code then falls back
to the *visible* area, which does not account for occlusions "as if always
present"), delivered as the full surface, or delivered before the ETA card
appears and never updated. The label therefore sits under the ETA card.

## What Changes

- `StreetNameLabel` anchors to the more conservative of the host's stable and
  visible area bottoms (`min(stable.bottom, visible.bottom)`) instead of the
  stable area alone, so a wrong/missing stable area can no longer push the
  label under the ETA card.
- The two Auto screens (`NavigationScreen`, `FreeDrivingScreen`) track the
  visible area separately from the stable area and drop the
  `stableArea.set(visibleArea)` fallback that conflated the two.
- The label width is capped tighter (~360 dp) so it stays in the center region
  between the ETA card (bottom-left) and the map action strip (bottom-right).
- A small bottom reserve is applied while navigating (travel estimate present)
  as a safety margin for hosts that deliver neither area correctly.
- **On-device finding (design D5)**: the user's head unit delivers both areas
  as the full surface, so no canvas anchor clears the card. When the host
  delivers no area that clears the surface bottom, the street name is
  rendered inside the host ETA card via `TravelEstimate.setTripText` instead
  of the map surface (map label hidden); hosts with correct geometry keep
  the map label and no trip text.
- Free driving keeps its current anchor (no ETA card there) but shares the
  corrected stable/visible handling.

Additive behavior fix — no API, manifest, or native changes. Rollback: revert
the anchor logic; the label returns to the previous (covered) behavior.

## Capabilities

- **New Capabilities**: none.
- **Modified Capabilities**:
  - `auto/navigation-view` — "Current street name shown during navigation":
    the label must stay clear of the host ETA card.
  - `auto/free-driving` — "Current street name shown": same anchor correction
    (shared logic; no ETA card in free driving, but the stable/visible
    handling is shared).

## Impact

- `auto/src/main/java/com/naviveylin/auto/StreetNameLabel.kt` — geometry:
  accept stable + visible bounds, width cap, optional bottom reserve.
- `auto/src/main/java/com/naviveylin/auto/NavigationScreen.kt` — track
  `visibleArea` separately; pass both bounds to the label; reserve while
  navigating.
- `auto/src/main/java/com/naviveylin/auto/FreeDrivingScreen.kt` — same
  stable/visible tracking.
- Tests: `auto/src/test/java/com/naviveylin/auto/StreetNameLabelTest.kt`,
  `SurfaceLayoutTest.kt`, screen tests for the anchor logic.
- Guidelines: `guidelines/UI.md` (Auto surface overlay rules) — update if it
  documents the bottom-anchor behavior.
- No native/JNI, manifest, or Gradle changes. Scope: `:auto` module only
  (both navigation and free-driving screens); phone UI unaffected.
