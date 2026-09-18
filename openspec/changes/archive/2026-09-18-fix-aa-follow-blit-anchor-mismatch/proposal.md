# Fix AA Follow Blit Anchor Mismatch

## Why

Android Auto has the same latent inconsistency the phone change (`fix-phone-follow-blit-anchor-mismatch`) fixed: the follow blit offset (`AutoMapRenderer` → `FollowPrediction.displayOffsetPx`) is computed against the **raw preset** (`followAnchor`), while the frame is rendered at the **resolved** fraction (`anchorCenterFor` → `clampAnchorOutOfPane` against the host pane band). It only fires for the pane-band presets — the far-left column in LTR, far-right in RTL (`fx` 0.1/0.9 inside the host's leading 40% band) — because AA resolves horizontally only; default center and all other presets keep resolved == raw and are unaffected.

The visible symptom differs from the phone: AA's marker projects against the frame target viewport and subtracts the same blit offset (`markerScreenPosition`), so the icon stays glued to its road content — but the frame content sits at the wrong screen fraction and the offset is permanently outside the overrun margin, so `renderFrame` bails to a **full native Cairo render on every tick** instead of blitting (continuous heavy re-renders / battery burn) and the vehicle is not held at the configured anchor fraction.

## What Changes

- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt`: pass the **resolved** anchor fractions to `FollowPrediction.displayOffsetPx` at both call sites — the extrapolation-loop blit (`~L714`) and `renderFrame`'s blit path (`~L775`) — instead of the raw `followAnchor.fx/fy`. Use `resolvedFollowAnchor()` (already resolved via `clampAnchorOutOfPane`, already exposed and unit-tested).
- Add regression test(s) in `auto/src/test/java/com/naviveylin/auto/AutoMapRendererTest.kt` pinning the pane-band geometry: with a far-left preset (LTR), the raw-anchor offset is clamped (churn) and displaced, while the resolved-anchor offset stays inside the margin and lands the content at the resolved fraction; the marker/glue contract (`markerRidesTheBlittedContentWithABlitOffset`) stays green.
- No change to phone code, `FollowPrediction.kt`, `VehicleAnchor.kt`, or the navigation session.
- Additive; no settings or persistence changes. Rollback: revert the two call-site arguments.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `auto-smooth-follow`: add a requirement that the AA follow blit offset SHALL be computed against the same **resolved** anchor fraction the AA frame render target uses (`clampAnchorOutOfPane` result), never the raw preset, so the pane-band presets hold their resolved screen fraction and the blit never saturates the overrun margin (no full-render churn). Scenario pins the far-left (LTR) / far-right (RTL) geometry.

## Impact

- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` — two call-site arguments in the follow blit paths.
- `auto/src/test/java/com/naviveylin/auto/AutoMapRendererTest.kt` — regression tests (Robolectric; existing file already tests `resolvedFollowAnchor` and the marker-blit glue).
- Spec delta `openspec/specs/auto-smooth-follow/spec.md` (via `specs/auto-smooth-follow/spec.md` in this change).
- Guidelines: `guidelines/MapRendering.md` §1.1 already documents the shared resolved-anchor rule — extend the AA paragraph (host-pane collision) to state the blit offset uses the same resolved value.
- Phone: none (resolved in `fix-phone-follow-blit-anchor-mismatch`).
- Verification: `./gradlew :auto:testDebugUnitTest`, `./gradlew :app:assembleAutomotiveDebug -Pandroid.injected.build.abi=arm64-v8a`, on-device AA check (head unit / car app host, far-left preset with the host pane present: no logcat render churn, vehicle at the configured fraction).
