# Map Rendering — Knowledge Base & Rules

This document owns the render pipeline and its data rules — the two render modes, the bitmap
lifecycle, coalescing, the marker overlay's projection and the epochs. A frame's delivery is
`guidelines/MapFrames.md`; a route's numbers are `guidelines/MapRoute.md`; the stylesheet, icon and
label-wrapping contract is `guidelines/MapStyling.md`. The follow path is `guidelines/MapFollow.md`,
the car renderer `guidelines/MapCar.md`. The history, the measurements and the rejected alternatives
are in `guidelines/MapRendering.why.md`; the commands and number tables are in
`guidelines/MapRendering.recipes.md`.

---

## 1. Render Pipeline Architecture

This section is an index by question; the rules are stated under the sub-sections, and the history and the
measurements behind them are in `guidelines/MapRendering.why.md` §1.

- **§1a** — follow framing: the vehicle anchor is applied in exactly one place, the render target.
- **§1b** — the two render modes (`TILES`, `DIRECT`) and how `executeRender` selects between them.
- **§1c** — the overlay stage, the mode switch, the low-zoom risk and the removed helpers.
- **§1d** — marker projection: which frame the Compose and car overlays project against.
- **§1e** — the follow display contract shared by the phone and Android Auto.


## 1a. Follow framing — the anchor applies to the RENDER TARGET (read before touching follow mode)


- **Rule: the vehicle anchor is applied in exactly one place — the follow render target.** The
  frame is rendered with its center at `anchorCenter(vehicle, resolveAnchorFraction(activeAnchor, insets))`
  (`core/VehicleAnchor.kt`), so the vehicle sits at the anchor *inside the bitmap*. The blit/
  display offset then carries the prediction drift only (`OverrunWindow.shiftPx` with the
  anchor parameters). Subtracting the anchor a second time from the drawn offset pushes the frame
  outside the overrun margin and paints an uncovered strip of `surfaceColor`.
