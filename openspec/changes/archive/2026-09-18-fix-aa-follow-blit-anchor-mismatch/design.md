# Design — Fix AA Follow Blit Anchor Mismatch

References: spec `auto-smooth-follow` — "Single resolved anchor in the AA follow blit" (this change); the AA follow pipeline in `AutoMapRenderer.kt`; the phone counterpart `fix-phone-follow-blit-anchor-mismatch`.

## Context

The AA pipeline mirrors the phone's three-stage anchor use, with one structural difference in the marker:

```
preset FAR_LEFT (0.1, 0.5)          ← setFollowAnchor (routing/free-driving shared settings)
        │
        ▼
resolvedFollowAnchor() = clampAnchorOutOfPane(anchor, pane band = 40% leading, LTR left / RTL right)
        │
        ▼
resolved (≈0.46, 0.5)   published internally (no UI state; pure function, unit-tested)
   │            │            │
   ▼            ▼            ▼
frame commit      blit offset         marker
anchorCenterFor   displayOffsetPx     markerScreenPosition
L342/512/543→resolved  L718, L779 RAW ✗  = proj(display; viewport) − blitOffset
```

- Frame: `fullRender` renders the overrun frame at `viewportLat/Lon`, committed to `anchorCenterFor(...)` (resolved) in every follow path (stationary snap L342, re-center L512, re-engage L543, clamp re-anchor L744).
- Blit: `displayOffsetPx(..., followAnchor.fx/fy)` — the **raw preset** — at both call sites (extrapolation loop L714, `renderFrame` L775). Same defect class as the phone.
- Marker: `markerScreenPosition` projects the display position against `viewportLat/Lon` (the frame target = resolved anchor center) and subtracts the same blit offset — so the icon is glued to the frame content **by construction**, unlike the phone (which projects against the resolved anchor center and draws at the anchor point). This is why the AA symptom is misplacement + render churn, not icon-off-road.

When does resolved ≠ raw? `clampAnchorOutOfPane` clamps horizontally only (the pane spans the surface height): presets with `fx` inside the leading 40% band — far-left column (fx 0.1) in LTR, far-right (fx 0.9) in RTL — resolve to the band edge plus the grid's 10% margin (≈0.46/0.54 for a 100px test surface). Default center and all other presets: resolved == raw, offset formula consistent, no behavior change.

Consequence for pane-band presets: the offset carries a constant `(raw − resolved)` term per axis → outside the overrun margin (0.1 of the surface) whenever the frame is aligned → `renderFrame`'s blit path bails (`offset.clamped`) to `fullRender` on every tick → continuous full native Cairo renders; the frame content is held at the clamped offset instead of the resolved anchor fraction (vehicle visually pressed off its configured position).

## D1 — Blit offset uses the resolved anchor at both AA call sites

`AutoMapRenderer.kt`: at L718 and L779-780, replace `followAnchor.fx/fy` with `resolvedFollowAnchor().fx/fy`. The helper already encapsulates the pane-side logic (`hostPaneRtl`), is pure given surface size, and is already unit-tested (`AutoMapRendererTest.hostPaneClampsTheLeadingEdgeAnchorOnly`), so call sites stay single-anchor-consistent: frame commit, blit and marker all derive from one resolved value.

Alternative A (keep the raw preset in the blit and render the frame at the raw anchor): would put pane-band preset vehicles inside/near the host panel band, undoing `clampAnchorOutOfPane` — rejected for the same reason the phone change rejects it (the whole point of the resolution is the visible-map-area contract).
Alternative B (remove the anchor term from `displayOffsetPx`, offset = pure drift): changes shared core semantics and the phone caller for no benefit — the anchor term is what makes "aligned display → zero offset" hold once the frame is anchor-centered. Rejected; keep one function, one consistent anchor.
Alternative C (resolve at the call sites inline): duplicates `resolvedFollowAnchor()`'s pane logic at two more places — rejected, the helper exists and is tested.

Non-follow call at L779-780 guards with `if (followMode) resolved else 0.5`: keep that guard (`resolvedFollowAnchor()` is only meaningful in follow, `followAnchor` is the default when not in follow); the resolved call applies inside the `followMode ?: resolvedFollowAnchor()` branch only. (The follow-mode branch replaces the raw anchor with the resolved one.)

## D2 — Regression tests in AutoMapRendererTest

Robolectric tests (existing harness: `mockSurface`, `asyncLoopsEnabled = false`):

1. `pane-band blit offset uses the resolved anchor` — LTR, far-left preset: compute the AA blit offset with the raw preset and with `resolvedFollowAnchor()`; assert the raw-anchor offset is clamped (churn) while the resolved-anchor offset for the same display drift stays inside the margin and places the content at the resolved fraction.
2. `non-follow browse blit keeps the surface-center behavior` — guard the existing browse path (`0.5/0.5`) is untouched and the follow branch picks the resolved anchor.

Existing tests already pin the resolution (`hostPaneClampsTheLeadingEdgeAnchorOnly`) and the marker glue (`markerRidesTheBlittedContentWithABlitOffset`) — assert they stay green unchanged.

## Threading and lifecycle

The two call sites run on the renderer's own thread (extrapolation loop / `renderFrame` under `surfaceLock`); `resolvedFollowAnchor()` reads `followAnchor`/`hostPaneRtl`/`surfaceWidth` — all `@Volatile` or set on the same thread — no new state, dispatcher, or lifecycle change.

## Verification

- Unit: `./gradlew :auto:testDebugUnitTest` (new tests + existing anchor/marker tests), full `./gradlew test`.
- Build: `./gradlew :app:assembleAutomotiveDebug -Pandroid.injected.build.abi=arm64-v8a` (AAOS flavor) and `:app:assembleMobileDebug` for the projection side.
- On-device (head unit / car app host with pane): select a far-left preset in navigation; `adb logcat -s NaviVeylin`/AA follow diagnostics show `off` inside the margin, `clamped` rare, no repeated full renders; vehicle at the configured screen fraction, not pressed toward the pane.
- Guidelines: extend the AA paragraph of `guidelines/MapRendering.md` §1.1 (host-pane collision) that the blit offset uses the same resolved value.

## Risk

- Low: two constant swaps in one file, backed by an existing pure, tested resolver. The phone and AA now share one documented anchor rule (single resolved anchor in every stage).
- Regression guard: D2 test 1 fails if a future change passes the raw preset again; the existing resolution + marker-glue tests pin the surrounding contract.
