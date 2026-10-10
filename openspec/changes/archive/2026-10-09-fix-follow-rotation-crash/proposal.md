# Proposal

## Why

Rotating the phone while follow mode is active **kills the process**:

```
java.lang.IllegalArgumentException: Cannot coerce value to an empty range:
  maximum -552.0 is less than minimum 552.0
  at com.naviveylin.core.FollowPrediction$Companion.displayOffsetPx(FollowPrediction.kt:317)
  at com.naviveylin.ui.map.MapCanvasScreenKt…  (FATAL EXCEPTION: main)
```

Reproduced 2026-10-07 on the `Pixel_8` AVD with navigation running and follow engaged
(`adb shell settings put system user_rotation 1`), filed as `TODO.md` §153.

`FollowPrediction.displayOffsetPx` clamps the follow display drift to the frame's overrun margin:

```kotlin
val marginX = (bitmapW - canvasW) / 2.0     // FollowPrediction.kt:307
val marginY = (bitmapH - canvasH) / 2.0
…
driftX.coerceIn(-marginX, marginX)          // :317
```

During an orientation change the **displayed** bitmap still carries the previous orientation
(1296×2880 portrait) while the canvas already has the new one (2400×1080 landscape), so
`marginX = (1296 - 2400) / 2.0 = -552` and `coerceIn(552.0, -552.0)` is an empty range — Kotlin
throws. Rotating back and forth during a drive therefore crashes the app instead of re-rendering
the frame in the new orientation.

The clamp is the single display-offset seam: `MapCanvasScreen` (follow blit), `MapRenderer`,
`PanWindowRules` and the car's `AutoMapRenderer` all call it. The guideline that owns the clamp,
`guidelines/MapRendering.md` §13, already states that a *coverage* decision must treat "a frame
without an overrun margin" as unservable — but the arithmetic that decides the offset itself assumes
the margin is positive. Nothing in the code or the specs says what the offset is when the margin is
not.

## What Changes

- Make the overrun margin in `FollowPrediction.displayOffsetPx` **non-negative**: a frame that
  offers no overrun margin on an axis clamps the drift on that axis to `0.0` instead of constructing
  an empty range. The `DisplayOffset` contract (`rawX/rawY`, `clampedX/clampedY`, `clamped`) is
  unchanged; `clamped` becomes `true` for such a frame, which is exactly the signal the phone caller
  already uses to request a render (`MapCanvasScreen.kt:745+`, "request a render when clamped so the
  map keeps scrolling").
- Deliberately **not** in scope: changing the rotation handoff itself (a full render is already
  requested by the rotation commit, `guidelines/MapRendering.md` §14), changing the overrun window
  size, and any change to the resolved-anchor rules.
- One line added to `guidelines/MapRendering.md` §13 stating the margin is never negative and that a
  frame from the previous orientation yields a zero offset flagged clamped.

**Not BREAKING.** The change is a total-function guard on a pure helper: the same-orientation
behaviour is byte-identical, no API changes, no persisted state changes, no user flow is removed.
Rollback: revert the change — the helper returns to the bare margin and the pre-change behaviour
(including the crash) is restored verbatim.

**Scope: phone.** The crash is the phone follow loop. The guard lives in the shared `:core` helper, so
the car renderer (`AutoMapRenderer`) is protected by the same code, but no car behaviour requirement
changes and the car surface is not part of the acceptance criteria.

## Capabilities

### New Capabilities

None. The non-negative-margin contract belongs to the follow display offset that `smooth-follow`
already owns.

### Modified Capabilities

- `smooth-follow`: adds the requirement that the follow display offset is computed against a
  non-negative overrun margin, so an orientation change between the displayed frame and the canvas
  yields a zero offset on the margin-less axis (reported as clamped) and never fails. The existing
  "Anchor-centered follow framing" and "Every preset stays inside the overrun buffer" behaviour is
  otherwise unchanged.

Specs deliberately left unchanged: `auto-smooth-follow` (the car renderer shares the helper but its
own framing contract does not change), `map-modes`, `smooth-zoom`.

## Impact

Code:

- `core/src/main/java/com/naviveylin/core/FollowPrediction.kt` — the whole fix: two lines in
  `displayOffsetPx` plus KDoc.
- `core/src/test/java/com/naviveylin/core/FollowPredictionTest.kt` — new cases for the swapped
  orientations and a control case for the unchanged margin.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` (`:747`), `MapRenderer.kt` (`:1096`),
  `PanWindowRules.kt` (`:72`), `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt`
  (`:1153`, `:1277`, `:1469`) — call sites only; they must need no change (the guard is inside the
  helper).
- `app/src/test/java/com/naviveylin/ui/map/` — `FollowAnchorFramingTest`, `MapPanDisplayWindowTest`
  must stay green; the `clamped`-drives-a-render assertion is extended to the mismatched orientation.
- `auto/src/test/java/com/naviveylin/auto/AutoMapRendererTest.kt` — its `displayOffsetPx` cases must
  stay green.

Documentation:

- `guidelines/MapRendering.md` §13 ("Overrun Display Window (pan and follow)") — one added rule.
- `TODO.md` §153 — removed when this change is archived.

Native/JNI: none. No submodule patch, no bridge change.