- **Rule: The anchor fraction is relative to the map canvas, and the resolved value is published once.**
  `resolveAnchorFraction(preset, left/top/right/bottom insets, W, H)` keeps the preset fraction exact
  unless the marker — its footprint plus padding — falls inside a covered region (phone: next-turn card,
  routing-status card, widget column, street-name pill — measured via `onSizeChanged` and pushed with
  `MapCanvasViewModel.setMapOverlayInsets`); a covered preset moves per axis to the nearest free position
  (covered-region edge + the grid's 10% margin), and the result is clamped into `0.1..0.9`. The ViewModel
  publishes it as `MapCanvasUiState.resolvedAnchor`; the follow render target, the Compose marker
  overlay **and the follow blit offset** all consume that value —
  never re-derive it and never pass the raw preset: a blit computed against the preset while the frame
  renders on the resolved fraction leaves the marker ahead of the content by the anchor delta and keeps
  the offset outside the overrun margin (500 ms re-render churn). The blit offset has **one** computing
  place, `FollowBlitRules.offsetFor`, which takes the whole `MapCanvasUiState` and reads `resolvedAnchor`
  itself: the anchor is not a parameter, because the screen's previous inline
  `OverrunWindow.shiftPx(…, activeFollowAnchor.fx, activeFollowAnchor.fy)` call site was
  invisible to every case and needed two fixes in one day (the dated record is `MapRendering.why.md` §1). A caller that rebuilds
  the state around the preset is refused too: the guard reads the code around the seam call, so
  `offsetFor(ui.copy(resolvedAnchor = ResolvedAnchor(ui.activeFollowAnchor.fx,
  ui.activeFollowAnchor.fy)), …)` fails it (`MapRendering.why.md` §1 carries the measurement).
  `FollowBlitCallSiteTest` holds the screen to the seam (one call, no inline offset computation, no raw
  preset at the seam call site or in the seam) and `MapCanvasViewModelVehicleAnchorTest`
  `followBlitOffsetTakesTheResolvedAnchorNotTheRawPreset` drives it with measured overlays and the
  production `followRenderTarget(...)`: persisted preset
  `fy = 0.9` resolves to `0.733`, the seam's blit is the prediction drift (0 px, unclamped), and the same
  geometry with the preset is 320 px and clamped. With no measured overlay (browse mode, tests)
  every preset resolves to its exact fraction; non-covered presets (including the default center/center)
  do the same with overlays present, so the default framing is exactly the pre-feature framing in every
  mode and orientation (a 2026-09-16 on-device report — vehicle left of center in portrait navigation —
  showed the earlier visible-area *remap* moving the default; collision-remap replaced it).
  **Android Auto uses the same collision rule** for its host panel: the panel is 40% of the surface
  width on the leading edge (`PANE_FRACTION`, side from `isHostPaneOnRight`), treated as a forbidden
  band — `clampAnchorOutOfPane` moves only the presets that fall inside it and never the default
  (`AutoMapRenderer.anchorCenterFor`). The AA follow blit offset uses that same resolved value
  (`resolvedFollowAnchor()` at both `shiftPx` call sites) — passing the raw preset there keeps
  the offset outside the overrun margin for pane-band presets, turning every tick into a full render.
  A remap of the whole grid into the remaining strip was tried and
  rejected: it moves the default preset to 0.7 and changes the specified default framing.
- **Rule: Every chrome branch that composes a control measures it, and the probe spans the whole band.** The
  right-side widget column publishes its width through `LocalOverlayWidthProbe`, which the chrome band
  provides **once around its content** (`MapCanvasScreenChromeBand.kt`, the content of the `MapLayer.CHROME`
  box) — the band
  composes one column from its browse branches and from its navigation branch, only one at a time, and
  the two do not measure the same width: the navigation column reserves the speed-limit slot and
  measured 64 px against the browsing column's 56 px in the 411 px window (2026-10-09). A provider
  around one branch leaves a band that composes while navigation is already active with no right inset
  at all — the car-session resume composes the band from scratch (`MapCar.md` §18), so the driver's phone framed the
  followed map with a right-edge preset under the column for the rest of the session (change
  `fix-nav-column-width-probe`, archived 2026-10-09).
- The anchor presets are bounded to `0.1..0.9` on purpose: that is exactly the overrun margin
  (`(1.2 − 1)/2 = 0.1` of the surface), so the visible window always lies inside the rendered frame.
  A new preset outside that range would require a larger `canvasOverrun`, and
  `resolveAnchorFraction` clamps into the band for the same reason.
- The drawn offset MUST stay inside `(bitmap − canvas)/2`; `OverrunWindow.shiftPx`
  returns the clamped value and a `clamped` flag, and the follow display loop requests a full render
  at the displayed position when the flag is set (the map keeps scrolling instead of sticking at the
  buffer edge). That clamp is the LAST resort, not the trigger: `FollowRefreshPolicy` requests the
  frame while margin remains (§4), because a clamped offset freezes the drawn content until the new
  frame lands.

## 1b. The render modes and the path selection

- Two user-selectable modes in `MapRenderer` (`renderMode`: `TILES` default, `DIRECT`):
  - **Rule: TILES mode — tile path** (`renderMode == TILES && (angle == 0.0 || !forceFullRender)`):
    visible geo bounds are covered from geographic tiles in `TileCache`; missing tiles are
    rendered natively one per tile (`renderTilePixels`, 256px @ 96dpi scaled by the render DPI
    the request carries — the drawing surface's own DPI) and
    cached; the composed frame is screen-sized (e.g. 1080×2400). Rotated live previews compose
    tiles north-up and rotate the whole canvas about the viewport center — labels stay north-up.
  - **Rule: DIRECT mode — full native path**: every render goes through `MapRenderUtil.renderToBitmap`
    onto an overrun canvas (1296×2880 = 1.2× screen), then `extractCenterRegion` → 1080×2400.
    Labels render natively in the viewport direction. No tile cache is read or written.
  - **Rule: The full render's pixel storage is caller-owned** (change `reduce-render-peak-memory`, spec
    `render-performance` — A render writes into caller-owned pixel storage): a full render goes
    through `MapRenderUtil.renderInto`, which takes a DIRECT `ByteBuffer` from `RenderBufferPool`
    (`:core`), has the native side write the frame straight into it (JNI
    `OSMScoutClient.renderInto`, the buffer-taking entry point), and copies it into the target with
    `Bitmap.copyPixelsFromBuffer`. No `jintArray`, no C++ pixel vector, no per-pixel conversion, and
    no per-render frame-sized allocation: the storage is pooled per size class (≤2 free, ≤2 classes)
    and the native side keeps no reference to it beyond the call. `renderToBitmap` keeps the
    allocating entry point for callers that need an owned result.
    - Format contract: 4 bytes per pixel, stride `width * 4`, no padding, one `0xAARRGGBB` int per
      pixel, buffer order `ByteOrder.nativeOrder()` — what `Bitmap.setPixels` and
      `copyPixelsFromBuffer` both expect.
    - The **tile path keeps the allocating entry point** (`renderTilePixels` renders one 256 px tile
      at a time): a tile is not frame-sized, and `TileCache` retention is governed by its own policy.
  - Forced full renders (rotation gesture end, `forceFullRender = true`) use the full native path
    in BOTH modes when the angle is non-zero, so labels are drawn in the correct direction.
- Path selection in `executeRender`:
  `val tilePath = renderMode == RenderMode.TILES && (job.angle == 0.0 || !job.forceFullRender)`
  - Tile path bails (→ full native render) on antimeridian views, tile render failure, or a tile
    grid wider than the 4×4 sanity guard.
- The overrun buffer is what the display shows: the frame emitted to the UI is the OVERRUN-sized
  front buffer (`crop = false`), drawn at natural size and positioned by the **display offset**
  described in `MapFrames.md` §13. A screen-sized crop in the UI is the old (pre-overrun-window) contract —
  do not reintroduce it: the follow scroll and the pan window both need the margin around the
  visible area.

## 1c. The overlay stage, the mode switch, the low-zoom risk and the removed helpers

- **Rule: Overlay stage (final)** the GPS marker is NOT part of the native render. After a frame is
  emitted, `MapCanvasScreen` composes `LocationMarkerOverlay` on top of the displayed bitmap.
  Tiles, back buffer, and front buffer contain only static map content — never the marker.
- **Rule: Mode switch** `onSetRenderMode` in the ViewModel persists the choice and applies it via
  `mapRenderer.invalidateStyle()` (epoch bump + tile cache clear + forced full re-render), so no
  tiles/buffers from the other mode survive. `renderMode` is a `@Volatile` field read at job
  execution — an in-flight job may finish in the old mode; its result is discarded by the epoch
  check.
- **Rule: DIRECT mode low-zoom risk** at mag < 4 a full native render covers huge world viewports
  (z=2 ~5s, z=1 hangs). The zoom floors (`MIN_MAG`/`GESTURE_MIN_MAG` = 4) prevent gesture-driven
  low zooms in both modes; DIRECT at mag 4 is still a full-world first draw — expect slowness
  before the frame lands.
- **Rule: Removed** the old screen-space tile-split helpers (`TileCache.storeTiles`/`compose`,
  `computeTileGrid`, `MapRenderer.blitSubRegion`) are gone; the tile cache is geographic-only.

## 1d. Marker projection and the overlay viewport

- **Rule: Marker projection** the GPS marker is drawn by a separate Compose overlay with no offset of
  its own, so its viewport must absorb the blit offset: in follow mode the marker projects against
  the **anchor center of the displayed (eased predicted) position** — the viewport for which that
  position projects to the anchor. Projecting against the *frame's own* center leaves the marker
  ahead of the map content by exactly the blit offset (drifts, then snaps on the next commit).
  In browse (follow off) no offset is applied and the marker projects against `renderViewport`.
- **Rule: Analysed-route-segment overlay (delta `route-planning-session`)** the analysed step's polyline
  range is drawn by a Compose overlay (`ui/map/RouteSegmentHighlightOverlay.kt`) on the layer **above
  the rendered frame and below the markers/pins** — the route itself stays in the native frame, so the
  highlight must not be baked into it (a cached tile would carry a stale highlight). It projects with
  `ProjectionUtils.viewport(...).geoToScreenRotated` against the **displayed frame's** viewport and takes
  the same pan display offset and zoom-anchor scale as `LocationMarkerOverlay`, so it rides the content
  on the tile path and the full-render path alike.
- **Rule: Android Auto overlays are drawn INTO the surface** (`AutoMapRenderer.drawGpsMarker`,
  `drawDestinationMarker`), so they cannot pick a different viewport per overlay: the renderer
  remembers the offset the displayed frame was blitted by (`blitOffsetX/Y` — set in `blitToSurface`,
  reset for a fresh render in `drawToSurface`) and every map-content overlay subtracts it. Same rule,
  different mechanism: an overlay that projects map content must carry the blit offset.
- **Rule: Displayed frame, not the pending render target (delta `overlay-projects-against-displayed-frame`)**
  `AutoMapRenderer` keeps TWO frame descriptions: the pending render target (`viewportLat/Lon`,
  `viewportZoomFraction`, `viewportAngle` — written by `setViewport`, `reCenter`, `reengageFollow` and
  the extrapolation clamp branch) and the DISPLAYED frame (`overrunLat/Lon`, `overrunMag`,
  `overrunAngle` plus the blit offset — published when a frame is committed in `fullRender` or blitted
  in `blitToSurface`). Every overlay projects against the DISPLAYED frame through
  `displayedLat/Lon/Mag/Angle` (falling back to the target before the first frame exists). The two
  differ for one render latency (100 ms debounce + one Cairo render) after EVERY viewport write, and
  the AA fix path (`setViewport` + `reengageFollow`) re-anchors the target on each fix — projecting the
  marker/pin against the target detaches them from the map content by that delta (one fix step: ~17 px
  at 50 km/h, mag 16, and margin-sized when the clamp branch also moves the target) and snaps them back
  on the commit. The phone has the same separation (`renderViewport` is committed atomically with
  `renderedBitmap`); do not "simplify" either side into a single frame field. Each write requests its own
  frame (change `fix-car-follow-reengage-render`): `setViewport` unconditionally, `reengageFollow` only
  when no frame is already scheduled, so the pending description always has a frame behind it.
- **Rule: The follow blit offset is measured on the displayed vehicle position**, not on the frame center:
  `renderFrame` passes the displayed position (`markerPosition()`) to
  `OverrunWindow.shiftPx`, with `overrunLat/Lon` as the frame. A frame *center* is not a
  point of the rendered bitmap, so passing it charges the offset with the anchor displacement
  (`(fy − 0.5) × h` — 240 px for a bottom-row preset on a 600 px surface) against a 60 px margin: the
  offset is permanently clamped and every follow viewport change (i.e. every fix while heading-up)
  falls back to a full native render. Measured before the fix: **101 full renders for 105 fixes** in
  104 s; the blit path was reachable only for the exact center anchor.
- **Rule: `viewport.center` vs render target** `MapCanvasUiState.viewport.center*` always means "the geo
  position at the screen center" — in follow mode that IS the anchor center, and it is what gesture
  commits (`rotateZoomAtFocalPoint`), the mini-map and the route-fit helpers must use. The emitted
  `renderViewport` is the frame's geometry (center/mag/angle) for the marker and the display loop.
  Leaving the vehicle position in `viewport.center` would make it a lie of up to 0.4 screen and jump
  on the first pan after re-engage.
- **Rule: A camera fit is verified, never just rounded (delta `fix-area-fit-zoom-rounding`)** every fit that
  parks the camera to fit an extent must route through `verifiedAreaFit`
  (`app/src/main/java/com/naviveylin/ui/map/FitVerification.kt`), which starts from
  `MapCanvasViewModel.Companion.computeAreaZoom` and steps one whole level out while the projected bbox
  leaves the visible band. `computeAreaZoom` alone is not a fit: it rounds the magnification
  (`Math.round`, so an exact fit can move UP to the next level — 2^0.5 ≈ 1.41x more content than its
  80 % target, past the viewport), and it assumes the bbox midpoint is the camera center, while the
  area-favorite fit parks the camera on the favorite coordinate and the POI fit on the POI — an
  off-centre camera clips at an exact fit. A rotated viewport needs a larger screen hull than north-up.
  The verification takes the **camera center** and the band (`coveredPx`, 0 when no sheet covers the
  canvas) as parameters, projects the bbox's four corners through `ProjectionUtils.viewport` — the
  renderer's own projection, so rotation is exact — and passes the *visible* height (`height −
  coveredPx`) to the fit itself, as the route overview does. Stepping stops at the caller's floor: the
  route overview, the POI fit and the embedded result map pass the render-stability minimum (`MIN_MAG`),
  the area-favorite fit keeps the documented area-favorites floor — a fit that returns its floor has NOT
  been verified to fit, so callers must not read the floor as success. Adding a fit caller means calling
  this seam, not `computeAreaZoom` directly.
- **Rule: Single follow center (delta fix-follow-vehicle-jumps)** the displayed (eased predicted)
  position IS the follow center. The display loop (`MapCanvasScreen`) writes
  `MapCanvasViewModel.followDisplayLat/Lon` every frame; BOTH follow render paths (the per-fix
  zoom/rotation commit and the clamp re-anchor `renderFollowFrameAt`) anchor on it via
  `followRenderTarget` — never on a raw-fix-derived center. A fix updates prediction/state only;
  position scroll happens in the display loop within the overrun margin (the fix-arrival sawtooth
  was the per-fix re-centers alternating with the clamp re-centers). Fallback to the marker fix
  only before the first display frame.
- **Rule: Frozen stop/go (same delta)** below `FOLLOW_MIN_SPEED_KMH` the displayed position and the
  blit offset are FROZEN, never reset, and the marker keeps riding the frozen displayed position;
  the display is reset (offsets zeroed) only when follow mode disengages. The bounded-rate display
  lives in `core/FollowDisplayState`: the displayed position advances at the prediction's committed
  effective speed (`FollowPrediction.effectiveSpeedMs`) plus a correction toward the extrapolated
  target, clamped to `CORR_FRACTION` (0.25, floored at `CORR_FLOOR_M_S` = 1 m/s) of that speed — a
  fix-arrival target that drops behind the display slows the map to 0.75x, it never freezes it and
  never reverses it; the display snaps when the target gap exceeds `TELEPORT_SNAP_METERS`. The
  correction and the snap are skipped while the reference is older than `STALE_REFERENCE_SEC` (3 s),
  so a lost fix dead-reckons at the decaying speed instead of being dragged back onto a fix the
  vehicle has long left. The committed speed itself moves toward the estimate at no more than
  `BOUNDED_STEP_MS2` = 2.5 m/s², so the deceleration cap cannot step the displayed rate by 30 % in
  one fix. The pre-2026-10-12 monotonic forward-only hold is GONE: measured on the emulator it
  stopped the map for up to 2.7 s (29 % of the driving time at a steady 50 km/h) and paid the gap
  back at up to 2.9x the vehicle speed.
- **Rule: AA follow loop shares the same contract (delta fix-aa-follow-vehicle-jumps)**
  `AutoMapRenderer`'s extrapolation loop drives the displayed position through the SAME
  `FollowDisplayState` (phone and AA use one tested bounded-rate rule) — `extrapolationTick`
  re-syncs the state after any frozen gap and copies the advance back into the `@Volatile`
  display fields. The stopped gate FROZES the display + marker (no NaN reset / raw-fix fallback;
  a stationary fix re-seeds the viewport to the fix ONCE per stop episode, then jitter fixes are
  inert). The margin render request is throttled at `RENDER_REQUEST_INTERVAL_MS` = 200 ms (phone
  parity), shrinking the freeze-then-advance step at the overrun edge; the resolved-anchor blit
  call sites in the same function stay untouched (single resolved anchor rule above).
- **Rule: AA follow render target = the DISPLAYED position; rotation commit deadband (delta `aa-follow-framing-and-zoom-parity`)** on AA the follow render target is the anchor center of
  the displayed (eased predicted) position — the same point the extrapolation loop advances — never
  the raw fix (raw fix only before the first display frame), so a fix commits a centre-only change
  the overrun blit serves and no longer re-anchors/steps the whole map per fix. The heading commit
  gets a deadband in the shared commit gate (`MapPanHandler.resolveCommittedAngle`,
  `HEADING_DEADBAND_RAD` ≈ 1.5°): the rotation is re-committed only when the smoothed heading moved
  more than the deadband from the COMMITTED rotation (measured against the committed value — the
  heading-up lag can never accumulate). Below it the fix keeps the committed rotation (centre-only
  commit, blit-able); above it the rotation commits with the fix as before. A rotation cannot be
  blitted, so the deadband removes the ~1 forced full render per fix for the ~0.9°/s heading signal
  while a real turn still tracks (lag ≤ deadband).

## 1e. The follow display contract on phone and Android Auto

- **Rule: AA magnification changes are a transition, not a single-frame step (same delta)** a committed
  auto-zoom/turn-zoom magnification change is applied across display frames by scaling the overrun
  blit about the FOLLOW ANCHOR (not the surface center), easing the displayed magnification toward
  the committed value at the display rate; a step beyond the blit-able window (≈0.26 levels at
  `OVERRUN_FACTOR = 1.2`) falls back to a full native render at the committed magnification, and
  every transition ends on an exact native render at the committed value. This removes the measured
  7–12 % single-frame scale snap (20–40 px at the frame edges).
- **Rule: A larger magnification request is WALKED, not landed (delta `aa-entry-zoom-animation`)** a
  transition-eligible zoom commit (`setViewport(..., walkZoom = true)` — the auto-zoom commit path
  of `FreeDrivingScreen`/`NavigationScreen`) farther than `ZOOM_BLIT_LIMIT` from the committed
  magnification becomes a WALK TARGET instead of being applied in one frame: `advanceZoomWalk`
  commits one step per LANDED render (render-synchronous — the committed value never leads the
  frame by more than one blit window, so the display never has to snap), each step ≤ the blit
  window, and the last step lands EXACTLY on the requested value (entering free driving walked
  `13.0 -> 17.0` in 16 steps instead of a 16x area jump; measured `mag 13.000 -> 17.000` in one
  frame before). The walk runs on its OWN loop (`startZoomWalkLoop`, `ZOOM_WALK_FRAME_MS`) —
  deliberately NOT the extrapolation loop, which is movement-gated (`MOVEMENT_SPEED_MS`) and would
  stall a transition started while parked. While the extrapolation loop is closed that loop also
  owns `advanceDisplayedMagnification`, otherwise the frames would land at the new magnification
  while the displayed scale stayed behind. A request inside the blit window commits directly (no
  walk, no extra render), a fix that re-commits the value it read back does NOT cancel a pending
  walk, and the newest request wins (monotonic per leg, never past the requested value). Gesture
  and zoom-button paths keep their immediate response by not passing `walkZoom`.
## 2. Bitmap Lifecycle (CRITICAL — caused "jumps" multiple times)

- `Bitmap.createBitmap(src, x, y, w, h)` and `frontBuffer` SHARE pixel memory with `src`.
- The next render writes into the same memory via `backBuffer.setPixels()` → the image displayed
  by Compose is overwritten in place with new content at the old crop offset → horizontal jumps
  without rotation.
- **Rule: Every frame that goes to Compose (`_frontBufferFlow.value`) must be an independent
  copy** (`bitmap.copy(...)` or `extractCenterRegion(...).copy(...)`).
- `extractCenterRegion` MUST always return `copy()` — even when `fbW == sw` (otherwise Compose
  draws the front buffer directly, which gets recycled on the next render).
- Sub-region blits: copy the region first, then `recycle()`. `Bitmap.createBitmap` + `recycle` on
  a shared region causes a "trying to use a recycled bitmap" crash.
- Tile path: `frontBuffer` and `_frontBufferFlow` each get a copy, then the composition target is
  **released** back to the pool (it used to be recycled).
- **Rule: a render target is released, never recycled** (change `fix-render-buffer-reuse`, spec
  `render-performance`). The render path draws into a target obtained from `RenderBitmapPool`
  (`:core`): `acquire` → use → `release`. The pool is the only recycler — it recycles surplus and
  evicted targets itself, and it refuses a release of a target it did not hand out (or a double
  release) with a `RENDER` diagnostics line. A caller that calls `recycle()` on a pooled target
  hands the pool a dead bitmap, and the next render fails on it.
- **Rule: a frame still in use is never handed out again.** A target that is displayed (Compose's
  emitted frame, the car's overrun frame) must not be the target of an in-flight render; that is
  why the pool retains a pair of slots per size class and why the frame handed to Compose stays an
  independent copy rather than a pooled target.
- **Rule: the frame's pixel storage is caller-owned too** (`RenderBufferPool`, change
  `reduce-render-peak-memory`). The render path acquires a direct buffer, the native entry point
  writes the frame into it for the duration of that one call, the target is filled with
  `copyPixelsFromBuffer`, and the buffer is released back. The pool never hands the same storage to
  two renders, a release it did not hand out (or a double release) is refused with a `RENDER`
  diagnostics line, and the native side retains no reference — so a caller may release, reuse or
  display the storage afterwards. `RenderBufferPoolTest` pins the bound, the refusal and the format.
  The "is this frame unchanged?" decision needs no second bitmap either: the frame flow's own value
  is the previous frame, and `frontBufferSeq`/`lastEmittedSeq` say whether it changed.
## 4. Render Coalescing / Throttling

- Limit follow-mode renders to one per `GPS_FOLLOW_RENDER_INTERVAL_MS` (200 ms).
- Every fix still goes to `prepareViewport` (keep target viewport current), but only one render
  per time window is triggered. This keeps the native pipeline from overflowing.
- `positionChanged` threshold: render only from > 5 m movement (or first fix). Otherwise
  jitter renders.
- **Rule: The follow frame is requested BEFORE the overrun margin is exhausted** (`FollowRefreshPolicy`,
  spec `smooth-follow` — Follow frame refresh before the overrun margin is exhausted). In a
  steady-heading drive the clamp used to be the ONLY trigger (`shouldRender` is false while the
  display loop runs, and a constant heading never sets `angleChanged`), so the request was issued
  when the margin was already gone and the display sat pinned at the margin for a whole render —
  measured on the emulator 2026-10-12: byte-identical frames for 1.5-2.7 s, 29 % of the driving time
  at a steady 50 km/h, then the stall paid back in one frame. The policy requests at
  `share x margin`, `share = MAX_SHARE` (0.55) when the render latency is unknown, and
  `min(MAX_SHARE, max(MIN_SHARE, 1 − latency/margin − SAFETY_SHARE))` when it is known, one request
  outstanding at a time. The clamp remains the last resort for a render slower than the margin the
  threshold leaves (a latency above ~45 % of the margin, ~108 px or 2.4 s of travel at 50 km/h).
## 5. Epoch Rules

- `epoch.incrementAndGet()` ONLY on zoom, overlay, style, marker visibility, and map-data-set
  changes.
- **Rule: Map data set changes (basemap download/update/delete, and a *regional* database replaced by an update, while the app runs) use the same invalidation contract as mode/style switches**
  `MapRenderer.invalidateData()` = epoch bump + tile cache clear + forced full re-render (mirrors
  `invalidateStyle()`), because cached tiles were rendered without the new data. Driven by
  `BasemapReloadNotifier` (spec: basemap-loading), which the update path bumps after it has re-opened
  the installed database set (`MapDataRegistrar`, spec: map-updates — "An installed update is rendered
  without an app restart"). `AutoMapRenderer.invalidateData()` = blit-eligibility invalidation +
  re-render (overrun buffer may hold pre-change pixels).
- **Rule: Pure GPS position updates must NOT increment the epoch** — otherwise the running native render
  is discarded as stale and frame gaps occur ("map freezes / shows old image").
- The conflated render queue guarantees that the next job picks up the latest position.

---

## Sections that moved out of this document

The numbers below are carried by the document named, and this document no longer states their rules:

- §3, §6, §7, §8, §9, §10, §11 — `guidelines/MapFollow.md`.
- §12, §13, §14 — `guidelines/MapFrames.md`.
- §14 (the Android Auto renderer), §18 — `guidelines/MapCar.md`.
- §15, §15a, §16, §16a, §16b, §17 — `guidelines/MapStyling.md`.
- §19 — `guidelines/MapRoute.md`.
- §20 — `guidelines/MapStyling.md`.

The regression checklist is `guidelines/MapFrames.why.md` "Known Pitfalls"; the parameter table is
`guidelines/MapRendering.recipes.md` "Parameter Overview".
