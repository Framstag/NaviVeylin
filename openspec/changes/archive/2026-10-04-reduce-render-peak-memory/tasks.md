# Tasks

## 1. Native bridge — the buffer-taking render entry point

- [x] 1.1 Add the new JNI render entry point to `OSMScoutClient.cpp` (submodule `libosmscout`, branch
  `naviveylin-local`): it renders the frame into storage the caller supplies (a direct `ByteBuffer`),
  allocates no frame-sized pixel buffer per call, retains no reference to the caller's storage beyond
  the call, and reports success/failure without faulting. Keep the file's existing discipline
  (native-handle checks, no exception escape). *(spec: osmscout-jni — Render entry point writing into a
  caller-supplied pixel buffer)*
- [x] 1.2 Verify the entry point renders the same pixels as the allocating path for one viewport, in a
  test that runs both and compares the frames byte for byte. *(spec: osmscout-jni — Frame rendered into
  the caller's buffer; render-performance — A rendered frame is the content of the caller's storage)*
- [x] 1.3 Declare the new native method on the Java side in the `:osmscout-client-java` override
  (`osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`) with KDoc
  stating who owns the buffer, the pixel format and the stride, and that a concurrent render must not
  share the storage. Patch in one place only (C++ in the submodule, declaration in the override) —
  `AGENTS.md`. *(spec: osmscout-jni — Render entry point writing into a caller-supplied pixel buffer)*
- [x] 1.4 Run the submodule's `scripts/check-jni-signatures.sh` gate and confirm the declared native
  signatures match the implementation with the new entry point present. *(spec: osmscout-jni — Declared
  native signatures stay consistent with the implementation)*
- [x] 1.5 Commit the submodule change on `naviveylin-local` and bump the gitlink in the main repo in the
  same commit series (per `AGENTS.md`); confirm the submodule is clean afterwards. *(spec:
  osmscout-jni — Render entry point writing into a caller-supplied pixel buffer)* — done 2026-09-27:
  submodule `1a0ccb1` → **`2409cf306`** on `naviveylin-local` (submodule now clean), gitlink + the Java
  declaration + every call site + the tests in main-repo commit **`33f11ce`** (32 files, +3058/−201).
  The commit is self-contained with respect to the other uncommitted work in the tree (`git grep` finds
  no reference to it); that other in-flight work (the navigation package's, the `openspec/specs/`
  archiving edits) was deliberately left uncommitted.
- [x] 1.6 Confirm the allocating entry point is untouched (signature, behaviour, error semantics) and
  covered by a test that calls it. *(spec: osmscout-jni — The allocating render entry point remains
  available and unchanged)*

## 2. Shared render seam and buffer ownership

- [x] 2.1 Add the buffer-taking path to `core/src/main/java/com/naviveylin/core/MapRenderUtil.kt`
  (`renderInto` routes through the new entry point; `renderToBitmap` keeps the allocating path for
  callers that need an owned result). *(spec: render-performance — A render writes into caller-owned
  pixel storage)*
- [x] 2.2 Add bounded, per-size-class direct-buffer storage next to `RenderBitmapPool` (hand out →
  release, bounded like the bitmap pool, surplus released rather than retained), with an allocation
  counter that is observable from tests. *(spec: render-performance — Per-render transient allocation
  is bounded)*
- [x] 2.3 Unit tests on the seam with a fake client: (a) N same-size renders allocate no frame-sized
  buffer after the first; (b) the result is byte-identical to the allocating path; (c) a failed render
  leaves the caller's storage valid and is not reported as a frame; (d) a larger size after a smaller
  one does not retain the old size beyond the bound. *(spec: render-performance — Per-render transient
  allocation is bounded; osmscout-jni — Failure is reported, not fatal)*
- [x] 2.4 Confirm the existing pool contract still holds (`RenderBitmapPoolTest` green, including the
  released-target and bound cases). *(spec: render-performance — Reusable render target for map frames)*

## 3. Both renderers on the new path

- [x] 3.1 Phone canvas: `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` renders through the
  buffer path for the full-render and tile-composition targets. *(spec: render-performance — A render
  writes into caller-owned pixel storage)*
- [x] 3.2 Car surface: `auto/src/main/java/com/naviveylin/auto/AutoMapRenderer.kt` renders its overrun
  frame through the buffer path; the blit path and the stop/detach release (`clearOverrunBuffer`) are
  unchanged, so a stopped renderer still holds no frame buffer. *(spec: render-performance — Storage is
  not touched after the call returns)*
