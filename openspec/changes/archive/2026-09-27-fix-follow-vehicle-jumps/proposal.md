# Fix Follow-Mode Vehicle Jumps

## Why

Since the last release, follow-mode driving still shows periodic vehicle jumps: the vehicle advances on the map, then the map content (and/or the vehicle marker) jerks backward, then advances again — a cyclic "move, jump, move" pattern. Analysis of the follow pipeline (`FollowPrediction` + `MapCanvasScreen` display loop + `MapCanvasViewModel` fix collector + `MapRenderer` blit) identifies the root cause as **two independent centers of truth for the follow framing** — the per-fix re-center on the raw fix vs. the display-loop extrapolation/easing — amplified by a prediction-base mismatch (raw GPS vs. engine-snapped position) and a hard stop/go gate that resets the display offsets.

## What Changes

- **Single follow-center authority**: the per-fix path in `MapCanvasViewModel` stops re-committing the viewport center to the raw fix while the display extrapolation loop is active. A fix updates the prediction state only; the display loop owns the follow center (renders only when the blit offset reaches the overrun margin). This is the existing spec's own intent ("A fix SHALL update the state without necessarily triggering a render") and removes the dual render-center race (J2).
- **No backward display ease below the true fix**: on fix arrival the displayed position must never ease behind the raw (or engine-filtered) fix position. The periodic backward correction slide at every fix interval (J1, sawtooth) is eliminated by making the display forward-only relative to the latest fix.
- **One position source**: the display loop's prediction base (`FollowPrediction.update`) uses the same position the ViewModel renders with — the engine position (`navPos`) while route guidance is active, raw GPS otherwise — so the prediction and the rendered frame can no longer disagree (J3).
- **Stable stop/go gating**: the display loop keeps its offsets and displayed position frozen (not zeroed) while the vehicle is briefly below the movement threshold, and resumes seamlessly (J4).

No new capability. Behavior is modified inside the existing follow-mode pipeline; the change is **additive/behavioral** (no breaking API or data changes). Rollback = revert the follow-loop changes; rendering, gestures, navigation engine, and settings are untouched.

## Capabilities

### New Capabilities
- (none)

### Modified Capabilities
- `smooth-follow`: requirements **Correction easing**, **Prediction state update**, and **Display center extrapolation** change — the correction must not slide the display backward of the true fix at every fix cadence, a fix must not trigger a full-frame re-center, and the displayed position must be derived from the same position source that frames are rendered on.
- `gps-location-marker`: requirement **Marker position tracks map viewport** changes — the marker must keep riding the displayed position through the stop/go threshold instead of falling back to a raw-fix projection and snapping back.

## Impact

Affected files (phone renderer only — Android Auto has its own `auto-smooth-follow` path and is not in scope):

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — display extrapolation loop: forward-only easing, frozen offsets at stop, one position source (L~255-540, marker projection L~1090-1140)
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — per-fix follow path: stop re-centering on raw fix while the display loop owns the center (L~900-1090); `renderFollowFrameAt` stays as the only follow re-render entry
- `core/src/main/java/com/naviveylin/core/FollowPrediction.kt` — shared core: fix-arrival handling for forward-only prediction, optional position-source parameter (phone + AA share this class; AA behavior must stay unchanged — guard the change or keep the parameter additive)
- `core/src/test/java/com/naviveylin/core/FollowPredictionTest.kt` — new unit tests for the fix-arrival reset and forward-only rule
- App-level tests: `RoutePanelComposeTest.kt`/related Compose tests if marker assertions change (see classloader rules in AGENTS.md)

Related in-flight changes sharing this code region (coordinate during design/apply): `anchor-per-surface-visible-area`, `fix-phone-vehicle-anchor-framing`, `auto-pan-during-navigation`, `vehicle-position-presets`.

Guidelines affected: `guidelines/MapRendering.md` (render pipeline notes) — update if the follow render flow description changes; `guidelines/Design.md` §4 (threading/lifecycle) only if the display loop's frame cadence handling changes.
