# Design

## Context and measured baseline

The change targets the per-render pixel buffers of the shared JNI render path
(`libosmscout-client-java/src/OSMScoutClient.cpp`, the target of `osmscout_client_java`), reached from
both surfaces through `core/MapRenderUtil.kt` (`renderInto` / `renderToBitmap`):

- phone map canvas — `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` (TILES composition and
  DIRECT full render) with targets from `core/RenderBitmapPool.kt`;
- car map surface — `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` (overrun frame, 1.2×,
  kept as `overrunBitmap` for sub-region blits).

Baseline measured 2026-09-27 (Pixel 8, phone canvas + DHU car surface in one process, `dumpsys
meminfo`): PSS 393 MB / RSS 497 MB / native heap peak 194 MB with `Graphics` 167 MB and malloced
bitmaps 62 MB; phone-only navigation was PSS 217 MB / native 150 MB. At the car overrun size
(`2695x1252`) the four per-render buffers §49 names are ~37 MB of transient native memory per render.

## D1 — How the frame's pixel storage reaches the native renderer

| | A: pass the target `Bitmap`, lock its pixels in native | B: pass a reused direct `ByteBuffer` | C: keep `jintArray`, reuse the native buffers |
|---|---|---|---|
| Removes per render | C++ vector, `jintArray`, conversion, the `setPixels` copy | C++ vector, `jintArray`, conversion | C++ vector, `jintArray` allocations only |
| Copies left per frame | 0 | 1 (`copyPixelsFromBuffer`) | 1 (+ per-pixel conversion) |
| Submodule stays platform-neutral | **no** — needs `<android/bitmap.h>` / `AndroidBitmap_*` in upstream code | yes (`GetDirectBufferAddress`) | yes |
| Risk | bitmap config/stride coupling, lock/unlock on every path, upstream-unfriendly | moderate: buffer↔bitmap contract, stride/format | low, but leaves the largest term (the conversion) in place |

**Chosen: B.** It removes the C++ `std::vector<uint32_t>` and the `jintArray`, keeps one unavoidable
copy into the `Bitmap`, and keeps the submodule free of Android headers (its `Android/` directory is
the only place Android-specific code is allowed, and this file is not in it). A is rejected on
`AGENTS.md`'s upstreamability rule and because it couples the bridge to `Bitmap`-specific locking; C
is rejected because it leaves the conversion, which is the allocation-heavy part.

**Corrected 2026-09-29 — two premises of this table were wrong, and the second one shipped a defect
that reached the device. See D1b.**

1. "Removes the BGRx→ARGB per-pixel loop" is not true and cannot be: the loop is still required in
the implemented body (`OSMScoutClient.cpp`, the per-pixel write into `outPixels`), because a Cairo
RGB24 surface holds B,G,R,X. What B actually removed is the `jintArray` and the allocation behind it.
2. "Buffer format/stride mismatch" being a single contract for both destinations is false. The
`int[]` destination is consumed by `Bitmap.setPixels`, which takes `0xAARRGGBB` ints; the direct
buffer is consumed by `Bitmap.copyPixelsFromBuffer`, which takes the bitmap's own byte layout. The
same 32-bit value is therefore correct in one destination and a channel swap in the other.

`ByteBuffer.allocateDirect` storage is pooled app-side next to the render targets (one per size class
actually used, bounded like `RenderBitmapPool`), so nothing frame-sized is allocated per render.

## D1b — The caller's storage has the consumer's byte order (defect, 2026-09-29)

**What shipped:** `MapRenderUtil.renderInto` writes the native frame into a pooled direct `ByteBuffer`
and hands that buffer to `Bitmap.copyPixelsFromBuffer`. The native body writes one `uint32_t` per
pixel, `0xFF000000 | r<<16 | g<<8 | b` — the layout `setPixels(int[])` reads. The regression landed
with this change (main repo commit `33f11ce`, 2026-09-27 20:24, submodule `2409cf306`); before it,
`renderInto` went through `renderPixels` → `int[]` → `target.setPixels(...)` and was correct.

