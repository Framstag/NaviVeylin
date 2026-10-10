# Proposal

## Why

The native renderer reads its projection DPI from one process-global value
(`ClientData.settings.mapDpi`, `OSMScoutClient.cpp:1348`), while every renderer
projects its own gestures, tiles and overlays with a per-instance DPI. When the
Android Auto session and the phone UI share a process — always the case for
projection — the two drift apart: the car writes its surface DPI, the phone's
renderer keeps its own frozen value, and the map raster is then rendered at a
different scale than the overlay math assumes. The user sees the vehicle marker
and the map "far off" from each other (≈1.8x on a 236-dpi head unit, per the
JNI comment) until the app process is restarted.

The same defect exists in the opposite direction (phone writes, car renders) and
is not visible only because the car re-applies its DPI at every screen start.
Both surfaces can be wrong; nothing enforces that the shared value matches the
surface that is drawing.

## What Changes

- **Render DPI becomes a per-render argument.** The JNI render entries
  (`render`, `renderWithRouteAndPois`) SHALL take the physical DPI of the frame
  being rendered; the native projection SHALL use the passed value. The
  process-global `Settings.mapDpi` SHALL no longer influence rendering.
- **The bridge's DPI setter is removed.** `OSMScoutClient.setMapDpi` (a
  NaviVeylin addition) has no remaining reader once the projection is a render
  argument; its Java declaration and JNI implementation go away, and the car's
  `surfaceDpi` collector stops mutating the client.
  **BREAKING** for the Java bridge API (`:osmscout-client-java` +
  `libosmscout_client_java.so`) — both sides ship in one APK, but a mismatched
  pair fails *silently*: JNI symbol names carry no parameter list, so an old
  library paired with a new declaration would read the request's arguments in the
  old order.
- **Both surfaces pass their own DPI on every render call**: the phone's
  `MapRenderer.dpi` (full renders and per-tile renders) and the car's
  `AutoMapRenderer.projectionDpi`. A renderer's projection DPI and the DPI of
  its render calls can no longer disagree.
- **Tile content becomes DPI-attributed.** A tile's pixels depend on the DPI it
  was rendered at, so `TileCache` must key or invalidate on it; today's key
  `(zoomLevel, tileX, tileY)` would serve tiles rendered under a foreign DPI.
- **Guidelines updated**: `guidelines/MapRendering.md` (tile DPI and the
  blit-eligibility rule naming DPI) and `guidelines/UI.md` §3a (the host-callback
  rule that names `setMapDpi` as the thing a background collector applies).

Scope: **cross-cutting (phone + Android Auto + AAOS)** — the defect is the
shared value itself, so a fix on one surface only moves the breakage. Not a new
user-facing feature; no UI, no settings, no manifest change.

Additive/breaking: additive for behaviour (rendering output is unchanged when
one surface draws alone), breaking for the bridge API surface only.

Rollback: revert the main-repo commit **and** the submodule gitlink together.
A mixed pair (new Java declaration against an old `.so`, or vice versa) does not
fail loudly at the first render — JNI symbol names carry no parameter list, so
the native side would read the arguments in the old order — which is why the two
sides must always move in one commit and no partial rollback exists.

## Capabilities

### New Capabilities

- `render-projection-dpi`: the contract for how a surface's physical DPI reaches
  the native renderer — passed with each render request, owned by the calling
  renderer, with no process-global projection DPI shared between surfaces.

### Modified Capabilities

- `osmscout-jni`: the JNI render entries take the projection DPI as a parameter,
  and the bridge exposes no client-wide DPI setter.
- `map-render`: the phone render path passes the surface DPI on every render
  call (full render and per-tile render) instead of setting a client-wide value
  once at map init.
- `tile-cache`: cached tile content is attributed to the DPI it was rendered
  with; a change of the render DPI cannot serve tiles rendered at another DPI.
- `auto-map-renderer`: the car renderer passes its surface DPI per render and
  stops mutating client-wide state when the host delivers a surface.
