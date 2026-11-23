# Design — the displayed viewport is one projection

## The defect

`markerDisplayShiftPx` (`app/src/main/java/com/naviveylin/ui/map/MapCanvasScreenParts.kt:326-331`) is a mode
test wearing a geometry's name:

```kotlin
internal fun markerDisplayShiftPx(
    followActive: Boolean,
    panOffsetX: Float,
    panOffsetY: Float
): Pair<Float, Float> =
    if (followActive) 0f to 0f else -panOffsetX to -panOffsetY
```

Its two call sites are the band's only two overlays — `RouteSegmentHighlightOverlay`
(`MapCanvasMapBand.kt:610`, viewport `state.renderViewport` at `:614`) and `LocationMarkerOverlay`
(`:667`, viewport `markerViewport` at `:672`) — and each applies the result as a `graphicsLayer`
translation (`:618`, `:682`). The follow branch's zero is correct only because the follow projection
(`:641`, `anchorCenter(...)`) has already moved the overlay's own viewport to the anchor centre of the
displayed position. So the rule "the overlay's shift is the frame's shift" is answered in two places, and the
answer is selected by a boolean rather than by the geometry.

No case fails today: a pan disengages follow, so the disagreement is unreachable. The duplication is the
defect — the invariant is carried by a mode test, so it cannot be asserted.

## The single fix

Compute the **displayed viewport** once per frame and hand it to every overlay: the frame viewport shifted by
the clamped overrun offset, derived through `ProjectionUtils.screenToGeoRotated` so the shift is a geo
translation rather than a pixel translate. Both modes read that one value:

- **Pan** — the frame viewport is the committed viewport; the displayed viewport is it shifted by the clamped
  offset. The overlay needs no `graphicsLayer` translate, because its viewport already carries the shift.
- **Follow** — the frame viewport is the anchor-centred viewport; the displayed viewport is *that* shifted by
  the clamped offset, which is the same derivation with a different input. The overlay's own anchor-centre
  projection at `:641` is retired: the frame's own viewport is the derivation's follow input, so the displayed
  position still lands on the anchor while the offset is applied by the one derivation and not by a translate.

`markerDisplayShiftPx` and the two `graphicsLayer` blocks go. The mode no longer selects a projection: it
selects an input.

## What stays as it was, and why

- **`OverrunWindow.shiftPx` and the clamp.** The change reads the offset; it does not compute it. The pan
  window's own arithmetic and the clamp are unchanged.
- **The pan gesture's commit threshold and the follow re-engage rule.** A pan still disengages follow; the
  change removes the *need* for the two mechanisms to be exclusive, not the exclusivity itself.
- **The marker-to-content glue.** The marker lands on the road at the resolved anchor in both modes, as
  `smooth-follow` "Single resolved anchor across render, blit and marker" already requires. The change keeps
  that requirement's scenarios and adds the derivation's own.
- **The car surface.** Its pairing is in different files and is a sibling change.

## Blast radius

`MapCanvasScreenParts.kt` (the helper and the derived viewport), `MapCanvasMapBand.kt` (the two overlay call
sites and the follow projection), and the tests that pin the pan-side contract (`MarkerDisplayShiftTest`,
`MapPanDisplayWindowTest`). No command line, no exit code, no tracker label, no capability other than
`smooth-follow`.

## Alternatives the reading eliminated

- **Keep the helper and add a third branch.** A mode test with three arms is the same defect with more surface;
  the invariant stays unassertable because the branches still cannot be compared.
- **Assert the invariant without changing the code.** A case asserting "the overlay's shift equals the frame's
  shift" passes on HEAD by construction, because the pan branch is the only branch reachable with a non-zero
  offset. A case that cannot fail is not a red case, and the change owes one.
- **Unify both surfaces in one change.** The car's mechanisms are in `AutoMapRendererMotion.kt` and
  `AutoMapRendererRender.kt`; one change across both would owe two capability deltas and a red case on each,
  which is the size `#706` was split to avoid.

## Rollback

Revert the two files and the two test files: `MapCanvasScreenParts.kt`, `MapCanvasMapBand.kt`, and the pan-side
contracts in `MarkerDisplayShiftTest` and `MapPanDisplayWindowTest`. Nothing is migrated and no state is
written.

## Open decisions

none

## Surface

`surface: 1 requirement modified in smooth-follow (2 scenarios added), 2 overlay call sites rewired, 1 helper
retired, 1 case added — tools/look.sh 'markerDisplayShiftPx'` (on the base, 3 files name the helper:
`MapCanvasScreenParts.kt:326` (the definition), `MapCanvasMapBand.kt:618` and `:682` (the two call sites);
`app/src/test/java/com/naviveylin/ui/map/MarkerDisplayShiftTest.kt` pins its contract) and `tools/look.sh
'anchorCenter('` (the follow projection at `MapCanvasMapBand.kt:641`). One case added
(`app: MarkerDisplayShiftTest.theOverlayShiftIsTheFramesShift`), one case rewritten (`MarkerDisplayShiftTest`'s pan-side contract,
which now asserts the derived viewport), one requirement modified (`smooth-follow`, "Single resolved anchor
across render, blit and marker").

## Verification

The case `app: MarkerDisplayShiftTest.theOverlayShiftIsTheFramesShift` asserts the derivation's singularity — the same offset with the
mode set either way. HEAD cannot express it, because the shift is selected by a mode test, so the case's red
proof is the falsification step rather than a base run: the mutation restores the mode test (the `if` in
`markerDisplayShiftPx`) and the case must redden, then the mutation is reverted and the case must go green
(`TODO.md` §113, the `revert-check` discipline). The pan-side cases in `MapPanDisplayWindowTest` and the
follow-side cases in `FollowAnchorFramingTest` stay green, which is what proves the change moved no behaviour.
