# Fix AA Follow-Mode Vehicle Jumps

## Why

Android Auto follow mode still shows periodic vehicle jumps — the same bug class the phone change (`fix-follow-vehicle-jumps`) eliminated, still live in the AA follow pipeline (`AutoMapRenderer` extrapolation loop + `FollowPrediction`). The phone fix explicitly scoped AA out ("AA keeps raw-fix behavior"); analysis of `AutoMapRenderer.kt` shows three live defect sources: (1) the display eases toward the extrapolated prediction without a forward-only rule, so every fix arrival that resets the prediction base behind the display slides the vehicle backward over ~300 ms (the 1 Hz sawtooth); (2) the loop gate-off zeroes the displayed position (`displayLat = NaN`) so the marker falls back to the raw fix and re-anchors on every stationary fix — stop/resume snaps, and it violates the existing stop behavior contract ("displayed viewport SHALL remain at the last position"); (3) the margin full-render request is throttled at 500 ms + 100 ms debounce (phone: 200 ms), so at speed the frame freezes then teleports by `v × wait` on every overrun-margin hit.

## What Changes

- **Forward-only displayed position (AA)**: the AA extrapolation loop adopts the same monotonic-forward rule the phone now uses (`FollowDisplayState` in shared core — hold-when-target-behind on fix arrival, teleport snap beyond 50 m). The displayed position never slides backward along the direction of travel; residual lead stays bounded (≤ v·τ while moving), no per-fix backward correction slide.
- **Frozen stop/go gate (AA)**: the gate-off branch keeps the displayed position and marker frozen (no `displayLat = NaN` reset, no marker fallback to the raw fix); a stationary fix re-seeds the display to the fix ONCE per stop episode (moving→stopped transition) instead of re-anchoring the viewport on every jittery fix. Resume continues from the frozen position — no stop or resume snap.
- **Margin full-render throttle parity**: `RENDER_REQUEST_INTERVAL_MS` drops from 500 ms to 200 ms (matching the phone's `GPS_FOLLOW_RENDER_INTERVAL_MS`), shrinking the freeze-then-advance step at speed. Renderer mechanics (overrun buffer, sub-region blit, resolved-anchor blit offsets) are untouched.
- No engine/storage/API changes: AA stays single-source (raw GPS fixes — no engine snapped position exists in the AA renderer), `FollowPrediction` itself is untouched (the hold rule lives in `FollowDisplayState` at the call site).

## Capabilities

### New Capabilities
- (none)

### Modified Capabilities
- `auto-smooth-follow`: requirements **Correction easing** / **Extrapolation loop gating** change — the fix-arrival correction must never slide the displayed position backward of the true fix line (forward-only hold), and the stopped gate must keep the displayed position and marker frozen at the last position (no reset to the raw fix, no per-fix re-anchoring while stationary).

## Impact

- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt`:
  - `extrapolationTick` L~696-762 — inline ease `display += (predicted − display)·α` becomes `FollowDisplayState.advance(predicted, dtSec, headingDeg)` (heading = the fix's effective bearing; NaN → rule skipped, same as phone).
  - `startExtrapolationLoop` gate-off branch L~653-663 — drop the `displayLat/Lon = NaN` reset; keep the display frozen.
  - `setGpsMarker` follow/stationary branch L~331-350 — remove the every-fix viewport re-anchor on stationary fixes; re-seed display to the fix once per moving→stopped transition, then freeze.
  - `RENDER_REQUEST_INTERVAL_MS` L~1380 — 500 → 200 ms.
- `core/src/main/java/com/naviveylin/core/FollowDisplayState.kt` — reuse the existing class (11 unit tests); update the "phone-only" comment to reflect AA adoption.
- `auto/src/test/java/com/naviveylin/auto/AutoMapRendererTest.kt` — new regression tests: fix-arrival hold (no backward `disp` delta), gate-off freeze (no NaN, marker stays at the frozen position), stationary jitter fix does not re-anchor, stop/resume seamless, margin render not requested faster than the (tuned) interval.
- Guidelines: `guidelines/MapRendering.md` §1.1 AA follow paragraph — extend the shared "single follow center / frozen stop" rule to the AA loop (same doc the blit-anchor change touches — coordinate).

Related in-flight changes sharing this code region: `fix-aa-follow-blit-anchor-mismatch` (8/9 — resolved-anchor blit call sites at L714-726/L783-796; do NOT touch those lines, this change edits the ease/gate around them), `fix-aa-vehicle-anchor-not-applied` (anchor persists/applies — distinct symptom, no interaction), `anchor-per-surface-visible-area` (28/36 — AA anchor resolution already landed in the tree).

Guidelines affected: `guidelines/MapRendering.md` — AA follow-flow statement update (same paragraph as the blit-anchor change, merge into one edit).

Rollback: revert the `AutoMapRenderer.kt` edits; rendering, gestures, navigation, settings untouched.
