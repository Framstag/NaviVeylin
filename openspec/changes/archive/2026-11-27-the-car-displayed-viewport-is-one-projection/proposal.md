# Proposal: the car's displayed viewport is one projection

## Why

The car surface states the same display rule as the phone's — where a map-anchored overlay goes when the drawn
frame is shifted inside the overrun window — through two mechanisms keyed on the same resolved anchor.
`AutoMapRendererMotion.kt:193` computes the blit offset with `OverrunWindow.shiftPx(...)` against
`resolvedFollowAnchor()`, and `:219` moves the frame with `anchorCenterFor(displayLat, displayLon)`; the same
pairing repeats at `AutoMapRendererRender.kt:84` (the blit offset) and `:154` (`anchorCenterFor`), and the
marker's own draw offset is a third reading at `:276`.

Both are correct today and the anchor is resolved once, so the two never disagree — but the rule "the overlay's
position is the frame's position plus the blit offset" is answered in three places, each of which must be
given the same resolved fraction. The day one site is handed the raw preset, the marker drifts from the content
it rides and nothing catches it: the "Single resolved anchor in the AA follow blit" scenarios pin the resolved
fraction, not the derivation's singularity.

This change is the car half of `#706`. The phone's pairing is a sibling change
(`the-displayed-viewport-is-one-projection`), so a fix on one surface is never mistaken for a fix on the other.

## What changes

- One derived **displayed viewport** on the car: the frame viewport shifted by the clamped overrun offset,
  computed once and read by the blit, the marker's draw offset and the frame's own commit. The three sites SHALL
  read it rather than each recomputing a shift from the resolved anchor.
- `anchorCenterFor` SHALL remain the derivation's input — the frame's anchor-centred commit — and SHALL NOT be
  a second projection the blit has to agree with.
- The marker-to-content glue SHALL be unchanged on screen: the marker keeps riding the blitted content at the
  resolved anchor.

## What does not change

- `OverrunWindow.shiftPx`'s own arithmetic and the clamp, and `clampAnchorOutOfPane`'s pane-band resolution.
- The blit-versus-full-render decision and its throttle.
- The phone surface, which is its own change.
- The capability's shipped scenarios: both are repeated verbatim in the delta.

## Surface

surface: 1 requirement modified in auto-smooth-follow (2 scenarios added), 3 call sites rewired, 1 case added —
`tools/look.sh 'anchorCenterFor'` (on the base, 6 files name it: `AutoMapRendererMotion.kt:219`,
`AutoMapRendererRender.kt:154`, `AutoMapRenderer.kt:542`, `:718`, `:772`, and the definition at
`AutoMapRendererFollow.kt:94`) and `tools/look.sh 'OverrunWindow.shiftPx'` (3 sites: `AutoMapRendererMotion.kt:193`,
`AutoMapRendererRender.kt:84`, `:276`). One case added (`auto#theCarOverlayOffsetIsTheFramesShift`), one
requirement modified (`auto-smooth-follow`, "Single resolved anchor in the AA follow blit").

## Out of scope

The phone surface's pairing; a shared "displayed viewport" type across the two variants, which is a later change
if the two derivations turn out to be the same function.

Route:

LOOP-ROUTE: guidelines/MapCar.md §14