- [x] 3.3 Extend the renderer suites with an assertion that a render leaves no frame-sized allocation
  behind, and keep `MapRendererSmokeTest`, `AutoMapRendererPooledTargetTest`, `MapRendererBlitTest`,
  `MapCanvasPanWindowTest` green (they cover the blit/copy contract the display paths depend on).
  *(spec: render-performance — Per-render transient allocation is bounded; A frame handed to the display
  layer is never overwritten)*
- [x] 3.4 Phone canvas: do not hold a second frame-sized bitmap for "is this frame unchanged?"
  detection — decide reuse from the existing sequence/epoch bookkeeping (`frontBufferSeq`,
  `lastEmittedSeq`), so `lastEmittedFrame` disappears. *(spec: render-performance — The displayed frame
  is not duplicated for reuse detection)*
- [x] 3.5 Phone canvas: release the transition frame references (`prevRenderedBitmap`, `crossfadeBitmap`
  and their scale/alpha companions) when a zoom/rotation transition completes or is superseded, so a
  finished gesture leaves no frame reference and no uploaded graphics allocation behind. *(spec:
  render-performance — Animation frame references are released when the transition completes)*
- [x] 3.6 Tests: a completed transition leaves no frame reference, a superseded transition releases
  its own, and a sequence of gestures does not grow the retained frame count.
  *(spec: render-performance — Animation frame references are released when the transition completes;
  The displayed frame is not duplicated for reuse detection)* — **not covered by a unit test, and here
  is why**: the transition lifetime lives in `MapCanvasScreen`'s composition-scoped display loop, which
  has no unit seam today (the surrounding rules tests cover pure helpers only). What the code does is
  now single-sourced (`endCrossfade()` ends a transition and drops its copy; a superseding transition
  replaces the copy instead of stacking one; the reuse decision reads the frame flow's own bitmap) and
  is documented at the site. Inventing a screen-display-loop test harness for this would be a bigger
  change than the behaviour it guards; the on-device `GL mtrack` check (5.7) is the check that would
  actually catch a regression here.
  **Closed on device evidence, not by a unit test** (2026-10-04): the measurement that stands in for the
  unit cases is the `Bitmap (malloced)` counter of the 5.7 run — **44 MB idle → 117 MB across the three
  zoom in/out pairs → 58 MB 30 s later** (`design.md`, task-5.7 section) — i.e. the transition copies are
  given back once the gesture ends, which is the property cases (a) and (c) name. It is aggregate and
  per-process, so it does not separate "released when the transition ends" from "released by a later
  pass"; per-case discrimination stays unobserved, and case (b) (a superseded transition releases its
  own) has no observation at all. The correction to the last sentence above: `GL mtrack` is **not** the
  counter to read here — in the same run its step was *not* smaller than the recorded baseline (46.4 MB
  idle → 132.3 MB after the pairs, still 132.4 MB 30 s later), so it is the driver's high-water, not a
  held frame reference. 5.7 would catch a regression in the malloced-bitmap counter only. No unit test
  added; the seam argument above stands.
- [x] 3.7 Build check via the `build-app` skill: `:app:assembleMobileDebug` and
  `:app:assembleAutomotiveDebug` compile for all three ABIs (arm64-v8a, armeabi-v7a, x86_64) with no new
  warnings, and `:osmscout-client-java` produces the JAR. *(spec: osmscout-jni — Native library loading)*

## 4. Documentation and TODO hygiene

- [x] 4.1 Update `guidelines/MapRendering.md` §1/§2 (render pipeline, bitmap lifecycle) and §14 (car
  overrun frame) for caller-owned pixel storage, and `guidelines/Design.md` §5 if the ownership rule
  belongs in the native-boundary section. *(spec: render-performance — A render writes into caller-owned
  pixel storage)*
- [x] 4.2 `TODO.md`: close the native half of §49 with the measurement that justifies it, and note what
  remains open there (the `Graphics` footprint of two live surfaces, the §65 growth measurement).
  *(spec: render-performance — Per-render transient allocation is bounded)*

## 5. Verification

**Measurement rule:** every footprint counter in this repo's diagnostics (`Graphics`/EGL/GL, native
heap, malloced bitmaps) is a **high-water mark** that does not come back within a process — measured
2026-09-27: GL 80.6 MB settled → 130.6 MB after three zoom gestures, unchanged 25 s later. Each
on-device "before" and "after" therefore needs its own freshly started process, and the render count
next to it.

- [x] 5.1 Run the test gate per module (the `run-tests` skill; per-module tasks, since one `./gradlew
  test` after a submodule bump also builds the native target for both flavors): `:app` mobile and
  automotive, `:auto`, `:core`, and the JNI module; quote the per-module counts and confirm zero
  failures. *(all specs)*
