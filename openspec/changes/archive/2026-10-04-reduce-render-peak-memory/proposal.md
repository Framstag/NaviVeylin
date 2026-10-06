# Proposal

## Why

A full native map render still allocates four full-size buffers per frame (TODO.md §49: the C++
`std::vector<uint32_t>`, a Cairo RGB24 surface, the JNI `jintArray`, plus the BGRx→ARGB conversion
loop). At the car overrun size (`2695x1252`, `OSMSCount = 1.2×`) that is roughly 37 MB of *transient*
native memory per render, several times per second while two surfaces render.

Measured 2026-09-27 on a Pixel 8 with a car session live (phone canvas **and** car surface in one
process, `dumpsys meminfo`, DHU session):

| state | PSS | RSS | native heap | Graphics (EGL+GL) | malloced bitmaps |
|---|---|---|---|---|---|
| phone map, browse | 102 MB | 197 MB | 52 MB | 5 MB | - |
| phone map, navigating | 217 MB | 320 MB | 150 MB | 8 MB | 44 MB |
| phone + car surface | **393 MB** | **497 MB** | **194 MB** | - | - |
| same, later (reclaim) | 369 MB | 345 MB | 61 MB (+120 MB swap PSS) | 167 MB | 62 MB |

The device in that state had ~230-330 MB genuinely free with zram swap 99.5 % full
(`3,858,880K` of `3,877,376K`), and the Android Auto host family held ~750 MB RSS
(`:projection` 249 MB, `:car` 197 MB, `:provider`/`:watchdog`/`:shared` ~101 MB each). The processes
lmkd reaps there are the host's, not ours: `com.google.android.projection.gearhead:*` carries
repeated `exit-info reason=3 (LOW_MEMORY)` kills — including twice with this app **not running at
all** — while every recorded exit of our app is `USER REQUESTED` or `PACKAGE UPDATED`. The user's
"Android Auto crashes" symptom is that reaping, plus the host's own `OutOfCarLifecycle` /
transport-socket FATALs when the head-unit link drops.

The app's frame cost is the only term of that we control. This change removes the per-render
allocation and copy, and states the peak a frame and a two-surface process are allowed to reach.

## What Changes

