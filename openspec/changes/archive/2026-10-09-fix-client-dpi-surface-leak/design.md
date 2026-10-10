# Design

## Context

See `proposal.md` — Why, for the defect and its symptom. What shapes the
approach:

- The JNI render entries currently project with a value read from shared client
  settings (`OSMScoutClient.cpp:1348`), while every renderer keeps its own
  projection DPI for overlays, gestures and tile geometry: the phone's is
  `MapRenderer.dpi` (`MapRenderer.kt:45`, immutable, used at `:605`, `:643`,
  `:978`), the car's is `AutoMapRenderer.projectionDpi`
  (`AutoMapRenderer.kt:70`, `@Volatile`, updated by `updateProjectionDpi`).
- Both surfaces reach the native render through exactly three call sites:
  `MapRenderUtil.renderPixels` (`core/…/MapRenderUtil.kt:155`, `:163`) for full
  frames on both surfaces (`MapRenderer.kt:799`, `AutoMapRenderer.kt:1244`) and
  `client.renderWithRouteAndPois` for the phone's per-tile renders
  (`MapRenderer.kt:755`).
- The only writer of the shared value from the app side is
  `OSMScoutClient.setMapDpi` — a NaviVeylin addition. Its Java declaration lives
  in the local override module (`osmscout-client-java/…/OSMScoutClient.java:195`)
  and its JNI implementation in the submodule
  (`OSMScoutClient.cpp:1090`); the submodule's own Java source has no such method.
- The bridge already carries a per-call projection DPI elsewhere: the local
  override's `projectToPixel(…, double dpi, …)`
  (`osmscout-client-java/…/OSMScoutClient.java:647`) takes it as an argument, so
  D1 applies the bridge's existing shape to the render entries instead of
  inventing one.
- `guidelines/Design.md` §5 requires a small, explicit JNI surface, upstreamable
  submodule patches and exact mirroring of upstream APIs; §4 forbids native calls
  on the main thread, which the car's current DPI collector satisfies only by
  hopping to a background dispatcher.
- Three JNI `render*` symbols exist: `render` (`:1293`, delegates),
  `renderWithRouteAndPois` (forward declaration `:1196`, definition `:1314`).
- `MapRenderUtil.renderToBitmap` (`:40`) has no production caller; only tests
  call it.

## Goals / Non-Goals

**Goals:**

- A frame is projected at the DPI of the surface it is drawn on, decided solely
  by the render request, in any order of surface activity and without a process
  restart, screen re-entry or map re-initialisation.
- The invariant "renderer projection DPI == DPI the native render used" becomes
  structural — a single call path where the value cannot be omitted.
- One source of truth for the car host's DPI (the delivered surface value) and
  for the phone's (`DisplayMetrics.densityDpi`), with no third, client-wide copy.

**Non-Goals:**

- No change to the native projection maths, the symbol/font sizing, the tile
  path's geometry, the overrun/blit rules or the epoch rules.
- Not giving the phone a runtime-mutable projection DPI (the point is that it
  needs none), and not unifying the two renderers.
- Not touching `Settings::SetMapDPI` / `mapDPIChange` in `libosmscout-client`
  (upstream concept, used by the Qt client this app never builds).
- Not the `LocationService` start/stop asymmetry (proposal, decision 3).

## Decisions

### D1 — Carry the DPI with the render request

Chosen: the JNI render entries take the physical DPI as an explicit parameter of
the request, and the projection uses it. The value travels:
`MapRenderer.dpi` / `AutoMapRenderer.projectionDpi` → `renderInto(…, dpi)` →
`renderPixels(…, dpi)` → `client.render(…, dpi)` /
`client.renderWithRouteAndPois(…, dpi)` → native `MercatorProjection::Set(…)`.

Alternatives considered:

- *Re-establish on display*: keep the shared value, make the phone re-apply its
  density when it becomes the foreground surface, and make `MapRenderer.dpi`
  mutable with a tile-cache invalidation path. Smaller diff, but the invariant
  stays a discipline two writers must remember (and the car's `surfaceDpi` is a
  `StateFlow` that does not re-emit an unchanged value, so the mirror case needs
  its own unconditional re-apply), and every future surface inherits the problem.
- *Per-surface native client*: real isolation, but two DB threads and a second
  `N × cache` tile-data set — conflicts with the car RAM budget the tile-cache
  work was sized against (`TODO.md` §51/§63).
- *Thread-keyed DPI lease*: no signature change, but the render mutex serializes
  renders from arbitrary threads, so a lease keyed by the calling thread cannot
  be made correct without a second registry — the indirection is the same size as
  the parameter, with none of the visibility.

### D2 — Remove `setMapDpi` rather than leave it inert