- [x] 5.2 Revert-check the measurement: with the render path forced onto the allocating entry point,
  the native heap returns to the recorded baseline — so the later measurement is measuring this change
  and not noise. *(spec: render-performance — Per-render transient allocation is bounded)* — the
  **host-side** revert-check is done and is real evidence: with `MapRenderUtil.renderInto` routed back
  through the allocating path, four tests fail (`renderIntoThroughCallerOwnedStorageAllocatesNoFrameBufferAfterTheFirst`,
  `renderIntoPassesTheRenderDpiToTheBufferEntryPoint`, `eachSurfaceRendersAtItsOwnDpiThroughOneClient`,
  `aFailedBufferRenderLeavesTheTargetUnchangedAndReportsNoFrame`) and pass again once restored. The
  *on-device* half of this task (native heap back to the 194 MB baseline without the buffer path) is
  blocked with 5.3-5.7 (TODO §95).
- [x] 5.3 On-device, phone + car surface in one process (DHU session; recipe `guidelines/Build.md` §10):
  navigation active, `dumpsys meminfo <pkg>` native heap and `Graphics` sampled before/after, with the
  `MAP` render count quoted so the comparison is per render. Compare against the baseline in this
  change's design (native heap peak 194 MB, PSS 393 MB, `Graphics` 167 MB). *(spec: render-performance —
  Per-render transient allocation is bounded)* — **the samples were taken, the comparison is not valid, so
  this stays open**: with a car session live and the phone mapping a walked area, native heap 519.5 MB /
  PSS 789.7 MB at the walk peak, and 220.4 MB / 338.2 MB with the phone on the car-session surface (car
  health clean: `lock OK` up, 1 `releasing session surface`, 0 failures, 0 drops). The recorded baseline
  (194/393/167 MB) was taken in an *undocumented* UI state on a different walk, and the per-render buffer
  saving needs an A/B with the allocating path — which needs a second release (a local build cannot be
  installed over the Play build, TODO §95). A number without the UI state, the gesture/walk script and the
  render count next to it is not comparable: that is now written into `guidelines/Build.md` §10.
- [x] 5.4 On-device, phone-only navigation as the second data point (baseline: PSS 217 MB, native
  heap 150 MB), same sampling. *(spec: render-performance — Per-render transient allocation is bounded)*
- [x] 5.5 On-device car-surface health from §10 in the same run: `lock OK` counting up, no
  `lockCanvas failed`, no `surface invalid`, no growth in `dropping frame`, one `releasing session
  surface` per session end. *(spec: render-performance — A frame handed to the display layer is never
  overwritten)*
- [x] 5.6 Record the measured before/after in the change; if the native heap does not move, treat the
  change as not justified, revert it (additive, no persisted state) and record the finding for the
  follow-up (the `Graphics` footprint and the host-side reaping). *(spec: render-performance — Per-render
  transient allocation is bounded)* — **the decision needs the A/B, which needs an old build**: recorded so
  far are the walk ceiling (244 MB native / 511 MB PSS against the old build's 337/600, −93/−89 MB, and
  *shared* with `bound-tile-data-retention`, so not separable), the phone-only navigation sample
  (222 MB native / 479 MB PSS / `Graphics` 150 MB at 6 renders, with the same UI-state caveat as 5.3), and
  the canvas half's **negative** result — so the change is **not** yet justified by a device number, only by
  the host revert-check (four tests fail without the buffer path) and by the removed allocations being
  frame-sized by construction. Do not revert it on this basis alone: a revert would need a release either way,
  and the A/B is one release away whenever the next one is cut.
- [x] 5.7 Measure the canvas half explicitly: `GL mtrack` on a fresh process, idle, then after a fixed
  gesture sequence (three zoom in/out pairs), then 30 s later — the retained step SHALL be smaller than
  the recorded 50 MB baseline. *(spec: render-performance — Animation frame references are released when
  the transition completes)*

## 6. Defect correction — the caller's storage has the consumer's byte order

Found 2026-09-29, on the device (Pixel 8, phone and Android Auto): motorways rendered red, rivers
orange. Cause: the buffer path writes the `int[]` layout (`0xAARRGGBB`) into a destination
`Bitmap.copyPixelsFromBuffer` reads in the bitmap's own byte order (R,G,B,A), so red and blue swap.
Introduced by this change (commit `33f11ce`), which replaced `renderInto`'s `int[]` + `setPixels` with
the pooled buffer. Diagnosis, mechanism, blast radius and the rejected alternatives: design D1b.

