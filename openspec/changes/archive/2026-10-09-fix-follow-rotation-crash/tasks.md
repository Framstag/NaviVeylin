# Tasks

## 1. Make the overrun margin non-negative

- [x] 1.1 In `core/src/main/java/com/naviveylin/core/FollowPrediction.kt` compute both margins as
  `((bitmapW - canvasW) / 2.0).coerceAtLeast(0.0)` / `((bitmapH - canvasH) / 2.0).coerceAtLeast(0.0)`,
  keep the rotated delta and the `DisplayOffset` contract, and extend the KDoc to state that a frame
  with no overrun margin on an axis yields a zero clamped offset on that axis and reports `clamped`
  (spec: smooth-follow — A frame from the previous orientation yields no offset instead of an error;
  design D2). Verify: `./gradlew :core:testDebugUnitTest` is green and the helper compiles warning-free.
- [x] 1.2 Add cases to `core/src/test/java/com/naviveylin/core/FollowPredictionTest.kt`:
  portrait bitmap `1296×2880` against landscape canvas `2400×1080` and the reverse — the call SHALL NOT
  throw, `clampedX`/`clampedY` SHALL be finite, the margin-less axis SHALL be exactly `0.0` and
  `DisplayOffset.clamped` SHALL be true (spec scenario: The phone is rotated while follow mode is
  active; Both orientation transitions are safe). Add one control case with a same-orientation
  overrun margin asserting the existing clamp and the unclamped in-margin drift are unchanged (spec
  scenario: A frame with overrun margin keeps today's clamp). Verify: focused
  `./gradlew :core:testDebugUnitTest --tests "com.naviveylin.core.FollowPredictionTest"` names all
  four new cases and is green.
- [x] 1.3 Revert-check for the new invariant (design D2, `guidelines/Build.md` §2/§4): mutate the
  margin back to the bare `(bitmapW - canvasW) / 2.0` (guard removed) → the swapped-orientation case
  MUST fail with `IllegalArgumentException: Cannot coerce value to an empty range`. Restore the guard
  and re-run forced green (`./gradlew :core:testDebugUnitTest -PforceTests --no-build-cache`). Record
  the mutation, the failing case and the restored green in the change's evidence. Evidence: mutation →
  `tests="27" failures="2"` with `Cannot coerce value to an empty range: maximum -552.0 is less than
  minimum 552.0` (portrait/landscape case) and `… maximum -312.0 … minimum 312.0`
  (landscape/portrait case); guard restored → `tests="27" failures="0"`, forced green.

## 2. Call-site consequences

- [x] 2.1 Assert the render-request signal at the phone call site: extend the framing case in
  `app/src/test/java/com/naviveylin/ui/map/FollowAnchorFramingTest.kt` (or add a case beside it) so a
  mismatched orientation produces `DisplayOffset.clamped == true`, which `MapCanvasScreen.kt:740-745`
  uses to request a re-render (spec scenario: The phone is rotated while follow mode is active).
  Verify: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.map.*"` is green,
  including `FollowAnchorFramingTest` and `MapPanDisplayWindowTest`.
- [x] 2.2 Confirm the remaining callers need no change: `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt:1096`,
  `PanWindowRules.kt:72` and the car paths `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt:1153,1277,1469`
  compile against the unchanged signature (spec: smooth-follow — A frame from the previous orientation
  yields no offset instead of an error). Verify: `./gradlew :app:testMobileDebugUnitTest
  :app:testAutomotiveDebugUnitTest :auto:testDebugUnitTest` green, in particular `AutoMapRendererTest`.

## 3. Documentation

- [x] 3.1 Add one rule to `guidelines/MapRendering.md` §13 ("Overrun Display Window (pan and follow)"):
  the clamp's overrun margin is never negative, and a frame from the previous orientation yields a
  zero offset on the margin-less axis flagged clamped, so the surface re-renders instead of failing.
  Verify: the sentence is present (`grep -n 'never negative' guidelines/MapRendering.md`) and the
  section still reads as the owner of the clamp rule (spec: smooth-follow — A frame from the previous
  orientation yields no offset instead of an error). Evidence: rule added at `guidelines/MapRendering.md:500`.

## 4. Build, tests and measurement

- [x] 4.1 Verify the build compiles without errors for the touched modules and that all existing tests
  pass: `./gradlew :core:testDebugUnitTest :app:testMobileDebugUnitTest
  :app:testAutomotiveDebugUnitTest :auto:testDebugUnitTest --continue`. Quote the executed-task count
  and the tallies from the result XMLs (a 5-second "BUILD SUCCESSFUL" is not evidence —
  `guidelines/Build.md` §4). Evidence: `BUILD SUCCESSFUL in 5m 13s`; app mobile 1840/0, app automotive
  1840/0, auto 789/0, `AutoMapRendererTest` 76/0 — all from result XMLs, 0 failures.
- [x] 4.2 Run the full both-flavor gate once, forced, before marking the change complete:
  `./gradlew test -PforceTests --no-build-cache`, then confirm zero failures, zero warnings, and record
  the per-suite tallies. Evidence: `BUILD SUCCESSFUL in 4m 58s`, 189 actionable / 21 executed, all six
  `*test*UnitTest` tasks executed (not up-to-date): app mobile 1840/0, app automotive 1840/0, auto
  789/0, core 525/0, JNI 33/0, buildSrc 9/0. Zero warnings in the touched files (`FollowPrediction`,
  `FollowAnchorFramingTest`); the 68 warnings emitted are the pre-existing §44 deprecation debt.
- [x] 4.3 On-device measurement (device-gated): with navigation running and follow engaged on the
  phone AVD/device, run `adb shell settings put system user_rotation 1` then `… user_rotation 0`; the
  process SHALL survive both and `adb logcat -s NaviVeylin` SHALL show no `FATAL EXCEPTION` naming
  `FollowPrediction.displayOffsetPx`, with the map re-rendered in each orientation. Record the numbers
  and state explicitly that a stationary emulator cannot produce follow *drift* — this measurement
  proves the orientation-swap path only. **MEASURED 2026-10-09** on `emulator-5554`
  (`sdk_gphone64_x86_64`, Pixel_8, 1080×2400), after provisioning maps (basemap Minimal 3.0 MB +
  Andorra 5.6 MB, `files/maps/{basemap,andorra}`) and a fix at `1.5218, 42.5078`; free driving (follow)
  was active (`content-desc="Freie Fahrt beenden"`) across the whole run. Results: rotation 1 applied —
  `dumpsys window` → `mBounds=Rect(0,0-2400,1080) mDisplayRotation=ROTATION_90`, screencap `2400×1080`
  (950 303 B, map content); rotation 0 applied — `mBounds` portrait, screencap `1080×2400` (1 190 296 B,
  map content). Process `pid=16661` identical before and after both rotations,
  `dumpsys gfxinfo` frames 200 → 205 → 215, and `logcat` FATAL/`Cannot coerce`/`empty range` count = **0**
  for the whole window. Caveat stated, not hidden: a stationary emulator cannot produce follow *drift*,
  so this proves the orientation-swap path (the crash trigger) and not the drift framing.
- [x] 4.4 Remove `TODO.md` §153 when the change is archived, noting the verification that closed it.
  Evidence: section removed 2026-10-09 (TODO.md 2346 → 2322 lines); the closing verification is recorded
  in this change's tasks 1.2/1.3 (unit + revert-check) and 4.3 (device rotation run).