**Why it is wrong:** an `ARGB_8888` bitmap's memory layout is **R,G,B,A** bytes, not an `0xAARRGGBB`
word (the NDK names that format `ANDROID_BITMAP_FORMAT_RGBA_8888`, and Skia's `kN32_SkColorType` is
`kRGBA_8888`; Google issue 36980284 is titled "Bitmap.copyPixelsFromBuffer expects RGBA format, when
created with Bitmap.Config.ARGB_8888"). A little-endian write of `0xAARRGGBB` produces bytes
B,G,R,A, so red and blue swap.

**Blast radius:** every frame of the buffer path — the car surface
(`AutoMapRenderer` → `MapRenderUtil.renderInto`) and every phone full render (`MapRenderer`, DIRECT
mode and the tile path's rotation/gesture-end fallback). The phone tile path
(`renderTilePixels` → `renderWithRouteAndPois` → `Bitmap.createBitmap(int[])`) is unaffected, so the
palette flipped between tile frames and full-render frames.

**Observed (Pixel 8, `standard.oss` daylight palette), exactly the swap:**

| object | stylesheet colour | on screen |
|---|---|---|
| water / rivers | `#9acffd` light blue | `#fdcf9a` orange |
| motorway | `#7d7af5` blue-violet | `#f57a7d` red |
| primary road | `#f58b8b` red | `#8b8bf5` blue |

**Why no test caught it:** the seam test's fake writes the frame with
`pixels.asIntBuffer().put(intArray)` and the assertion reads it back through a Robolectric bitmap —
int round-trip, no byte order involved. A device-order assertion in a Robolectric test is not
possible anyway: Robolectric's own `copyPixelsToBuffer` channel order differs by SDK (SDK 33 correct,
SDK 36 swapped) while a real device is correct (robolectric#10700). The `order(ByteOrder.nativeOrder())`
call in the pool is a no-op for `copyPixelsFromBuffer` (it copies bytes); it only made the wrong
contract look deliberate.

**Chosen fix: write the destination's layout in the shared body.** The render body stays shared and
the destination becomes the only difference: an `int[]`/`GetIntArrayRegion` destination keeps
`0xAARRGGBB`, a direct-buffer destination writes the four bytes R,G,B,A (little-endian word
`0xAABBGGRR`). No added copy, no allocation, no per-destination render, so the "one body" guarantee
survives. Rejected: swizzling per frame on the Java side (reintroduces the loop this change's premise
was built on), and reading the buffer back through an `int[]` + `setPixels` (reintroduces the
frame-sized array and the copy). A one-time self-probe of the device's bitmap order (1x1
`eraseColor` → `copyPixelsToBuffer` → read byte 0) and passing the order to the entry point is the
fallback if the layout is ever not the platform constant; it is not the default, because it adds an
API parameter for a value that is fixed on Android.

**Documentation that is part of the defect** (both currently state the wrong layout, and are what
made the swap look intentional): the `renderInto` declaration's format paragraph in the
`:osmscout-client-java` override, and `RenderBufferPool`'s contract section.

## D2 — Ownership and concurrency of that storage

Two surfaces render concurrently on different threads (`Dispatchers.Default`), so scratch storage
inside the native client would need a lock:

| | A: one client-wide scratch buffer + mutex | B: a small pool of scratch buffers in native | C: caller-owned storage, no native scratch |
|---|---|---|---|
| Thread safety | serialized (a lock around every render) | correct, more state to size/evict | correct by construction |
| Cost | serializes the two surfaces — a full car render is 25-300 ms | needs the same bound policy again in C++ | none |
| Failure mode | a slow car render blocks the phone frame | leaked or mis-sized scratch | none: two callers simply use two buffers |

**Chosen: C.** The caller supplies the storage; the native side writes only for the duration of the
call and retains no reference. That is the contract the `osmscout-jni` delta states, and it makes the
"never hand the same storage to two renders" rule the caller's responsibility — which the app already
satisfies, because a frame that is displayed is an independent copy (spec `render-performance` — A
frame handed to the display layer is never overwritten) and the pool never hands out a target twice.

## D3 — Compatibility and failure semantics

The allocating entry point stays unchanged (upstream API, used by consumers outside this app). The new
entry point reports success/failure; a failure leaves the caller's storage valid and unreported as a
frame. The bridge must not fault: the JNI method follows the file's existing shape (null/invalid
handle checks, no exception escape) and is covered by the submodule's
`scripts/check-jni-signatures.sh` gate, which today already flags a caller compiled against a stale
declaration (the failure that was hit on 2026-09-27).