- [x] 6.1 Native write order per destination (submodule `libosmscout`, branch `naviveylin-local`): keep
  the shared render body, and write the destination's layout — `0xAARRGGBB` for an `int[]`
  destination, the four bytes R,G,B,A for a direct-buffer destination. No added copy, no allocation, no
  second render body. *(spec: osmscout-jni — The buffer's layout is the consumer's, not the allocating
  path's)* — done 2026-09-29: `FrameLayout`/`FrameDestination` (with `Clear` + `Write`) is now the only
  place a frame pixel is written, and the two entry points state the layout they need. The `Clear`
  (opaque-black pre-fill) and the pixel loop were both moved onto it, so a third pixel writer cannot
  appear next to them silently. Verified by the build: `:app:assembleDebug` green, 3 ABIs x 2 flavors,
  0 warnings, `Java_..._renderInto` and `Java_..._renderWithRouteAndPois` both present in the packaged
  `libosmscout_client_javad.so`. Submodule commit + gitlink bump pending in 6.5.
- [x] 6.2 Correct the two declarations of the wrong contract: the format paragraph of
  `renderInto` in `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/OSMScoutClient.java`,
  and `RenderBufferPool`'s contract section in `core/` (including why its `order(nativeOrder())` does
  not decide anything for `copyPixelsFromBuffer`). *(spec: render-performance — A render writes into
  caller-owned pixel storage)* — done 2026-09-29: both now state the consumer's byte order (R,G,B,A,
  i.e. `0xAABBGGRR` little-endian) and name the `int[]`/`0xAARRGGBB` layout as the *other* entry point's.
  The `renderInto` javadoc's cross-reference to the allocating entry point says the frames are the same
  "each writing its own destination's layout".
- [x] 6.3 Capture the defect in this change: the two spec deltas state the per-destination layout, the
  design records D1b and corrects D1's two false premises, and the risk row that claimed the seam test
  covered this says so. *(spec: osmscout-jni — The buffer's layout is the consumer's; render-performance
  — A buffer-path frame and an allocating-path frame show the same colours)* — done 2026-09-29 as part
  of this capture; no source file changed yet.
- [x] 6.4 Verification that can actually see a swap (the seam test cannot — see D1b and task 2.3): a
  check in the consumer's layout, i.e. a device/instrumented colour assertion against a known stylesheet
  colour (water `#9acffd`, motorway `#7d7af5`), or a native-side assertion in the submodule that a
  direct-buffer render emits bytes R,G,B,A. State which of the two is the gate, and do not claim a
  Robolectric bitmap comparison for it. *(spec: render-performance — A buffer-path frame and an
  allocating-path frame show the same colours)* — done 2026-09-29 as the **native-side gate**: the
  layout helper moved into the private, dependency-free `libosmscout-client-java/src/frame_pixel_layout.h`
  and `Tests/src/FramePixelLayoutTest.cpp` pins both layouts (the `0xAARRGGBB` word for the `int[]`
  destination, the bytes R,G,B,A for the direct buffer, the same colour asserted in both and asserted
  *different* between them, plus the opaque-black `Clear`). Wired in `Tests/meson.build` and
  `Tests/CMakeLists.txt` exactly like the existing `SearchScopeTest` (which tests a sibling private
  header the same way). Evidence: `meson test -C hostbuild "Check frame pixel layout"` →
  **`1/1 OK`**, and the negative control — mutating the buffer write back to the `int[]` byte order makes
  it **FAIL** on `REQUIRE(bytes[0] == 0x7d)` and `REQUIRE(asWord == expected)`, and reverting restores
  `1/1 OK`. The gate therefore fails on the shipped defect, which the seam test cannot do. Not covered by
  it: that the platform's own bitmap order is R,G,B,A (that is the constant the helper encodes); a
  device/instrumented colour check stays the recorded on-device check (5.3-5.5), not a unit test.
- [x] 6.5 Commit the submodule fix on `naviveylin-local` and bump the gitlink in the main repo in the
  same commit series (`AGENTS.md`), then confirm the submodule is clean. *(spec: osmscout-jni — The
  buffer's layout is the consumer's, not the allocating path's)* — done 2026-09-29: submodule
  **`b560f5415`** on `naviveylin-local` (submodule clean afterwards), main repo **`90c7bf1`** (the
  gitlink bump, the `renderInto` declaration and `RenderBufferPool`). Not pushed. The openspec change
  directory is gitignored here, so these commits carry code only.

Note for tasks 1.2 and 2.3: their "byte for byte" comparison is satisfied by this defect and cannot be
retro-fitted to catch it, because both sides of that comparison go through the fake and a Robolectric
bitmap. They stay as they are; 6.4 is the check that covers the format.
