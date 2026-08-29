# Proposal: continuous-pinch-zoom

## Change Name
`continuous-pinch-zoom`

## Why

Pinch-to-zoom currently commits only **integer** magnification levels: the gesture
zoom factor is rounded at gesture end (`round(log2(gestureZoom))`), clamped per
gesture to ±2 levels (`MAX_GESTURE_ZOOM = 4.0f`), and the visual preview is
clamped to the levels the integer commit can deliver. The result feels discontinuous
("stops quickly", "kicks a limit"), especially on coarse pinch input (emulator,
two-finger mouse emulation): small pinches often round to ±0 or ±1 level, and the
preview/commit mismatch produces visible snap-back at gesture end.

libosmscout natively supports **fractional** magnification — `osmscout::Magnification`
is a double scale factor (`SetMagnification(double)`, level derived as
`floor(log2(mag))`) — so continuous zoom is a plumbing change, not a renderer
limitation. Only the app/JNI layers coerce to `int`.

## What Changes

1. **Fractional magnification end-to-end for the render pipeline** (pinch path):
   `MapRenderer` viewport magnification and the JNI `render()` call carry a
   `Double` magnification (value = 2^z, fractional z allowed). Tile lookup snaps
   to `floor(log2(mag))` (native behavior, unchanged).
2. **Continuous pinch commit**: on gesture end, the accumulated gesture zoom is
   committed *unrounded* (`committedMag = gestureStartMag + log2(gestureZoom)`),
   so the rendered frame matches the gesture preview exactly — no rounding snap.
3. **Per-gesture cap raise**: pinch zoom factor cap raised from 4.0× (2 levels)
   to 16.0× (4 levels) per gesture; headroom clamping at MIN/MAG limits retained
   to guarantee preview == commit.
4. **Discrete inputs stay integer**: zoom buttons, keyboard, scroll wheel keep
   stepped ±1-level zoom (they already render through the smooth-zoom eased
   animation from change `smooth-zoom`; fractional values are rounded to the
   next level for these paths).
5. **Persistence**: `viewport.json` stores the fractional magnification as a
   double. Existing integer files load unchanged (int is a valid double).

Spec impact: **additive + breaking for internal API shapes** (magnification Int →
Double across `MapRenderer`, `ProjectionUtils`, viewport state), user-visible
behavior continuous pinch zoom. Rollback: revert commit (persistence files remain
readable; doubles fall back to the double-typed field).

## Affected specs

- `map-pan-zoom` — pinch zoom requirement rewritten (continuous commit, cap 16×,
  clamp text updated: min 4 / max 20 as implemented today)
- `viewport-persist` — magnification field type double
- `map-render` — render/JNI magnification is a double scale factor
- guideline `guidelines/MapRendering.md` — replace "integer magnification" statements
  in the render pipeline (checked during apply; update only if it pins Int mag)

## Affected files / modules

| Area | Files |
|------|-------|
| JNI bridge (submodule patch, minimal + upstreamable) | `osmscout-client-java` render + projection paths: `OSMScoutClient.java` (`render()` magnification int → double), `libosmscout-client-java/src/OSMScoutClient.cpp` (`SetLevel(uint32_t)` → `SetMagnification(double)` in the render/projection path only; description/lookup paths keep level semantics) |
| Renderer | `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` (`RenderViewport.mag`, render-call signatures, view change listener) |
| View model | `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` (viewport magnification Double, clamps, zoomIn/zoomOut snap logic) |
| Screen | `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` (gesture commit unrounded, `clampGestureVisualZoom` cap 16×, wheel/keyboard/buttons snap, `MIN_GESTURE_ZOOM/MAX_GESTURE_ZOOM` constants) |
| Projection helpers | `core/src/main/java/com/naviveylin/core/ProjectionUtils.kt` (`zoomAtCursor`, `dragDeltaToNewCenter*`, `computeScale` take fractional magnification) |
| Marker overlay | `app/src/main/java/com/naviveylin/ui/map/LocationMarkerOverlay.kt` (`computeScale` with fractional mag) |
| Persistence | viewport JSON model (magnification `Double`) |
| Auto module | `:auto` map renderer paths that mirror mag Int (audit; AAOS renders through the same native call) |

## Scope

- Phone/tablet + Auto/Automotive share the same map renderer; change is
  engine-level and applies to all form factors. No UI/surface changes.
- Android Auto map interaction parity is preserved automatically (same pipeline).

## Consequences

- Pinch zoom becomes continuous and matches its preview 1:1 at commit.
- Tile cache lookup level = floor level during fractional zoom — tiles from the
  lower level are used at the scaled projection (same behavior as placeholder
  scaling today, but as the actual render). Rendering cost unchanged at level
  boundaries changes.
- Viewport persistence files from older versions remain compatible.
- Discrete zoom behavior unchanged (stepped), so zoom-controls UX is stable.

## Guidelines affected

- `guidelines/MapRendering.md`: "on zoom change keep the old correct frame",
  integer-magnification assumptions, tile-level pinning statements — update in
  the same change if they contradict fractional magnification rendering.