Chosen: delete the JNI function and the Java declaration, and drop the
client-mutating collector from the car screens. Rationale: after D1 nothing reads
the client-global value for rendering, so any remaining setter is a trap for the
next reader. `Settings::SetMapDPI` itself stays untouched upstream.

Alternatives: *keep inert* (smaller diff, leaves a silently non-functional
API); *keep as an explicit no-op with a deprecation note* (same trap with more
ceremony).

### D3 — Require the DPI parameter (no default)

Chosen: `dpi: Double` is a required parameter of `MapRenderUtil.renderInto`,
`renderToBitmap` and `renderPixels`, placed before the defaulted overlay
parameters. Rationale: a default (e.g. 96) would silently reintroduce a wrong
DPI at any call site added later; a required parameter makes the compiler the
guard. Consequence: positional test call sites
(`MapRenderUtilTest.kt:154,179,203,207,217,221`) are updated.

### D4 — Tile cache keyed by the render DPI

Chosen: add the DPI to `TileCache.TileKey` (`TileCache.kt:85`). Rationale: a
tile's pixels are a function of the DPI, so the key must carry it; the epoch rules
and the composition stay untouched, and no purge storm is needed on a DPI change.
Alternative: *purge the cache on a DPI change* — needs every renderer that can
change its DPI to remember to purge (the car can, the phone cannot), i.e. the
same class of discipline the change removes. The phone's cache is per
`MapRenderer` instance, so the extra key dimension costs no memory in practice.

### D5 — Sources of the DPI stay as they are

Chosen: phone `context.resources.displayMetrics.densityDpi` (already how
`MapRenderer` is constructed, `MapCanvasViewModel.kt:1897`), car the DPI
delivered with the surface by the Car App Library callback, with
`AutoMapRenderer.initialProjectionDpi` (= `carContext.resources.displayMetrics.densityDpi`)
as the pre-surface fallback it already is. No new source, no settings storage,
no new plumbing for the car beyond passing the value it already tracks.

### D6 — Diagnostics replace the removed log line

Chosen: log the DPI a frame was rendered with at Debug level, once per renderer
instance (not per frame), from the Kotlin side, so the on-device check has a
signal after `[JNI] setMapDpi(<v>)` disappears. Rationale: the removed line was
the only evidence of the projection DPI in logcat; a per-frame log would be
noise in a hot path, and the native render's own verbose logging is deliberately
disabled (`OSMScoutClient.cpp:1349`).

### Threading and lifecycle (Design.md §4)

- No new component, thread, dispatcher or lock. The DPI is a scalar on the render
  request, read on the dispatcher that already runs that render: the phone's
  `rendererScope` (`Dispatchers.Default`, `MapCanvasViewModel.kt:1895`) and the
  car's `carScreenScope("AutoMapRenderer", Dispatchers.Default)`.
- The car's host-callback path gets *less* work, not more: `onSurfaceAvailable`
  keeps retaining the value (`RendererGate.surfaceDpi`) and applying it to the
  renderer; the background collector's `client.setMapDpi` hop disappears.
  `AutoMapRenderer.projectionDpi` stays `@Volatile` (written from the
  surface-availability path, read by render and gesture paths) and
  `updateProjectionDpi` keeps invalidating the blit eligibility and re-rendering.
- Lifecycle is unchanged on both surfaces: nothing is captured, nothing needs
  release, and no new reference to the surface, the renderer or the client is
  held.

### Files, modules and build

`:core` (`MapRenderUtil.kt`), `:app` (`MapRenderer.kt`, `TileCache.kt`,
`MapCanvasViewModel.kt`), `:auto` (`AutoMapRenderer.kt`, `RendererGate.kt`,
`NavigationScreen.kt`, `MapScreen.kt`, `DetailsScreen.kt`,
`FreeDrivingScreen.kt`), `:osmscout-client-java` (`OSMScoutClient.java` override),
and the submodule's `libosmscout-client-java/src/OSMScoutClient.cpp`
(branch `naviveylin-local`) plus the main-repo gitlink bump.

No Gradle change: the submodule `.cpp` reaches the build through the existing
CMake target (`osmscout_client_java`), and no dependency, flavor, ABI or asset
list changes. The native patch is a submodule patch (minimal, upstreamable: a
parameter on an existing projection call), with its Java counterpart in the local
override module — not both places for one change.

## Risks / Trade-offs

- [Bridge signature change: a `.so` built from the old source would not fail to
  link against the new declaration — JNI symbol names carry no parameter list and
  this bridge has no native overloads, so the old library would silently read the
  request's arguments in the old order (magnification/dpi crossed, a wrong scale
  or a crash) rather than raising `UnsatisfiedLinkError`] → one commit for the
  submodule patch *and* the gitlink bump; `:osmscout-client-java` is compiled
  from the pinned submodule, so a fresh clone cannot mix them, and an installed
  APK replaces both artefacts together. The host test stub exports no symbols and
  cannot catch it — the real gate is the CMake build plus an on-device render.
