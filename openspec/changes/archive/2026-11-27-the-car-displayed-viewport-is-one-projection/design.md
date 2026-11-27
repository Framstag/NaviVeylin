# Design — the car's displayed viewport is one projection

## The defect

The car's follow path states one display rule three times. `AutoMapRendererMotion.kt:193` computes the blit
offset with `OverrunWindow.shiftPx(...)` against `resolvedFollowAnchor()`; `:219` moves the frame with
`anchorCenterFor(displayLat, displayLon)`. `AutoMapRendererRender.kt:84` computes the blit offset again on the
render path, and `:154` calls `anchorCenterFor(dLat, dLon)` again. The marker's own draw offset is a third
reading at `:276`.

Each site must be handed the same resolved fraction, and each is handed it separately. The shipped requirement
pins the *value* — "the same **resolved** anchor screen fraction the AA frame render target uses" — so a site
that is handed the raw preset reddens a scenario. But the derivation's singularity is not pinned: a change that
keeps all three sites consistent while adding a fourth, or that moves the shift into a helper with its own
branch, is invisible to the suite.

No case fails today. The duplication is the defect, and the invariant it prevents asserting is "the overlay's
position is the frame's position plus the blit offset".

## The single fix

Compute the **displayed viewport** once per tick — the frame viewport shifted by the clamped overrun offset —
and have the blit, the marker's draw offset and the frame's commit read it. `anchorCenterFor` stays as the
derivation's input: it produces the frame's anchor-centred commit, and the derivation takes that commit as its
input instead of the blit recomputing a shift from the resolved fraction.

The three sites become one read plus three uses. The resolved fraction is then read in one place, so the "single
resolved anchor" requirement is satisfied by construction rather than by three sites agreeing.

## What stays as it was, and why

- **`OverrunWindow.shiftPx` and the clamp.** The change reads the offset; it does not compute it. The
  blit-versus-full-render decision and its throttle are untouched.
- **`clampAnchorOutOfPane`.** The pane-band resolution is the derivation's input, not a second mechanism.
- **The marker-to-content glue.** The marker rides the blitted content at the resolved anchor, as the shipped
  scenarios require; the change keeps both verbatim.
- **The phone surface.** Its pairing is in different files and is a sibling change.

## Blast radius

`AutoMapRendererMotion.kt` (the blit site and the commit), `AutoMapRendererRender.kt` (the render-path blit, the
render-path commit and the marker's draw offset), and the tests that pin the blit contract
(`AutoMapRendererBlitTest`, `AutoMapRendererFollowAnchorTest`), plus the new case
`AutoMapRendererDisplayedViewportTest`. No command line, no exit code, no capability
other than `auto-smooth-follow`.

One file outside `auto/` moves with it: `architecture/src/test/kotlin/com/naviveylin/architecture/StructureReportTest.kt`
states the census of classified source files as a literal (`files=860`), and the new test file is one more source
file of the three modules, so the literal becomes `files=861` in both places that carry it.

## Alternatives the reading eliminated

- **Keep the three sites and add a shared helper they all call.** That is the same defect with one more layer:
  the helper still returns a shift three sites apply, and a fourth site can still be added.
- **Assert the invariant without changing the code.** A case asserting "the marker's position equals the frame's
  position plus the blit offset" passes on HEAD by construction, because the three sites are already consistent.
  A case that cannot fail is not a red case, and the change owes one.
- **Unify both surfaces in one change.** The phone's mechanisms are in `MapCanvasScreenParts.kt` and
  `MapCanvasMapBand.kt`; one change across both would owe two capability deltas and a red case on each, which is
  the size `#706` was split to avoid.

## Rollback

Revert the two main files and the two test files: `AutoMapRendererMotion.kt`, `AutoMapRendererRender.kt`, and the
blit contracts in `AutoMapRendererBlitTest` and `AutoMapRendererFollowAnchorTest`. Nothing is migrated and no
state is written.

## Open decisions

none

## Surface

surface: 1 requirement modified in auto-smooth-follow (2 scenarios added), 3 call sites rewired, 1 case added —
`tools/look.sh 'anchorCenterFor'` (on the base, 6 files name it: `AutoMapRendererMotion.kt:219`,
`AutoMapRendererRender.kt:154`, `AutoMapRenderer.kt:542`, `:718`, `:772`, and the definition at
`AutoMapRendererFollow.kt:94`) and `tools/look.sh 'OverrunWindow.shiftPx'` (3 sites: `AutoMapRendererMotion.kt:193`,
`AutoMapRendererRender.kt:84`, `:276`). One case added (`auto#theCarOverlayOffsetIsTheFramesShift`), one
requirement modified (`auto-smooth-follow`, "Single resolved anchor in the AA follow blit").

## Verification

The case `auto#theCarOverlayOffsetIsTheFramesShift` asserts the derivation's singularity — the marker's offset is
the blit's offset, read once. HEAD cannot express it, because the three sites each recompute the shift, so the
case's red proof is the falsification step rather than a base run: the mutation restores the per-site
recomputation and the case must redden, then the mutation is reverted and the case must go green (`TODO.md` §113,
the `revert-check` discipline). The shipped scenarios in `AutoMapRendererBlitTest` and
`AutoMapRendererFollowAnchorTest` stay green, which is what proves the change moved no behaviour.
