## Context

See proposal.md — Why. Current state that shapes the approach:

- Both follow-mode renderers (phone `MapCanvasScreen`/`MapCanvasViewModel`, AA `AutoMapRenderer`) keep the vehicle at screen center by making the map render target equal the vehicle position: the native full render centers on the vehicle geo point, and the vehicle marker is drawn at the projection of that geo point against the same center — so marker and map coincide at center.
- The overrun-buffer machinery (`FollowPrediction.displayOffsetPx`, blit translate, extrapolation easing) is shared by phone and AA and treats the buffer center as the reference frame.
- `PaneOffset.paneOffsetCenter` in `:auto` already proves the anchor trick for the "show location" maps: compute the viewport center as the geo point that projects to the chosen screen fraction, via `ProjectionUtils` (currently angle-blind, used only for north-up browsing).
- `ProjectionUtils.viewport(...)` accepts an angle; `ProjectedViewport.screenToGeo` therefore supports rotation-aware anchor math with no new projection code.
- Settings flow for a global cross-surface value is established: `AppSettings` (phone JSON) + `AutoSettings` (`:core`, shared with the AA process) + `AutoSettingsMapping` both directions (see in-flight `overspeed-warning-threshold` change).

## Goals / Non-Goals

**Goals:**
- One anchor preset per mode (routing, free driving), each from a fixed 5×3 grid (15 presets), default center/center.
- Anchor applied to follow-mode map framing on phone and AA, north-up and heading-up, without touching the overrun/glide smoothness machinery's semantics.
- Marker stays glued to the map content at the anchor position (no marker/map divergence during extrapolation or rotation).
- Zero behavior change at upgrade with default values; additive settings with `ignoreUnknownKeys`-safe JSON.

**Non-Goals:**
- Free-form (continuous) anchor positioning — user explicitly wants fixed grid.
- Automatic host-panel detection/compensation during navigation — pane compensation stays for browsing screens only (`MapScreen`, `DetailsScreen`); the anchor is the manual user choice.
- Changing overrun buffer size, extrapolation easing, or zoom logic.
- Anything on the C++/JNI side or new car-app APIs.

## Decisions

### D1: Anchor model in `:core` — fixed enum, not raw fractions

`VehicleAnchorPosition` (new `core/.../VehicleAnchor.kt`): 15 enum entries, each carrying `fx`/`fy` screen fractions (horizontal 0.1/0.3/0.5/0.7/0.9, vertical 0.1/0.5/0.9), a stable id string, and a display label ("Center", "Bottom left", …). Persist the id string in `AutoSettings` (`routingAnchor`, `freeDrivingAnchor`, defaults `CENTER_0_50-50`-style ids) and `AppSettings`; `AutoSettingsMapping` maps both directions like every other cross-surface setting. Labels are single-sourced here so the phone picker and the AA picker stay in parity (guidelines/UI.md parity rule).

- Alternative considered: persist raw `fx/fy` doubles. Rejected: re-introduces free-form values (user explicitly ruled out), invalid-state risk, no canonical labels.
- Alternative considered: 3×3 grid. Rejected in discussion — horizontal resolution matters because host panel widths vary widely (~40% on the current unit); 5 columns cover it at 10% steps.

### D2: Rotation-aware anchor geometry — `ProjectedViewport` with angle

`VehicleAnchor.anchorCenter(vehicleLat, vehicleLon, anchor, mag, w, h, dpi, angle)` computes

```kotlin
ProjectionUtils.viewport(vehicleLat, vehicleLon, mag, w, h, dpi, angle)
    .screenToGeoRotated((1 - anchor.fx) * w, (1 - anchor.fy) * h)
```

i.e. the geo point that, when used as render center under the current viewport angle, projects the vehicle to the anchor screen fraction — the `paneOffsetCenter` logic generalized to both axes and rotation. Note the MIRROR (1−fx, 1−fy): re-centering on the geo point at screen fraction f projects the old center to the mirrored fraction 1−f, so the raw fraction would put the vehicle at 1−fx (verified by the `VehicleAnchorTest` projection round-trips). `screenToGeoRotated` carries the rotation (the offset is rotated back by `angle` first), keeping the vehicle pinned under heading-up.

- Alternative considered: render centered on the vehicle and post-shift the image in screen space. Rejected: breaks the overrun blit model (blit is a pure 0/90° copy of the already-rendered buffer; an extra rotated translation per frame defeats the sub-region blit).
- Alternative considered: angle-blind `screenToGeo` (reuse `paneOffsetCenter` as-is). Rejected: under heading-up navigation (angle = −bearing, the nav default), the vehicle would orbit the anchor as the bearing changes. The rotation-aware form keeps the vehicle pinned.

### D3: Render target = anchor center in follow mode; marker drawn against the active framing

In follow mode the AA renderer sets `viewportLat/Lon` to `anchorCenter(display position)`: the native full render, `emitViewportState`, and `markerViewport()` all get the anchor center in follow mode; the raw vehicle geo stays in `displayLat/Lon`/`gpsMarkerLat/Lon` (`markerPosition()` unchanged). On the phone, the ViewModel's `updateCenter`/`renderMap` center becomes `anchorCenter(followDisplay)` and `recenterInBrowse` centers on `anchorCenter(GPS pos)`; the Compose marker projects against `state.renderViewport`, which is the rendered bitmap's viewport — so marker and bitmap agree by construction.