- [Merge conflicts in a single 8000-line translation unit] → keep the patch to
  three signatures, one line and one deletion; record it on the branch as
  upstreamable.
- [A call site omits the DPI] → required parameter (D3); the compiler lists every
  site.
- [First car frame before a surface exists is projected at the fallback density]
  → pre-existing behaviour, unchanged; the delivered surface triggers
  `updateProjectionDpi` and a full re-render, which the existing
  "Surface arrives before the renderer is ready" scenario already covers.
- [`renderToBitmap` (`:core`, test-only caller) becomes a second, DPI-less
  path] → same required parameter (D3).
- [Dropping the client mutation silently weakens the car's host-safety story] →
  it strengthens it: one fewer background collector that resolves the client.

## Verification

Unit tests (host JVM, `FakeOSMScoutClient` records the DPI of every render
request instead of the densities passed to the removed setter):

- `MapRenderUtilTest` — the DPI reaches the native call on both the overlay and
  the no-overlay branch, and `renderToBitmap`/`renderInto` pass their own value.
- A two-surface regression test: two renderers with different DPIs share one
  client; each render request carries its own renderer's DPI and neither is
  affected by the other's requests (this is the test that fails with the D1
  plumbing reverted — the mandatory revert-check).
- `MapRendererSmokeTest` — the tile path's per-tile request carries the same DPI
  as the renderer's full-frame request.
- `MapRendererSmokeTest`/`MapCanvasViewModelStyleTest` — map initialisation no
  longer configures a client-wide DPI.
- `TileCacheTest` — a tile stored at one DPI is not served at another, and the
  same key at the same DPI still hits (existing cases keep passing).
- `RendererGateTest` / `AutoMapRenderer*Test` — surface delivery applies the DPI
  to the renderer, calls no client method, and a DPI change still invalidates the
  overrun blit and re-renders.
- `FollowAnchorFramingTest`, `MapPanDisplayWindowTest` and the marker tests stay
  green: the marker and pan projections use the same DPI value as the frame.

On-device checks: the logcat and reproduction steps in the Migration Plan below.

### Known verification gap (accepted)

`AutoMapRenderer.updateProjectionDpi` clears `blitEligible` because the overrun buffer was
projected at the old DPI, but no test pins that line. `updateProjectionDpi` is followed by
`requestRender()`, which already produces a full render at the new DPI, and the display loop is
*supposed* to keep blitting the currently displayed frame while that render is in flight
(`renderFrame` — "the extrapolation loop can keep blitting the old frame while the native render is
in flight"). Measured: with the line commented out,
`RendererGateTest.aProjectionDpiChangeReRendersAtTheNewDpi` still passes; with the line present, an
assertion of "no blit after the change" fails against the real renderer. Accepted: the test pins the
observable contract (a DPI change yields a full native render whose request carries the new DPI),
the line stays as defence for the in-flight window, and pinning that window would need either an
in-flight-render concurrency test (fragile) or an `isBlitEligible()` test seam (pins an internal
flag, not behaviour).

## Migration Plan

Deploy: one change, one commit (submodule patch + gitlink bump + Kotlin), no data
or settings migration, no user-visible behaviour change on a single-surface run.

Verify in order:

1. Unit tests (see Verification above) — the two-surface regression must fail
   with the D1 plumbing reverted.
2. `:app`, `:auto` and `:core` unit tests, `:osmscout-client-java` compile, and
   the native library for all three ABIs (the signature change is native).
3. On-device, phone + AA (or AAOS AVD), `adb logcat -s NaviVeylin`:
   - `setMapDpi` lines are gone; the per-renderer Debug line shows the phone's
     density for phone frames and the delivered car DPI for car frames.
   - Reported reproduction: phone map visible → connect AA → navigate →
     return to the phone **without killing the process** → the vehicle marker
     sits on its road; force-stop + relaunch gives the identical result.
   - Mirror: car map visible → open the phone UI → return to the car → the car
     map's scale is unchanged.

Rollback: revert the single commit — the main-repo change and the submodule
gitlink together. Because a mismatched pair fails silently rather than loudly,
no partial rollback exists: never revert one side alone.

## Open Questions

- Whether the submodule's `Settings::SetMapDPI` / `mapDPIChange` plumbing (now
  unused by this app, used by the Qt client) should also be dropped from the
  NaviVeylin branch. Deferrable: it changes nothing in the specs, the approach or
  the task breakdown, and dropping it would widen the submodule diff.
