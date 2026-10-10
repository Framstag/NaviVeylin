# Apply Evidence

Change: `fix-follow-rotation-crash` — 2026-10-09.

## Code

- `core/src/main/java/com/naviveylin/core/FollowPrediction.kt` — `displayOffsetPx` now coerces both
  overrun margins with `coerceAtLeast(0.0)` (`:317-318`); KDoc states a margin-less axis yields a zero
  offset reported as `clamped`.
- No call site changed (`MapCanvasScreen.kt:747`, `MapRenderer.kt:1096`, `PanWindowRules.kt:72`,
  `AutoMapRenderer.kt:1153,1277,1469`).

## Unit evidence

`./gradlew :core:testDebugUnitTest --tests "com.naviveylin.core.FollowPredictionTest" -PforceTests --no-build-cache`
→ `BUILD SUCCESSFUL`, `tests="27" skipped="0" failures="0" errors="0"`. New cases:

- `portrait frame against a landscape canvas yields a zero offset on the margin-less axis`
- `landscape frame against a portrait canvas yields a zero offset on the margin-less axis`
- `a frame with an overrun margin keeps the existing clamp`

`./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.*" -PforceTests --no-build-cache`
→ `BUILD SUCCESSFUL`, `FollowAnchorFramingTest` `tests="10" failures="0"` including the new
`a frame from the previous orientation reports clamped so the screen requests a render`.

## Revert-check (task 1.3)

Mutation: both margins restored to the bare `(bitmapW - canvasW) / 2.0` (guard removed).
Result: `tests="27" failures="2"` —

```
java.lang.IllegalArgumentException: Cannot coerce value to an empty range:
  maximum -552.0 is less than minimum 552.0.   (portrait frame / landscape canvas)
java.lang.IllegalArgumentException: Cannot coerce value to an empty range:
  maximum -312.0 is less than minimum 312.0.   (landscape frame / portrait canvas)
```

Guard restored → `tests="27" failures="0"`, forced green. Single mutation, the named cases failed.

## Full gate

`./gradlew test -PforceTests --no-build-cache` → `BUILD SUCCESSFUL in 4m 58s`, 189 actionable / 21
executed, every `*test*UnitTest` task executed (none up-to-date): app mobile 1840/0, app automotive
1840/0, auto 789/0, core 525/0, JNI 33/0, buildSrc 9/0. Zero warnings in the touched files; the 68
warnings emitted are the pre-existing `TODO.md` §44 deprecation debt.

## On-device (task 4.3) — measured on the phone AVD

Device: `emulator-5554`, `sdk_gphone64_x86_64` (Pixel_8), 1080×2400. The AVD was provisioned for this
run: the debug APK was installed (`adb install -r -t`, `Success`, `lastUpdateTime 22:03:53`), location
granted, animation scales 0, and maps downloaded through the app's own manager — basemap Minimal
(3.0 MB) and **Andorra (5.6 MB)** → `files/maps/{basemap,andorra}` — with the fix moved to Andorra
(`adb emu geo fix 1.5218 42.5078`) so free driving has a real area.

Follow was engaged for the whole run (`content-desc="Freie Fahrt beenden"`).

| step | observation |
|---|---|
| `settings put system user_rotation 1` + fix | `dumpsys window` → `mBounds=Rect(0,0-2400,1080) mDisplayRotation=ROTATION_90`; screencap `2400×1080` (950 303 B, map content) |
| `settings put system user_rotation 0` + fix | portrait; screencap `1080×2400` (1 190 296 B, map content) |
| process | `pid=16661` identical before and after both rotations |
| frames | `dumpsys gfxinfo` Total frames rendered 200 → 205 → 215 |
| crash scan | `logcat` `FATAL EXCEPTION` / `Cannot coerce` / `empty range` count = **0** for the whole window |

Caveat, stated not hidden: a stationary emulator cannot produce follow *drift*, so this proves the
orientation-swap path (the crash trigger) and not the drift framing.

Earlier blocker (superseded): before provisioning, the AVD held a differently-signed install
(`run-as: package not debuggable`; `adb install -r -t` → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`), which is
why the fresh debug install and the map download were needed first.

## Not covered

- `TODO.md` §153 removal is task 4.4, due at archive.
