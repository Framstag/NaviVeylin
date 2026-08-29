# Design: continuous-pinch-zoom

## Context

Pinch zoom today is level-quantized: `MapCanvasScreen` accumulates a continuous
`gestureZoom` during the drag (visual preview on the front buffer, anchored at
the centroid), but at gesture end only `round(log2(gestureZoom))` levels are
committed, and the per-gesture factor is capped at 4.0 with `clampGestureVisualZoom`
mirroring exactly what the integer commit can deliver. Users experience discrete
jumps, small pinches rounding to no-op, and snap-back mismatches on coarse input.

libosmscout supports arbitrary magnification: `osmscout::Magnification` wraps a
`double` scale factor; `MercatorProjection::Set()` accepts it directly and
`level` is derived as `floor(log2(magnification))` for all level-based lookups
(tile cache, stylesheets, feature activation). The JNI bridge is the only place
that coerces to `int level` (`SetLevel(uint32_t(mag))` in
`libosmscout-client-java/src/OSMScoutClient.cpp:1094`).

Smooth-zoom (landed) owns the *display* animation for discrete zoom; this change
owns the *committed* magnification becoming fractional. The two compose: gesture
end → continuous commit → render → smooth-zoom handoff logic already handles
render-land crossfade.

## Goals / Non-Goals

**Goals**
- Continuous (fractional) committed magnification from pinch gestures.
- Gesture preview == committed frame (no rounding snap at gesture end).
- Per-gesture headroom cap raise (4× → 16×).
- Double magnification through `MapRenderer` + JNI render path.

**Non-Goals**
- No continuous rendering *during* the pinch (visual preview stays the scaled
  front buffer from change `smooth-zoom`/`map-pan-zoom`; native render runs once
  at gesture end) — avoiding continuous-zoom render pumping (performance).
- No changes to discrete input behavior (buttons/keyboard/wheel stay ±1 level).
- No stylesheet/feature-density work at fractional levels (native already snaps
  to floor level).
- No double-tap zoom.

## Decisions

### D1: Magnification becomes a Double scale factor (2^z), level = floor(log2)

`MapRenderer`/`ViewPort`/`ProjectionUtils` carry `mag: Double` where 2.0 = one
level. `zoomAtCursor`, `dragDeltaToNewCenter*`, `computeScale` switch param types
from `Int` to `Double`; formulas unchanged (they already treat mag as exponent /
scale factor).

- Why: libosmscout semantic is a continuous scale factor; matching it 1:1 avoids
  conversion bugs and keeps tile level derivation in one place (native).
- Alternative (rejected): keep `Int` internally, compute fractional only for the
  render call — splits "displayed magnification" semantics between layers, same
  class of bug as the smooth-zoom display scale split we just removed with
  type-level guarantees (see smooth-zoom task 4.1). Double end-to-end is the
  type-level guarantee.

### D2: JNI render path takes double magnification (minimal submodule patch)

Patch `libosmscout-client-java` (submodule, minimal + upstreamable): change
`native ... render(...)` and the C++ render/entry JNI functions to accept
`double magnification`, using `magnification.SetMagnification(mag)` instead of
`SetLevel(uint32_t(mag))`. All other entry points (`getDescription`,
`getObjectBoundingBox`, tile/lookup APIs) keep `int` level — they are
point/POI queries, not rendered viewports, and callers convert with
`floor(log2(mag))` where needed.

- Why minimal: JNI surface elsewhere is level-semantic; converting only the
  render path keeps the upstreamable diff tiny (2 lines + signature).
- Alternative (rejected): convert every JNI entry to double — larger upstream
  diff, no benefit, risk of accidental fractional level in lookup logic.
- Per AGENTS.md conventions: this is a **submodule patch (minimal,
  upstreamable)**, not a local override in the bridge module.

### D3: Gesture commit unrounded; clamp caps guarantee preview == commit

At gesture end: `committedMag = gestureStartMag + log2(gestureZoom)` (no rounding).
`clampGestureVisualZoom` keeps coercing the *visual* factor into the headroom
`[GESTURE_MIN_MAG − mag, MAX_MAG − mag]` as powers of two, so the unrounded commit
always lands inside `[MIN_MAG, MAX_MAG]` and equals the preview. Cap raised to
`MAX_GESTURE_ZOOM = 16.0` (4 levels per pinch); `MIN_GESTURE_ZOOM = 1/16`.

- Why: preview/commit equality is the entire point of fractional zoom; keeping
  the headroom clamp (instead of clamping only at commit) preserves the existing
  no-snap-back invariant.
- Alternative (rejected): clamp at commit instead of preview — restores
  snap-back when the user pinches past a limit, the exact bug this change fixes.

### D4: Discrete paths snap to levels, one conversion at the ViewModel boundary

Buttons/keyboard/wheel call `zoomIn()/zoomOut()` which operate on
`round(committedMag) ± 1` and produce an integer target — the smooth-zoom
animation then eases the display to `2^(targetInt − frontMagDouble)`, math
unchanged. Screen displays `floor(committedMag)` as the level number
(zoom-controls display format unchanged).

- Why: keeps zoom-controls spec (stepped levels) untouched per proposal.
- Alternative (rejected): fractional wheel zoom (smooth like browsers) — scope
  creep; wheel path can adopt it later without spec break.

### D5: Threading/lifecycle — no new components

No new components: magnification type change flows through existing
`MapRenderer` debounce pipeline, `ViewModel` main-immediate state + `Dispatchers.IO`
persistence (per guidelines/Design.md §4). The only new code is the commit math
in the existing gesture callback and clamping helpers. JNI calls stay on the
existing dedicated render dispatcher.

## Risk assessment

- [Tile lookup box computed at fractional mag may select wrong tiles mid-level]
  → native `LookupTiles` derives the tile zoom from `magnification` level
  (`floor(log2)`), which is the intended behavior; verify visually at mag 17.5
  that street geometry renders without holes (task 6.1).
- [Placeholder crossfade math (smooth-zoom D4) used integer front-buffer mag]
  → `frontBufferMag` becomes Double; alignment check `2^(committed − front)`
  works for fractional values unchanged. Unit-tested via ZoomAnimationTest + new
  tests.
- [Zoom label display shows floor level, e.g. "17" while committed 17.9] →
  intentional (levels stable for users); spec'd in zoom-controls delta scenario.
- [Persistence migration] → old int files parse as JSON numbers into Double
  (kotlinx.serialization tolerant); new files write e.g. `17.412`. Covered by
  ViewportStorageTest extension.
- [Auto/Automotive regressions] → `:auto` renders through the same `MapRenderer`;
  full `:auto` unit tests + build gate.

## Verification

- Unit: `ProjectionUtilsTest` fractional-mag cases; ViewModel clamp/snap tests;
  `ViewportStorageTest` double round-trip; ZoomAnimation/ZoomControlsAnimationTest
  adjust `frontMag` to Double.
- Native: JNI signature change compiles for all 3 ABIs via
  `./gradlew :app:assembleMobileDebug` (CMake builds the submodule target
  `osmscout_client_java`).
- On-device/emulator: continuous pinch (mouse + Shift on emulator produces
  two-finger pinch), preview-to-final frame equality, logcat render cadence
  unchanged (single render at gesture end), zoom buttons unchanged.