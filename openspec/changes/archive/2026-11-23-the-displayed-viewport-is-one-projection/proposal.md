# Proposal: the displayed viewport is one projection

## Why

The phone map states one display rule — where a map-anchored overlay goes when the drawn frame is shifted
inside the overrun window — through two mutually exclusive mechanisms. Follow projects the overlay against
the anchor centre of the **displayed** position and shifts it by zero
(`app/src/main/java/com/naviveylin/ui/map/MapCanvasScreenParts.kt:326-331`,
`markerDisplayShiftPx` returns `0f to 0f` when `followActive`), while pan keeps the frame's own viewport and
translates the overlay layer by the clamped offset (`MapCanvasMapBand.kt:618` and `:682`, over
`state.renderViewport` at `:614` and `:672`).

Both are correct today and a pan disengages follow, so the two never meet. That is the whole safety argument:
the day a path keeps follow active while panning, or an overlay lands on the pan path without the shift, the
drift is applied twice or not at all — and nothing fails, because the rule "the overlay's shift is the frame's
shift" is carried by a mode test instead of by a value. No case fails today and the duplication has no test
(`#706`, the phone half).

This change is the phone half only. The car surface carries the same pairing in different files
(`AutoMapRendererMotion.kt:193`/`:219`, `AutoMapRendererRender.kt:84`/`:154`/`:276`) and is a sibling change,
so a fix on one surface cannot be mistaken for a fix on the other.

## What changes

- One derived **displayed viewport**: the frame viewport shifted by the clamped overrun offset, computed once
  per frame and handed to every overlay. The mode SHALL NOT select between two projections — follow and pan
  SHALL both read the same value.
- `markerDisplayShiftPx` and its two `graphicsLayer` translations SHALL be retired: an overlay whose viewport
  is already the displayed viewport needs no translate, and the zero-shift branch disappears with the
  function.
- The follow anchor-centre projection SHALL become the same derivation rather than a second one: the anchor
  centre of the displayed position is the displayed viewport's own centre, so it is read, not recomputed.
- The marker-to-content glue SHALL be unchanged on screen: the marker keeps landing on the road at the
  resolved anchor, in both modes.

## What does not change

- The overrun window's own arithmetic (`OverrunWindow.shiftPx`, `PanWindowRules.kt`) and the clamp.
- The pan gesture's commit threshold and the follow re-engage rule.
- The car surface, which is its own change.
- No exit code, no command line, no tracker label, and no capability other than `smooth-follow`.

## Surface

surface: 1 requirement modified in smooth-follow (2 scenarios added), 2 overlay call sites rewired, 1 helper
retired, 1 case added — `tools/look.sh 'markerDisplayShiftPx'` (on the base, 3 files name the helper:
`MapCanvasScreenParts.kt:326` (the definition), `MapCanvasMapBand.kt:618` and `:682` (the two call sites);
`app/src/test/java/com/naviveylin/ui/map/MarkerDisplayShiftTest.kt` pins its contract) and
`tools/look.sh 'anchorCenter('` (the follow projection at `MapCanvasMapBand.kt:641`). One case added
(`app: MarkerDisplayShiftTest.theOverlayShiftIsTheFramesShift`), one case rewritten
(`MarkerDisplayShiftTest`'s pan-side contract, which now asserts the derived viewport), one requirement
modified (`smooth-follow`, "Single resolved anchor across render, blit and marker").

## Out of scope

The car surface's pairing; the "one displayed viewport" type being shared between the two variants (a later
change, if the two derivations turn out to be the same function).