- `marker-render-accuracy`: the GPS marker projection uses the same DPI value
  that the frame it is drawn on was rendered with.

## Impact

Affected modules: `:app`, `:auto`, `:core`, `:osmscout-client-java`, and the
libosmscout submodule (branch `naviveylin-local`). No manifest, resource,
Gradle-plugin or DI-graph change; `RendererGate.surfaceDpi` keeps its meaning as
the surface's DPI, it just no longer reaches the client.

Native side — **submodule patch (minimal, upstreamable) plus its local Java
counterpart, never both places for one change** (AGENTS.md "patch in one, never
both"):

- `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp`
  (submodule, `naviveylin-local`): render entries read the DPI argument; drop the
  `data->settings->GetMapDPI()` read and the `setMapDpi` JNI function; bump the
  main-repo gitlink after the submodule commit.
- `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`
  (local override module): the matching Java declarations. `setMapDpi` exists
  only in this override — the submodule's Java source has no such method.

Kotlin/Java:

- `core/src/main/java/com/naviveylin/core/MapRenderUtil.kt` — `renderInto` /
  `renderPixels` carry the DPI to the JNI call.
- `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` — pass `dpi` on the
  tile render and the full render.
- `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — drop the
  `client.setMapDpi(density)` call at `initMap`.
- `app/src/main/java/com/naviveylin/ui/map/TileCache.kt` — DPI attribution.
- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` — pass
  `projectionDpi`; keep `updateProjectionDpi` invalidating DPI-dependent state.
- `auto/src/main/java/com/naviveylin/auto/RendererGate.kt` and the four car
  screens (`NavigationScreen`, `MapScreen`, `DetailsScreen`, `FreeDrivingScreen`)
  — surface DPI reaches the renderer only; the client-mutating collectors are
  removed.

Tests and fakes that mirror the bridge signature:
`app/src/test/java/com/framstag/libosmscout/client/FakeOSMScoutClient.kt`,
`auto/src/test/java/com/framstag/libosmscout/client/FakeMapScreenClient.kt`,
plus `MapRenderUtilTest`, `MapRendererSmokeTest`, `TileCacheTest`,
`RendererGateTest`, `AutoMapRenderer*Test`. The committed host stub
(`app/src/test/jniLibs`, `auto/src/test/jniLibs`) needs no change — it exports no
symbols.

Docs to update in the same change: `guidelines/MapRendering.md`,
`guidelines/UI.md`, and the new capability's spec; `AGENTS.md` is unaffected
(it documents no DPI rule).

## Decisions and Open Questions

### Decided

1. **`setMapDpi` is removed, not kept inert.** One source of truth for the
   projection DPI; the native `Settings::SetMapDPI` stays untouched upstream (the
   Qt client still uses it, which this app never builds). The four car screens'
   client-mutating collectors are deleted in this change. The car keeps
   `RendererGate.surfaceDpi` for its renderer and overlay math; only the client
   mutation disappears.
2. **Tile-cache DPI attribution is in scope for this change.** The phone
   renderer's DPI is fixed per instance, so the phone cache is correct today;
   this is hardening, and the existing requirement text ("tile content depends
   only on geographic position, zoom level, and style") is already false once
   the DPI is a render argument. Left as a stated requirement change in
   `tile-cache` rather than a later fix.
3. **The `LocationService` start/stop asymmetry stays out of this change** and
   is handled as its own entry: phone `ON_PAUSE` (`MapCanvasScreen.kt:821`) and
   the car's `AutoLocationProvider.stop()` (`AutoServiceModule.kt:230`) both stop
   one non-refcounted subscription, so the later stop wins. Same class of defect
   (one process-wide resource, per-surface owners), different symptom (stale or
   missing fixes instead of a scale mismatch).

### Open

None — all decisions above are settled. Remaining detail (which value each
surface passes, ordering, test seams) belongs in `design.md`.
