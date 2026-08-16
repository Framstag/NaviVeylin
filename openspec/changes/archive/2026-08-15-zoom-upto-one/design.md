## Context

Today a single pair of constants controls every zoom path: `MIN_MAG = 4` and `MAX_MAG = 20` in `MapCanvasViewModel`'s companion object. They are applied by `updateMagnification()` (which backs the +/- buttons, keyboard `-`/`+`, and programmatic zoom), by the pinch/rotation gesture commit in `MapCanvasScreen.kt` (~line 357), by the mouse scroll-wheel handler (~line 403), and by auto-zoom. The gesture additionally has a visual relative clamp `MIN_GESTURE_ZOOM = 0.25f..MAX_GESTURE_ZOOM = 4.0f` (±2 levels) during the gesture, applied to the on-screen placeholder only.

Motivation and scope: see proposal.md — Why.

## Goals / Non-Goals

**Goals:**
- Zoom control (buttons, keyboard keys, scroll wheel) reaches magnification 1 (OSM-supported; zoom 0 would be whole world).
- Pinch/rotation gesture behavior stays exactly as specified today: clamped 4–20.
- Keep the two limit sets as named constants in one place so future changes are one-line edits.
- No behavior change to auto-zoom, fit-to-area zoom, or any native rendering.

**Non-Goals:**
- Not changing pinch/rotation gesture semantics, thresholds, or the ±2-level bound.
- Not lowering the floor to 0 (would be a one-constant change if ever wanted).
- Not touching `MIN_AREA_ZOOM = 14` (fit-to-bbox) or auto-zoom's speed table (12–17).
- Not fixing pre-existing spec drift (`map-pan-zoom` still says max 18 while code is 20).

## Decisions

**D1: Two minimum-zoom constants instead of one.**
`MIN_MAG = 1` (control/programmatic zoom) and new `GESTURE_MIN_MAG = 4` (gesture commit). `MAX_MAG = 20` stays shared.
- Rationale: the user wants the gesture's 4 floor preserved while the control range widens; one global constant cannot express both.
- Alternatives: single `MIN_MAG = 1` applied everywhere (rejected — gesture would zoom out below 4, violating the fix-two-finger-rotation contract the user wants kept); a `ZoomLimits` data class (rejected — overkill for two integers, and call sites reference constants directly).

**D2: `updateMagnification()` clamps to `MIN_MAG..MAX_MAG` (1–20).**
`zoomIn()`/`zoomOut()` delegate to it, so buttons, keyboard keys, and the scroll-wheel handler (which also calls `updateMagnification`) all inherit the 1 floor. `canZoomOut` in both screen layouts compares against `MIN_MAG`, so the button stays enabled until magnification 1.
- Rationale: one clamp site covers all control inputs; no per-input logic.

**D3: Gesture commit clamp switches to `GESTURE_MIN_MAG`.**
In `MapCanvasScreen.kt` the on-gesture-end commit (`newMag = (mag + zoomSteps).coerceIn(...)`) changes from `MIN_MAG` to `GESTURE_MIN_MAG`, keeping 4–20 for multi-touch gestures. The scroll-wheel handler keeps `MIN_MAG` — it is a discrete control input (like the buttons), not the rotation+pinch gesture.
- Rationale: the gesture is the only path that must keep the 4 floor; the wheel behaves like the control.

**D4: Gesture visual clamp `MIN_GESTURE_ZOOM`/`MAX_GESTURE_ZOOM` unchanged.**
It is a relative per-gesture factor on the placeholder, orthogonal to the absolute floor applied at commit. No change.

**D5: Auto-zoom keeps clamping to `MIN_MAG..MAX_MAG`.**
Speed table targets 12–17, always inside 1–20, so widening the clamp is a no-op for it. Keeping it on `MIN_MAG` avoids a third constant with no behavioral effect.

## Risks / Trade-offs

- [Rendering at magnification 1–3 may be heavy/slow] → libosmscout draws the whole region in one frame; low zoom levels are rarely visited, renderer is debounced (200 ms) and the canvas overrun buffer is unchanged. If perf issues appear, they are a separate follow-up, not a blocker for this change.
- [Map looks sparse/empty at zoom 1–3] → expected: basemap data density at low zooms is limited. Matches OSM behavior.
- [Spec text in `map-pan-zoom` (max 18) contradicts code (20)] → pre-existing drift, deliberately out of scope; `map-rotation-gesture` and `zoom-controls` deltas are the only spec changes here.
- [Regression risk: some code path still clamps to 4] → the two clamps are the only sites referencing minimum zoom; gesture commit is switched to `GESTURE_MIN_MAG` explicitly and covered by the existing `MapGestureComposeTest` clamp assertions.

## Migration Plan

Pure constant/expression change in two Kotlin files. No data migration, no native rebuild. Rollback: revert the two constants and the one `coerceIn` call — gesture and control limits return to 4–20.

## Open Questions

None.