## D3b — The phone canvas' own frame and animation buffers

**CORRECTED 2026-09-27, during implementation — this table overcounted, and two tasks built on it
were nearly busywork.** The measured gesture step is real (`GL mtrack` 80.6 MB → 130.6 MB after three
zoom gestures, still 130.6 MB 25 s later), but the inventory below attributed it to buffers that are
either not bitmaps or not duplicates:

- `emittedFrame` is *bookkeeping* — a data class of `width`/`height`/`viewport`, **no pixels**;
- `lastEmittedFrame` was **the** emitted bitmap, i.e. the very object `_frameFlow` handed to Compose
  (the state holds it too), so "a second frame-sized bitmap for reuse detection" did not exist:
  removing the field (task 3.4) deletes a field, not a buffer — it does not save 14.9 MB;
- `prevRenderedBitmap` is not a copy either: it is assigned `ui.renderedBitmap`, the frame the UI state
  already holds;
- `crossfadeBitmap` **is** a real copy — created when a transition starts and **already released when
  the transition completes** (and replaced, not stacked, when one supersedes another), so it exists for
  ~200 ms per gesture, not for the session.

What the change therefore does for the canvas half: `lastEmittedFrame` is gone (reuse is decided from
`frontBufferSeq`/`lastEmittedSeq` plus the frame flow's own bitmap), the transition release is
single-sourced in one `endCrossfade()`, and the supersession rule is documented at its site. The
retained `GL mtrack` step is a driver/high-water effect of the per-transition copy and of the
frame-sized bitmaps a gesture legitimately creates — **not** something these two tasks can remove; the
next lever for it is the display path's buffer queues, tracked in TODO §49 and §65, not here.

Measured 2026-09-27 on the phone with a car session live: `GL mtrack` 80.6 MB settled, **130.6 MB after
three zoom gestures, and still 130.6 MB 25 s later** — the added allocation is never given back inside
the process. Inventory of frame-sized storage on the phone display path (`MapRenderer` +
`MapCanvasScreen`):

```
backBuffer          1080x2400  10.4 MB   tile composition target
frontBuffer         1080x2400  10.4 MB   display source
emittedFrame        1296x2880  14.9 MB   overrun-sized copy -> what Compose draws
lastEmittedFrame    1296x2880  14.9 MB   held only for "unchanged frame" reuse
prevRenderedBitmap  ImageBitmap ~14.9 MB crossfade source
crossfadeBitmap     ImageBitmap ~14.9 MB crossfade target
```

| | A: keep both extra buffers | B: drop the duplicate reuse buffer and release the crossfade references | C: emit a screen-sized crop instead of the overrun frame |
|---|---|---|---|
| Saves | - | ~15 MB held + the ~50 MB gesture high-water step | ~4.5 MB per frame |
| Risk | the high-water mark stays | the reuse test must keep working from sequence/epoch bookkeeping (the fields already exist) | **rejected by `guidelines/MapRendering.md` §1**: the follow scroll and the pan window need the overrun margin in the emitted frame |

**Chosen: B.** `lastEmittedFrame` exists only to answer "is this frame unchanged?", a question the
`frontBufferSeq`/`lastEmittedSeq` fields already answer; and the crossfade `ImageBitmap`s are
composition state that is abandoned when the transition ends, so holding them is a leak of an uploaded
texture rather than a feature. C is rejected explicitly because MapRendering.md forbids reintroducing
the screen-sized crop in the UI.

## Threading and lifecycle (guidelines/Design.md §4)

- Native renders keep running on `Dispatchers.Default`; nothing new touches the main thread.
- The direct buffer is owned by the render path that uses it and is used only inside one JNI call. No
  new scope, no new lifetime: the buffers live in the same pool/lifetime domain as the render targets
  they back (released by the surface whose frame it is, exactly as `RenderBitmapPool.release` is
  called today).
- Both surfaces' stop paths keep releasing their frame storage (car: `clearOverrunBuffer` on stop and
  detach; phone: pool release on frame drop), so a stopped renderer still holds no frame buffer (spec
  `auto-map-renderer` — A stopped renderer holds no surface or frame buffer).

## Verification

**As implemented (2026-09-27):**

