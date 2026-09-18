# Proposal

## Why

Free-driving follow mode on Android Auto shows the vehicle icon jumping off its road content once per GPS fix, and every fix costs a full native Cairo render instead of an overrun blit. `AutoMapRenderer` paints its canvas overlays against the **pending** render target (`viewportLat/Lon`, `viewportZoomFraction`, `viewportAngle`) while the pixels on screen come from the **committed** overrun frame (`overrunLat/Lon`, `overrunMag`, `overrunAngle`) shifted by `blitOffset`. The two differ for ~100 ms (render debounce) + one native render after every viewport write — i.e. after every fix, because free-driving heading-up re-anchors the frame on each fix.

This violates two existing requirements, so it is a defect, not a new capability:

- `gps-location-marker` — **Marker projects against displayed bitmap viewport**: "SHALL project the GPS coordinate to screen pixels using the viewport of the bitmap currently displayed, not the target viewport of a render that has not completed."
- `auto-map-renderer` — **Viewport update**: "A viewport center change within the overrun region SHALL be served by blitting the overrun buffer; a full re-render SHALL occur only when the center exits the overrun region or when zoom or rotation changes."

Measured on the running AA emulator (free driving, GPX replay, 104 s window, surface 1080x600, overrun 1296x720, mag 16 ≈ 0.825 m/px, 50.6 km/h):

```
GPS fixes                                105
full native renders (lock OK)            101   -> 1.0 per fix; the blit path is never used
extrapolation ticks sampled               38   -> clamped=true in 0 of them
per-fix frame re-anchor                  13.7 m -> ~17 px
displayed-frame projection error         k * |C_overrun - C_viewport|  -> ~17 px per fix,
                                         up to the overrun margin (108/60 px) when the
                                         extrapolation clamp co-writes the target
resolved vehicle anchor                  fy ~ 0.9 -> anchor displacement 0.4 * 600 = 240 px
```

Why now: five in-flight changes (`fix-aa-follow-vehicle-jumps`, `fix-aa-follow-blit-anchor-mismatch`, `fix-phone-follow-blit-anchor-mismatch`, `anchor-per-surface-visible-area`, `vehicle-position-presets`) have all edited this seam, tuning the anchor **fraction** while the frame **identity** stayed conflated. The AA follow diagnostic logs fix/predicted/displayed/offset but never the displayed frame's centre, so the evidence for this defect was never emitted.

## What Changes

- **Displayed-frame projection for overlays (AA)**: `AutoMapRenderer` SHALL project every canvas overlay drawn on the surface (vehicle marker, destination pin) against the frame that is actually on screen — its centre, magnification and rotation — and then by the blit offset of that frame. A pending viewport write (per-fix re-anchor, heading rotation, auto-zoom band) SHALL NOT move an overlay before the frame carrying it is committed.
- **Anchor-independent follow blit (AA)**: the follow-mode viewport blit SHALL be computed against the displayed vehicle position, not the frame centre. A frame centre is not a point in the rendered bitmap, so passing it charges the offset with the anchor displacement; for any non-center anchor preset (bottom/top rows: 0.4 x surface height = 240 px on a 600 px surface) that exceeds the overrun margin, so `renderFrame` refuses the blit and performs a full native render on every fix — the blit path is currently unreachable for every anchor except the exact centre.
- **Diagnostics**: the AA follow diagnostic SHALL log the displayed frame's centre/mag/rotation next to the pending target, so a "pending frame" window is visible in logcat.
- **Docs**: `guidelines/MapRendering.md` §1.1 (AA follow) gains the displayed-frame rule for overlays and the anchor-independent blit rule. Same paragraph the in-flight `fix-aa-follow-blit-anchor-mismatch` / `anchor-per-surface-visible-area` edits touch — merge into one edit, coordinate before landing.
- **Scope**: Android Auto renderer only. The phone app already implements this contract (`MapCanvasScreen` projects against `state.renderViewport`, committed atomically with `renderedBitmap`, and uses its `mag`/`angle`) — the change SHALL NOT alter phone behavior; it adds a parity assertion instead.
- **Not breaking / additive**: no settings, persistence, navigation-session, JNI or public API change. `AutoMapRenderer`'s internal frame bookkeeping changes. Rollback: revert the renderer edit; rendering, gestures, navigation, settings untouched.

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- `gps-location-marker`: requirement **Marker projects against displayed bitmap viewport** — extend its scope from the GPS marker to every overlay drawn on the map surface (GPS marker + destination pin) and add the AA parity scenarios: a fix-driven re-anchor while the render is in flight, and a pending rotation. The phone already satisfies it; the delta makes the AA variant testable against the same contract.
- `auto-map-renderer`: requirement **Viewport update / viewport center change within the overrun region** — pin that the overrun blit SHALL serve a center change for a non-center vehicle anchor preset, and that the marker projection SHALL follow the displayed frame's centre, magnification and rotation rather than the pending render target.
- `auto-smooth-follow`: requirement **Sub-region blit on viewport change** — add the follow-mode re-anchor scenario (a GPS fix re-anchors the frame within the overrun region SHALL be served by a blit, with no full native render per fix).

Previous specifications changed by this proposal: `gps-location-marker`, `auto-map-renderer`, `auto-smooth-follow` (all three existing). No capability is renamed or removed.

## Impact

- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt`:
  - `markerViewport()` / `markerScreenPosition()` — project against the displayed frame's centre instead of the pending target.
  - `drawGpsMarker()`, `drawDestinationMarker()` — use the displayed frame's magnification/rotation.
  - `renderFrame()` follow blit call site — pass the displayed vehicle position for the offset.
  - `fullRender()` commit + the displayed-frame bookkeeping — one place that publishes "this frame is on screen with this centre/mag/rotation/offset".
  - The follow diagnostic line — add the displayed-frame centre.
- `auto/src/test/java/com/naviveylin/auto/AutoMapRendererTest.kt` — 3 new regression tests (below) plus keeping `markerRidesTheBlittedContentWithABlitOffset` and the pane-band anchor geometry green.
- `guidelines/MapRendering.md` §1.1 — AA follow paragraph (displayed-frame rule, anchor-independent blit).
- Modules: `:auto` only (`:auto:testDebugUnitTest`, `:app:assembleAutomotiveDebug`). No native/JNI change: no submodule patch and no bridge-module override; the fix is Kotlin-only inside `AutoMapRenderer`.
- Coordination (same code region, do not duplicate work): `fix-aa-follow-blit-anchor-mismatch` (8/9) and `anchor-per-surface-visible-area` (31/38) both assume the frame-of-reference this change separates — land this first or fold their remaining task in; `fix-phone-follow-blit-anchor-mismatch` (8/9) is phone-side and unaffected (verify only); `fix-aa-follow-vehicle-jumps` (complete) tuned the same loop's easing.