The overlay marker must be drawn against the framing that is actually on screen during extrapolation glide (render target + current blit offset), not a freshly recomputed anchor: otherwise the marker drifts a few pixels off its underlying road during the 200–300 ms glide. Implementation choices (phone currently applies blit offsets as overlay translate; AA `drawGpsMarker` projects against viewport state) may both reuse the tick's computed offset; the full render at the freshly computed anchor re-locks marker and content every clamp event.

- Alternative considered: keep marker projection at vehicle position and translate with the map. Same result — the constraint is one shared projection source for bitmap and glyphs; exact code shape left to tasks.
- Alternative considered: recompute `anchorCenter(display_now)` per overlay frame for the marker. Rejected: marker would sit at the theoretical anchor while the buffer still aligns the previous render target, producing visible marker/road mismatch during glide.

### D4: Anchor-relative clamp margins in `FollowPrediction.displayOffsetPx`

Today `displayOffsetPx` clamps when the vehicle's offset from the buffer center exceeds the (buffer − surface)/2 margin; with the vehicle at the surface center that margin is symmetric. With an off-center anchor the vehicle starts at `(fx−0.5)·W` from the buffer center — for `fx=0.9` that is 0.4·W, already beyond the 0.1·W margin, so every frame would look "clamped" → full render storm.

`displayOffsetPx` gains optional `anchorX`/`anchorY` (default 0.5): `clamped` = |offset − anchorOffsetPx| > margin, i.e. the render is requested when the vehicle has drifted more than the margin *from its anchor*, matching today's behavior exactly at center. Buffer-edge safety still holds: anchor is bounded to 0.1–0.9 and the margin keeps |offset| ≤ 0.5·W < buffer half-width 0.6·W.

- Alternative considered: clamp relative to the buffer edge (per-vehicle-travel budget). Rejected: asymmetric per direction and anchor, more complex to reason about and test; drift-from-anchor preserves current smoothness feel.
- Alternative considered: enlarge the overrun buffer for edge anchors. Rejected: memory cost on the car for no perceived benefit; margin math already safe.

### D5: Anchor selection per mode, applied at the screen level

`NavigationScreen` (routing) and `FreeDrivingScreen` (free driving) read the shared settings and pass the mode's anchor into the renderer on fix commits (rendezvous pattern already used for auto-zoom/north-up settings). The phone ViewModel picks the anchor by driving state: routable guidance active → routing anchor, else free-driving anchor. No nav-engine changes; the anchor is display-only.

### D6: Picker UIs

- AA: a `ListTemplate` picker screen with 15 rows (one per preset, current anchor marked) opened from the two new "Vehicle position" rows in `PreferencesScreen` — the same shape as the overspeed-delta value picker (`OverspeedDeltaPickerScreen`) because car templates cannot host a grid.
- Phone: a Material 3 grid picker (5 columns × 3 rows) in a dialog from the two new rows in `LocationOptionsOverlay`, sharing the enum labels. Parity requirement: same labels/hierarchy (guidelines/UI.md), grid layout is the platform-appropriate deviation (phone can host one).
- Alternative considered: 15-row list on the phone too. Rejected: grid is clearly better on a touch screen; parity is label-based, and UI.md's parity rule already accounts for platform-constrained deviations.

## Risks / Trade-offs

- [Heading-up rotation pivots at the surface center, not at the anchor] — the map "spins around the middle" while the marker stays pinned. For presets within 0.1–0.9 and typical turn rates this is visually subtle (offset ≤ 40%). → Verify on head unit; if it reads badly, fall back to limiting heading-up offset compensation to small angles (design change, not spec change).
- [Marker/map drift during extrapolation glide (D3)] — misimplemented projection source makes the marker wander off its road briefly. → Single projection source for bitmap + marker; re-lock on every full render; covered by A/B test on GPX replay.
- [Overlap with in-flight `auto-pan-during-navigation`] — both touch `AutoMapRenderer` re-engage/pause semantics. → Land that change first (it is at 11/12 tasks); this change's `reengageFollow` anchor-restore task is written to build on its contract (spec scenario "Anchor restored after manual pan"). Review the merge order in tasks.
- [Clamp-margin change affects the shared `FollowPrediction.displayOffsetPx` used by phone + AA] — regression risk in existing center behavior. → Default anchor params (0.5/0.5) reproduce current margins exactly; existing `FollowPredictionTest` cases must keep passing unchanged + new anchor-specific cases.
- [Host panel geometry varies by unit; preset is manual] — wrong preset = marker still under a panel on another host. → User-tunable by design; keep pane compensation for browsing; document in the picker that presets are per-head-unit tuning.
- [Auto-zoom / pinch zoom around which point] — auto-zoom re-renders at `anchorCenter` with the new magnification (marker stays at anchor: spec scenario); pinch keeps existing `zoomAtCursor` focus semantics, untouched.

## Migration Plan

1. `:core`: `VehicleAnchor` + `AutoSettings` fields (defaults) first — pure additive.
2. `FollowPrediction.displayOffsetPx` anchor params (defaults 0.5) — existing tests green, additive.
3. AA + phone renderers switch to anchor center; default anchor = exact old framing, verifiable by diffing follow logs.
4. Settings plumbing (`AppSettings` fields, mappers) + picker UIs.
5. Rollback: reset both settings to center/center (or remove fields) — JSON `ignoreUnknownKeys` already tolerates missing values (defaults on load). No migration of stored data required; old installs load defaults.