- The two entry points are **one shared native body** (`renderMapIntoPixels`), differing only in the
  destination pointer, so "the buffer path renders the same pixels as the allocating path" (task 1.2)
  holds by construction rather than by two implementations agreeing; `MapRenderUtilTest` additionally
  compares the two paths' frames byte for byte through the fake.
- The submodule's `scripts/check-jni-signatures.sh` passes: **55 native declarations checked, matches**,
  with `renderInto` present in the implementation. It lists `renderInto` among its "JNI function without
  a Java declaration" warnings because it reads the submodule's *not compiled* Java copy — the
  declaration lives in the app-side override (like `reloadBasemap`), which is the documented split.
- The three test fakes (`FakeOSMScoutClient`, `FakeMapScreenClient`, `FakeAutoRenderClient`) implement
  the new entry point. The compiler does **not** force this (a new native method compiles fine and only
  fails at the first call), so the fakes are a deliberate part of the change — and the app fake mirrors
  the old seam's routing (plain recorder for an overlay-free request) so the renderer suites' request
  assertions keep their meaning.
- Revert-check (host): routing `MapRenderUtil.renderInto` back through the allocating path makes four
  tests fail and restoring them makes them pass again.
- `:core` 388/0/0 · `:auto` 699/0/0 · `:app` mobile 1318/0/0 · `:app` automotive 1318/0/0, both flavors
  assembled with 0 warnings, all three ABIs carrying a freshly linked `.so` with the new symbol.
- **Open:** the on-device measurements (5.3-5.7) are blocked by the missing sideload path (TODO §95).
  The submodule commit + gitlink bump landed 2026-09-27 (submodule `2409cf306`, main repo `33f11ce`).

### Emulator A/B 2026-09-27 (the honest measurement this change needed — task 5.2/5.6)

The phone's recorded baseline could not be used (its UI state is undocumented, and the buffer path cannot be
compared against it). So the comparison was made **in one place, with one script, on one device**, by reverting
`MapRenderUtil.renderInto` to the allocating route, rebuilding, and re-running the identical script — the AAOS
AVD (`emulator-5554`, x86_64, software GPU, automotive debug build), where a debug APK *can* be installed over
the previous one, so the app data (the installed Nordrhein-Westfalen region) survives the swap.

Script (identical in both runs): force-stop → `CarAppActivity` → the documented `geo:` deep link to start
navigation → wait → **30 injected position fixes at 3 s spacing** (`emu geo fix`, a moving position) → sample.
The car map surface is the changed path (`AutoMapRenderer` → `MapRenderUtil.renderInto`), and its native renders
are countable in logcat (`Diag/MAP: render … -> bitmap 1296x720`).

| run | path | native heap before → after | per render | MAP renders |
|---|---|---|---|---|
| 1 | **buffer path** (this change) | 120.9 → 127.3 MB | **+0.64 MB** | 10 |
| 2 | allocating path (revert) | 103.6 → 137.1 MB | **+4.8 MB** | 7 |

The allocating path retains **~7.5x more native heap per render** than the pooled buffer path. At 1296x720 that
is 3.7 MB per frame-size buffer and ~11 MB of churn per render on the allocating path (the C++ vector, the
`jintArray`, the Java `int[]`), of which the allocator keeps a few MB — exactly the mechanism TODO §49 describes;
at the phone's own overrun size (2695x1252, 13.5 MB per buffer, ~40 MB of churn per render) the same shape
applies. **Task 5.6's decision: the change is justified** — the native heap does move, in the predicted
direction, and the pooled storage's own retention is bounded and constant (≤2 buffers per size class).

Stated limits: two runs, one emulator, small render counts (10 vs 7, because the emulator's software-GPU frame
time bounds the render cadence) and different baseline allocations — which is why the comparison is the **delta
per render**, not an absolute. It is a directional result that matches the mechanism, not a coefficient.


### Phone device run 2026-09-27 (Pixel 8, Play build `2026-09-27-4` / versionCode 90, freshly booted app per sample)

The installed APK contains the new symbol (`libosmscout_client_java.so`, `OSMScoutClient_renderInto`
verified by pulling `split_config.arm64_v8a.apk`), so these are numbers of the new path, not of the old
library.

