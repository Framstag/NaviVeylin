# Fix Phone Follow Blit Anchor Mismatch

## Why

On-device report (phone, navigation mode, bottom-center vehicle placement): the vehicle icon rides correctly above the routing status card, but during a drive it stays visibly offset from the route track **ahead in the driving direction** — the map content under the icon does not match the position the icon represents. In follow mode the marker and the map content share one projection; they can only drift apart if one stage of the follow pipeline uses a different anchor than the others.

Root cause confirmed in code: the frame render and the marker projection use the **resolved** anchor fraction (`state.resolvedAnchor`, collision-remapped above the routing card), but the follow **blit offset** (`MapCanvasScreen.kt` → `FollowPrediction.displayOffsetPx`) is still computed against the **raw preset** (`ui.activeFollowAnchor`, e.g. `0.9` for bottom-center). With navigation overlays measured, resolved ≠ raw (bottom-center resolves to ~0.76 on typical portrait), so the blit places the displayed content at the raw anchor while the marker draws at the resolved anchor — a mismatch of `(0.9 − 0.76)·H ≈ 0.14·H` along the travel axis in heading-up. The same inconsistency keeps the offset permanently outside the overrun margin, so the frame is re-rendered every 500 ms for the whole drive (render churn).

The Math/contract is already pinned by `FollowAnchorFramingTest` ("resolved anchor keeps the marker on the content with overlays" passes the resolved fractions to `displayOffsetPx`); the production call site violates that contract by passing the raw preset. Android Auto already passes its resolved value (`clampAnchorOutOfPane`), so AA is unaffected.

## What Changes

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` (follow display loop): compute the blit offset against `state.resolvedAnchor` instead of `ui.activeFollowAnchor` — the one anchor the frame render and the marker projection already use.
- Add a regression test in `app/src/test/java/com/naviveylin/ui/map/FollowAnchorFramingTest.kt`: with the frame rendered for the **resolved** anchor and the marker projected against the resolved anchor, a blit computed against the **raw** preset leaves the marker off the content by the anchor delta (documents the defect); the same geometry with the resolved anchor everywhere stays aligned and unclamped.
- No change to `VehicleAnchor.kt`, `FollowPrediction.kt`, `MapCanvasViewModel.kt`, or the Auto renderer — the resolved anchor is already published on `MapCanvasUiState.resolvedAnchor`; only the screen's offset call site needs to use it.
- Additive; no settings, persistence, or API changes. Rollback: revert the single call-site change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `smooth-follow`: add a requirement that the follow blit offset SHALL be computed against the same **resolved** anchor screen fraction as the frame render target and the marker projection, so the marker and the map content share one anchor in every stage (phone parity; AA already uses its resolved anchor). Scenario pins the regression: raw preset ≠ resolved while navigation overlays are measured → marker stays on the road content, no offset outside the overrun margin (no render churn).

## Impact

- `app/src/main/java/com/naviveylin/ui/map/MapCanvasScreen.kt` — one call-site change in the follow display loop (anchor args of `FollowPrediction.displayOffsetPx`).
- `app/src/test/java/com/naviveylin/ui/map/FollowAnchorFramingTest.kt` — new regression tests (plain JUnit, no JNI/classloader concerns).
- Spec delta `openspec/specs/smooth-follow/spec.md` (via `specs/smooth-follow/spec.md` in this change).
- Guidelines: `guidelines/MapRendering.md` §1.1 documents the follow pipeline ("Single follow center", "Anchor-centered follow framing") — add a sentence stating the anchor is applied once, identically, in render target, blit offset, and marker projection.
- Native/JNI: none. AA: none (verified already-resolved).
- Verification: `./gradlew :app:assembleMobileDebug -Pandroid.injected.build.abi=arm64-v8a`, `./gradlew test`, on-device GPX replay / drive with `adb logcat -s NaviVeylin` follow diagnostics (fix/pred/disp/off/clamped).
