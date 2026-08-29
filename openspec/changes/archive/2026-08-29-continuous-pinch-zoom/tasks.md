# Tasks: continuous-pinch-zoom

## 1. JNI bridge (submodule patch)

- [x] 1.1 Done 2026-08-29: `osmscout-client-java` module `OSMScoutClient.java` render/renderWithRouteAndPois/projectToPixel take `double magnification` (scale factor 2^z); submodule `libosmscout-client-java/src/OSMScoutClient.cpp` JNI entries `jint mag` → `jdouble mag` + `SetMagnification(std::max(1.0, mag))` (render path + projectToPixel). Description/lookup entries stay `int` level. Submodule java copy unchanged (excluded from Gradle build, local override used). Note: the build's Gradle module compiles top-level `osmscout-client-java/src/main/java`, NOT the submodule java copy. Verify: `:app:assembleMobileDebug` compiles arm64-v8a + x86_64 (both rebuilt)

## 2. Renderer + projection plumbing (Int → Double)

- [x] 2.1 Done: `RenderViewport.mag`, `RenderJob`, `PendingRender`, listener, request/prepare/submit paths → Double; JNI render calls converted at the boundary (`2.0.pow(job.mag)`, tile path uses floor level `1L shl floor(job.mag)`, `TileKey(floor(mag))`); `trySubRegionBlit` Double; `frontBufferMag`/`renderedMag` Double. Build ✅
- [x] 2.2 Done: all signatures Double; `computeScale` uses `2.0.pow(mag)` unchanged for fractional. New tests in `ProjectionUtilsTest`: cursor geo point fixed across fractional step (14→15.2), two 0.5-level steps == one 1-level step. `:core:test` ✅
- [x] 2.3 Done: `LocationMarkerOverlay` (marker viewport Double incl. follow path 0.0 default), `MiniMap` (`initialMag` Double, buttons snap via `round`), `PoiSearchPanel` (`poiFitMagnification` → Double), `MapRenderUtil` (Double + docs), `FollowPrediction.displayOffsetPx`, `:auto` AutoMapRenderer (renders at `viewportZoomFraction`, `overrunMag` Double, blit comparison fraction-based), PaneOffset/DetailsScreen/MapScreen call sites. Full `./gradlew test` ✅ (458 tests)

## 3. ViewModel + persistence

- [x] 3.1 Done: Double clamps, `updateMagnification(Double)`, `zoomIn/Out` snap `round(mag) ± 1`; auto-zoom commit path converts `targetInt`→Double with same cooldown/samples. Tests: snap 15.3→16/14, clamps, fractional preserved. ✅
- [x] 3.2 Done: `ViewportState.magnification` Double; `ViewportStorageTest` extended — fractional round-trip 17.412 + hand-written legacy integer JSON (`magnification: 14`) loads as 14.0. ✅

## 4. Screen gesture path

- [x] 4.1 Done: gesture end commits `gestureEndMagnification(mag, gestureZoom)` = `clampGestureMagnification(mag + log2(factor))`, unrounded; commit guarded by `abs(newMag − mag) > 1e-6`; `MAX_GESTURE_ZOOM = 16.0f`, `MIN_GESTURE_ZOOM = 1/16`; `clampGestureVisualZoom` Double headroom (preview == commit at limits); wheel/keyboard/buttons unchanged; `ZoomControls.currentMag` Double (displays `toInt()`). Tests in `MapCanvasGestureTransformTest` (unrounded commit + clamp equals preview). ✅
- [x] 4.2 Done: smooth-zoom frame-loop math works on Double (`hold = 2^(committed − lastFrontMag)`, crossfade/hold tolerance 0.02 verified on device with Double log `mag=18.0`); `ZoomControlsAnimationTest`/`ZoomAnimationTest` harness mag vars converted to Double. ✅

## 5. Validation

- [x] 5.1 Done: `:core:test` + `:app:testMobileDebugUnitTest` — 458 tests, 0 failures (incl. `FakeOSMScoutClient` scale-factor boundary). Lint: only the 3 pre-existing `androidx.car.app.connection.provider` MissingClass errors (TODO.md §9); no new findings in touched files (3 pre-existing UseKtx warnings in `MapRenderer.kt` on unchanged code).
- [x] 5.2 On-device/emulator pass: continuous pinch in/out (visual continuity at gesture end — no snap), pinch past limits (preview clamps with no snap-back), fractional magnification persists across app restart, zoom buttons/keys unchanged (±1 level + smooth-zoom animation), GPS marker anchored during pinch, follow-mode pinch. Verify: `adb logcat -s MapCanvasScreen` shows single render at gesture end (debounce cadence unchanged), no FATAL. **CLOSED 2026-08-29** — user-confirmed on emulator after 4 fix rounds (continuous pinch, limits, close-fingers case, fractional persistence restore, buttons/keys/crossfade, no FATAL). Real-device sanity check noted as follow-up for the user (emulator pinch is synthetic input); no blocking issues found
- [x] 5.3 Done: `guidelines/MapRendering.md` had no contradicting integer statement; added fractional-magnification rule to §12 (Kotlin-internal level-style Double, JNI boundary converts once to scale factor 2^z, tile lookup snaps floor).

## 6. Documentation

- [x] 6.1 Done: TODO.md has no stale pinch-zoom row (double-tap entry re-worded 2026-08-29 to point at the fractional-magnification animation path). JNI double-signature documented in `guidelines/MapRendering.md` §12 + task 1.1 note above.

## Progress notes

- 2026-08-29 pinch-flicker debugging arc (4 fix rounds) RESOLVED, user confirms. Fixes: (a) tile-composition dest scaling ×2^(z−floor(z)) in rotated + north-up paths, (b) base-rescale sign corrected (base ×= 2^(buffer_old − buffer_new), collapses to 1.0 when the committed render lands mid-gesture), (c) gesture-end fold of the composed scale into the display layer + mid-gesture base maintenance (visual = buffer × base × factor invariant across buffer swaps/lands), (d) input hardening: gestureStartDist gate 20→60 px + exponential smoothing (k=0.4) on the applied factor, commit uses the displayed factor (visual == commit invariant). See ki_processing_failures.log for the full diagnostics trail (frame-dump evidence).
- Emulator verification 2026-08-29: user-passed pinch (continuous, limits, close-fingers), buttons/keys/crossfade green, fractional viewport persistence restore verified live (`mag=16.5047…` restored + rendered). No FATAL across sessions. Real-device check remains (task 5.2 tail).
- Emulator scripted pass 2026-08-29 (task 5.2 partial): fresh APK (all 3 ABIs) installed, app runs, no FATAL; zoom buttons + smooth-zoom crossfade work end-to-end with Double pipeline (`smooth-zoom: render landed mag=18.0 hold=2.0 displayed=1.0 crossfade=true`).