| sample (fresh process each) | native heap | `Graphics` | `TOTAL PSS` | malloced bitmaps | renders |
|---|---|---|---|---|---|
| browse, idle | 139 MB | 98 MB | 306 MB | 44 MB | 0 |
| phone-only navigation | 222 MB | 150 MB | 479 MB | 65 MB | 6 |
| walk start (10 zoom-outs + 14 pans) | 139 MB | 99 MB | 318 MB | 44 MB | — |
| after the walk (ceiling) | 244 MB | 198 MB | 511 MB | 88 MB | 43 |
| back at the start viewport (retained) | 227 MB | 198 MB | 497 MB | 88 MB | 43 |

- The walk **ceiling** is 244 MB native / 511 MB PSS against the recorded baseline of **337 MB / 600 MB**
  (−93 MB / −89 MB), and it retains 227 MB at the start viewport against a recorded 324 MB. Two changes
  shipped in one commit, so this is their **combined** effect and cannot be split without reverting one;
  the buffer path's own share is what the host revert-check and the next section cover.
- The `Graphics` figure does not move (98 → 198 MB across the walk, and the EGL share is flat at 52 MB),
  as this change predicted: it does not touch the display path's buffer queues.
- **Negative result for the canvas half (task 5.7):** on a fresh process, `GL mtrack` went 46.4 MB idle →
  **132.3 MB after three zoom in/out pairs, still 132.4 MB 30 s later** — a retained step of ~86 MB for
  six gestures (~14 MB each) against the recorded ~50 MB for three gestures (~17 MB each). Per gesture the
  step is **no smaller**, i.e. the tasks 3.4/3.5 assumptions were indeed not the lever (see the design
  D3b correction). `Bitmap (malloced)` *does* come back (44 → 117 MB across the gestures → 58 MB 30 s
  later), so the transition copies are released; the GL high-water is the driver's, not a held reference.

Unit:

- `MapRenderUtil` seam test with a fake client: a sequence of same-size renders performs **no**
  frame-sized allocation after the first (the pool's `allocatedCount` + a buffer-allocation counter),
  and the rendered result is byte-identical to the allocating path for the same viewport.
- Renderer suites must stay green: `RenderBitmapPoolTest`, `MapRendererSmokeTest`,
  `AutoMapRendererPooledTargetTest`, `MapRendererBlitTest`, `MapCanvasPanWindowTest`.
- Submodule: `scripts/check-jni-signatures.sh` (JNI parity) plus the JNI-side unit/test target the
  submodule already runs.

On-device (recipe: `guidelines/Build.md` §10; measurement: this change's baseline section):

- phone + car surface in one process (DHU), navigation active: `dumpsys meminfo <pkg>` native heap and
  `Graphics` before/after, with the `MAP` render count quoted so the comparison is per render.
- phone-only navigation for the second data point.
- The car surface checks of §10 (`lock OK`, `surface created`, `releasing session surface`, no
  `lockCanvas failed`, no `dropping frame` growth).
- Expected direction: native heap peak lower than the 194 MB baseline with the same render count; the
  `Graphics` figure (167 MB) is **not** expected to move, because this change does not touch the
  display paths' buffer queues (see the proposal's "Not in scope").

## Risks

| Risk | Mitigation |
|---|---|
| Buffer format/stride mismatch shows as a colour-shifted or torn frame | **this mitigation failed — see D1b**: the seam test compares the two paths through a fake and a Robolectric bitmap, both of which round-trip ints, so a channel-order mismatch is invisible to it. The format contract is now stated per destination (spec `osmscout-jni` — The buffer's layout is the consumer's), and the check that can see a swap is a device/instrumented colour check or a native-side byte assertion, not a Robolectric pixel comparison |
| The direct buffer is retained accidentally (a leak that never appears in tests) | ownership stated in the spec delta; pool-bounded, counted, and asserted by an allocation-count test |
| A stale Java declaration compiles but calls the wrong native signature | submodule `check-jni-signatures.sh` gate plus the main repo's JNI-parity test (`jnisync-java-scout` spec) |
| The gain is smaller than expected, and the change is pure churn | the on-device measurement is an explicit task with the baseline numbers; if the native heap does not move, the change is reverted (additive, no persisted state) and the next lever (the `Graphics` footprint, or the host side) is taken up separately |
