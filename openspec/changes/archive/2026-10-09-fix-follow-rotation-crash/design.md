# Design

## Context

`FollowPrediction.displayOffsetPx` (`core/src/main/java/com/naviveylin/core/FollowPrediction.kt:290-319`)
is the single seam that turns a displayed geographic position into the follow blit offset, clamped to
the overrun margin of the frame in hand. Six call sites use it: the phone follow blit and marker shift
(`MapCanvasScreen.kt:747`, `MapRenderer.kt:1096`), the pan display window (`PanWindowRules.kt:72`) and
the car blit/extrapolation paths (`AutoMapRenderer.kt:1153`, `:1277`, `:1469`).

Rotation is an async handoff: at configuration change the canvas adopts the new dimensions immediately
while the displayed bitmap still carries the old orientation until the committed render lands
(`guidelines/MapRendering.md` §14). During that window `(bitmapW - canvasW) / 2.0` is negative on the
swapped axis and `coerceIn(-margin, margin)` is an empty range, which Kotlin rejects with
`IllegalArgumentException`. The exception escapes the main-thread draw path, so the process dies.

`guidelines/MapRendering.md` §13 owns the clamp rule and already treats "a frame without an overrun
margin" as **unservable** for the coverage predicate — but not for the offset arithmetic.

## Goals / Non-Goals

Goals:

- Rotating during follow never throws; the app re-renders in the new orientation.
- Same-orientation behaviour is byte-identical (no framing change, no new render churn).
- The fix is verifiable as a pure function, without a device.

Non-Goals:

- Changing the rotation handoff, the overrun window size, or the resolved-anchor rules.
- Any native/JNI change.
- A car-specific behaviour requirement (the car surface shares the helper and inherits the guard, but
  its framing contract is unchanged).

## Decisions

### D1 — The guard lives in the pure helper, not at the call sites

**Chosen.** Clamp the margin inside `displayOffsetPx`.

*Alternatives.* (a) Guard each of the six call sites: six chances to forget one, and the car paths are
not exercised by the phone rotation path, so a missed site stays latent. (b) Guard in
`ProjectionUtils`/the caller that owns the canvas dimensions: the helper cannot then be total, and the
unit seam (`FollowPredictionTest`) no longer covers the real risk.

Rationale: the helper is the one place every follow offset passes through, it is already the unit-tested
seam, and making it a total function is the smallest change that cannot leave a caller exposed.

### D2 — A non-positive margin clamps to zero and reports `clamped`, rather than returning the raw drift or `null`

**Chosen.** `marginX = ((bitmapW - canvasW) / 2.0).coerceAtLeast(0.0)`, so the mismatched axis yields
`clampedX = 0.0` and `DisplayOffset.clamped = true` whenever the raw drift was non-zero.

*Alternatives.* (a) Return the raw offset when the margin is non-positive: the offset is then unbounded
on that axis, so the blit can paint an uncovered strip of `surfaceColor` — the exact defect the clamp
exists to prevent (`guidelines/MapRendering.md` §13). (b) Return `null` / a "frame not usable" result:
a signature change across six call sites and a new refusal path each must handle, for a state that
already has a signal (`clamped`) the phone caller acts on. (c) Skip the clamp entirely on that axis:
same uncovered-strip risk as (a).

Rationale: zero is the only offset that is always inside a zero-width margin; it is also what the
render-request convention expects (`MapCanvasScreen.kt:740-745` requests a render when the offset
clamps), so the guard does not merely avoid the crash — it produces the re-render that ends the
transition. `clamped` staying a pure derivation (`clampedX != rawX || …`) means the contract and every
existing assertion are untouched.

### D3 — Keep the axis-symmetric form; do not special-case the rotation path

**Chosen.** Guard `marginX` and `marginY` identically in the helper.

*Alternative.* Detect "the frame orientation differs from the canvas" at the screen level and skip the
follow offset for that one frame. Rationale against: the screen does not know the frame's orientation
authoritatively (the renderer owns `EmittedFrame`), the negative margin can also arise from a zoom that
outgrows the canvas on one axis, and a screen-level skip again leaves the car and pan callers exposed.

## Threading & Lifecycle

`displayOffsetPx` is a pure function called on the draw/collector path (main thread for the phone
follow loop, the renderer's collector for the car). It holds no state, starts no coroutine and touches
no lifecycle. The guard adds two lines of arithmetic, so the per-frame allocation and cost are
unchanged.

## Risks

| Risk | Assessment | Mitigation |
|---|---|---|
| Zeroing the drift on the transition axis nudges the displayed content for one frame | Low: the frame is about to be replaced; the anchor-centered frame is re-requested immediately because `clamped` is true | Device rotation check (task 4.3); the same-orientation control case (task 1.2) proves nothing else moved |
| A caller treats `clamped` as "keep scrolling" and now requests a render it previously did not | Intended: a mismatched frame cannot be displayed correctly anyway | Assert the `clamped` flag in the swapped-orientation case (task 2.1) |
| The guideline rule drifts from the code | Low | Task 3.1 updates `guidelines/MapRendering.md` §13 in the same change |

## Verification

Unit (no device):

- `core/src/test/java/com/naviveylin/core/FollowPredictionTest.kt`: portrait bitmap (1296×2880) against
  landscape canvas (2400×1080) and the reverse — no throw, both axes finite, the margin-less axis
  exactly `0.0`, `clamped == true`; plus a same-orientation case that reproduces the existing margin
  clamp unchanged. Names the spec scenarios "The phone is rotated while follow mode is active", "Both
  orientation transitions are safe", "A frame with overrun margin keeps today's clamp".
- Revert-check: restore the bare margin (mutation) → the swapped-orientation case must fail with
  `IllegalArgumentException: Cannot coerce value to an empty range`; restore; forced green with
  `-PforceTests --no-build-cache` (`guidelines/Build.md` §2/§4, `revert-check` skill).
- `clamped`-drives-a-render: extend the phone framing case to the mismatched orientation so the
  render-request consequence is asserted, not assumed.

On-device (device-gated — `adb devices` is empty today; the blocker is recorded, not implied):

- With navigation running and follow engaged, `adb shell settings put system user_rotation 1` and then
  `… user_rotation 0`. The process must survive; `adb logcat -s NaviVeylin` must show no
  `FATAL EXCEPTION` naming `FollowPrediction.displayOffsetPx`, and the map must re-render in each new
  orientation. A stationary emulator cannot produce follow *drift*, but the orientation swap is the
  trigger here, so this measurement is valid on a stationary AVD (state in the task that the drift
  framing itself is not measured).