- **A native render that writes into a caller-owned buffer.** A new JNI render entry point renders
  the map into a pixel buffer supplied by the caller (the pooled render target's storage), so the C++
  `std::vector<uint32_t>` and the JNI `jintArray` are no longer allocated per render and the
  `setPixels` copy disappears. The existing entry point (allocating, returning `int[]`) stays, because
  it is the upstream API and other consumers use it — additive, not breaking.
- **Reused scratch storage for the paths that cannot avoid a copy** (nothing in this change should
  make a frame slower): the native client keeps its intermediate buffers across renders and grows them
  on demand instead of allocating and freeing them per frame.
- **A stated pixel layout for the caller's storage.** The two destinations of one render do not share a
  layout: an allocating render returns `int[]` elements (`0xAARRGGBB`, what `Bitmap.setPixels` takes),
  while a direct buffer is consumed by `Bitmap.copyPixelsFromBuffer`, which takes the bitmap's own byte
  order (R,G,B,A on `ARGB_8888`). The first implementation of the buffer path assumed one layout for
  both and swapped red and blue on the device (motorways red, rivers orange — design D1b), so this
  change now also carries the correction, the per-destination contract, and a verification that can see
  a channel swap (the seam test's byte comparison cannot).
- **Both renderers use the new path**: the phone map canvas (`MapRenderer`) and the car map surface
  (`AutoMapRenderer`) — the shared `MapRenderUtil` seam, so the win applies wherever the render
  happens and is largest in the phone+Android Auto process, where both surfaces are live.
- **A stated bound instead of an implicit one:** the per-render transient allocation of a frame at the
  car overrun size, and the peak of a process carrying two live surfaces, become requirements with
  numbers rather than emergent behaviour. The existing pooled-target policy
  (`RenderBitmapPool`: hand out → release, ≤2 free per size class, ≤2 size classes) is unchanged.
- **Not in scope** (named so the change stays honest):
  - the tile-data cache capacity and its **retention** (`NativeTileDataCache.PHONE_TILES`/`CAR_TILES`).
    A later scripted measurement (2026-09-27: 10 zoom-out steps + 14 pan swipes via `adb shell input`)
    shows the native side retaining ~220 MB after a wide-area walk — native heap 115 MB → 337 MB,
    `TOTAL PSS` 377 MB → 600 MB, saturating under continued panning and retaining 324 MB after
    returning to the start viewport, with the app's bitmap cache flat. An earlier reading of
    `TileData<NodeRef|WayRef|AreaRef>` as "single-digit MB per database" was therefore wrong: entry
    data is large at low zoom. That retention, its release under platform memory pressure and the
    capacity policy live in the sibling change `bound-tile-data-retention`; this change stays with the
    per-render transients.
  - the `Graphics` (EGL/GL) footprint of two live surfaces (167 MB measured). That is buffer-queue and
    texture behaviour driven by the display paths, not by the render path this change touches.
  - making an lmkd kill of the car host *visible* from the app (a footprint diagnostic). The triage of
    2026-09-27 was blind because nothing recorded our own footprint while the host died
    (TODO.md §93); that is a separate, diagnostics-shaped follow-up.

## Capabilities

### New Capabilities
- none.

### Modified Capabilities

- `render-performance`: the render path no longer allocates a pixel buffer per frame. New requirements
  state that a native render writes into caller-owned storage, that the transient allocation per frame
  at the car overrun size is bounded, and that the peak of a process with two live map surfaces is
  bounded and observable (the pooled-target requirement stays as it is).
- `osmscout-jni`: a render entry point that renders into a caller-supplied pixel buffer, with the
  buffer's ownership and locking contract (who owns the storage, what may happen to it concurrently,
  what the bridge guarantees on failure), alongside the existing allocating entry point.

## Impact

Affected files and modules:

- `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` — new JNI render entry
  point + retained intermediate buffers (submodule, branch `naviveylin-local`; **submodule patch,
  minimal and upstreamable**, plus a gitlink bump in the main repo).
- `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java` — the Java
  side of the new native declaration (**:osmscout-client-java override**, per `AGENTS.md`: the C++ side
  lives in the submodule, the Java declaration in the override module — patch in one, never both).
- `core/src/main/java/com/naviveylin/core/MapRenderUtil.kt` — the shared render seam (`renderInto` /
  `renderToBitmap`) gains the buffer-taking path.
- `core/src/main/java/com/naviveylin/core/RenderBufferPool.kt` — the pooled direct-buffer storage, whose
  contract states the storage's format (corrected 2026-09-29: the consumer's byte order, not the
  allocating path's `0xAARRGGBB` words — design D1b).
- `core/src/main/java/com/naviveylin/core/RenderBitmapPool.kt` — only if the hand-off needs the pool's
  storage exposed by a different accessor; the pool's contract itself does not change.
- `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` — phone canvas path.
- `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` — car surface path (overrun render + blit).
- Tests: `buildSrc`-adjacent native JNI parity gate (`scripts/check-jni-signatures.sh` in the
  submodule) and the existing renderer test suites (`MapRendererSmokeTest`, `AutoMapRendererPooledTargetTest`,
  `RenderBitmapPoolTest`), plus a new test for the transient-allocation bound.
- Build: `:osmscout-client-java` (the JAR the override compiles into), CMake target
  `osmscout_client_java`, and both app flavors.

Guidelines affected and to be updated in the same change:

- `guidelines/MapRendering.md` §1/§2 (render pipeline + bitmap lifecycle) and §14 (the car renderer's
  overrun buffer), because the buffer the native render writes into becomes caller-owned.
- `guidelines/Design.md` §5 (native boundary) if the entry point's ownership rule belongs there.

Change class: **additive** (a new entry point; the existing one is untouched). Rollback path: revert
the gitlink bump and the override/JAR change in one commit — no persisted state, no migration, no
user-visible behaviour to unwind.

Scope: the shared JNI render path, so **both** phone and Android Auto benefit; the measured case that
motivates it is the phone + Android Auto projection process (two live surfaces).

Previous specifications changed: `render-performance`, `osmscout-jni` (both listed above). Related but
untouched: `native-tile-data-cache` (see "Not in scope"), `auto-map-renderer` (the car renderer's
existing buffer-lifetime requirements already cover stop/replace behaviour).
