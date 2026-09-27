# Design

## Context

A full render currently walks four full-size pixel buffers, none of them reused:

- `core/src/main/java/com/naviveylin/core/MapRenderUtil.kt:60-64` — `Bitmap.createBitmap(width,
  height, ARGB_8888)` per call, then `setPixels` from the JNI `int[]`. Called by the phone
  (`app/ui/map/MapRenderer.kt:783`) and the car (`auto/AutoMapRenderer.kt:1236`).
- The native side (`libosmscout-client-java/src/OSMScoutClient.cpp:1793-1818`) allocates the Cairo
  surface, the `argbPixels` vector, the per-pixel conversion pass and a fresh `jintArray` per render.
  Not in scope here (see Non-Goals), but it sets the peak the Java side sits next to.

Existing lifetimes the design must not break:

- **Phone** (`MapRenderer.kt`): the full-render path blits the returned bitmap into `backBuffer`
  (`:865`), recycles it (`:866`) and swaps `backBuffer`/`frontBuffer` (`:868-870`); the tile path
  composes into its own bitmap (`:666`) and copies into `frontBuffer` (`:838`). `emitFrame` (`:405-427`)
  always hands Compose an independent copy and never recycles the previous emitted frame.
- **Car** (`AutoMapRenderer.kt`): the rendered bitmap *becomes* `overrunBitmap` (`:1262-1263`), which
  stays displayed — blitted by the extrapolation loop (`:983`, `:1115`) — while the next render runs;
  it is recycled when replaced and on stop (`:1489-1490`). `displayedLat/Lon/Mag/Angle` (`:1929-1935`)
  and `overrunBitmapSize` (`:1939`) are derived from `overrunBitmap != null` and its dimensions.
- **Guideline** `guidelines/MapRendering.md` §2: every frame that reaches Compose must be an
  independent copy; a shared-region `createBitmap` plus `recycle` has already caused "trying to use a
  recycled bitmap" crashes and invisible-content jumps.
- `TODO.md` §51 ranks per-render churn as the leading remaining app-process killer on AAOS; §65 wants
  a quieter allocation profile to interpret its memory measurement.

## Goals / Non-Goals

**Goals**

- No bitmap allocation per render on either surface; render targets are handed out, used and returned.
- The car's per-drawn-frame marker objects (`Path`, `Paint`, `BlurMaskFilter`, `LinearGradient`) are
  created once and rebuilt only when their inputs change.
- Reuse is observable in a unit test, and the §2 lifecycle rules stay enforced (a displayed or
  emitted frame is never overwritten).
- Both surfaces, one rule: the reuse contract lives in `:core`, not twice in two renderers.

**Non-Goals**

- The native half: no caller-supplied `int[]` / direct buffer, no new JNI entry point, no submodule
  commit, no gitlink bump, no `:osmscout-client-java` override.
- No change to render rates, debounce/coalescing, overrun math, blit offsets, follow framing, marker
  geometry/size/palette, or the tile cache's own storage.
- No `dumpsys meminfo` measurement in this change (no device attached; the AAOS AVD is unusable for
  headless sessions — `TODO.md` §40.45). It is recorded as an open task.

## Decisions

### D1 — One `:core` seam owns the reuse rule

New `core/src/main/java/com/naviveylin/core/RenderBitmapPool.kt`, used through a new
`MapRenderUtil.renderToBitmap(..., target: Bitmap)`-style entry point (the existing signature stays
for callers that own their result, so no caller changes shape silently).

*Alternatives:* (a) a reusable field inside each renderer — the rule and its lifecycle reasoning
would exist twice, and the car's two-live-slot requirement would have to be re-derived per renderer;
(b) render directly into the double buffer's storage — impossible without the deferred JNI change,
since the native side returns a fresh array; (c) keep allocating and only add the overlay caching —
leaves the larger allocation (the bitmap) untouched. Chosen: (a) rejected on
`guidelines/Design.md` §12 (shared logic extracted once), (b) deferred as §49's native half,
(c) insufficient for the stated goal.

*Risk:* a single shared pool is a cross-surface contention point. Mitigation: the lock is held only
for the hand-out/release bookkeeping, never during a render or a draw (D8).

### D2 — Pool shape: size-keyed, bounded, free-list only

The pool keeps free targets in a map keyed by `(width, height)`, at most **two free targets per size
class** and at most **two size classes**; a target is removed from the free list when handed out and
re-inserted on release. Surplus targets are recycled immediately. Targets handed out are owned by the
holder and are not counted against the bound.

*Alternatives:* (a) one reusable bitmap total — unsafe: the car displays a frame while the next render
is in flight, so the single instance would be overwritten under the display; (b) unbounded free list
keyed by size — retains every historical size class (overrun multiplier changes, rotation, surface
resizes) indefinitely; (c) `SoftReference` cache — size and lifetime become GC-dependent, so the
"no allocation" assertion could not be tested deterministically. Chosen: two free slots covers the
car's displayed-plus-in-flight pair and the phone's transient one-slot use; the two-size-class cap
bounds retention after a resize.

*Retention numbers (documented, not measured here):* car overrun 1296×720 ≈ 3.7 MB per target →
≤ 7.5 MB retained; phone overrun 1296×2304 ≈ 11.9 MB per target → ≤ 23.9 MB retained, which is the
same order as the peak the native side already holds for one render (surface + vector + array +
bitmap). The pool changes *churn*, not peak.

*Risk:* retention is permanent where churn was transient. Mitigation: the bound above, release at
renderer shutdown (D7), and an on-device `dumpsys meminfo` comparison in the follow-up task.

### D3 — Ownership contract: hand out → use → release; the pool recycles

`acquire(width, height)` returns a target; `release(target)` returns it to the free list (or recycles
it when the bound is exceeded). **A holder never calls `recycle()` on a pooled target.** A released
target is not reused by its former holder, and the pool refuses a double release (tracked per target)
rather than risking a double-hand-out.

*Alternatives:* (a) let callers keep recycling and have the pool hand out recycled bitmaps — a
recycled `Bitmap` throws on the next draw; (b) no ownership check, rely on discipline — a double
release would hand the same storage to two holders, which is exactly the aliasing bug
`guidelines/MapRendering.md` §2 warns about. Chosen: explicit release with a per-target state flag.

### D4 — Frames that reach the display layer stay copies

`emitFrame`'s rule (`MapRenderer.kt:405-427`) is unchanged: the frame handed to Compose is
`fb.copy(...)`, never a pool target. The car keeps its pooled target only while that frame is the
displayed one, so a released slot is never the frame on the surface.

*Alternative:* hand the pool target to Compose directly and track when Compose is done — there is no
such signal from Compose, and the previous attempt at sharing storage produced the documented jumps.
Rejected.

### D5 — Car adoption: the displayed overrun frame *is* a pooled target, held until replaced

`AutoMapRenderer` acquires its render target from the pool inside the render path, publishes it as
`overrunBitmap` under `surfaceLock`, and **releases the previous target** instead of recycling it
(`:1262-1263`); the stop path (`:1489-1490`) releases the held target and clears the reference. The
`displayed*` derivations (`:1929-1939`) keep reading the held reference, so "a frame is displayed" is
true exactly as long as the slot is held.

*Alternatives:* (a) render into a scratch target and copy into a persistent `overrunBitmap` — adds a
3.7 MB copy per render, i.e. reintroduces half the churn; (b) release the target immediately after the
blit is computed — the extrapolation loop blits repeatedly (`:983`), so the pixels must live until
replacement. Chosen: hold-until-replaced, which is the current `recycle` lifetime with a new owner.

*Risk:* a size or rotation change allocates a new size class while the old one is still held; the
free-list bound caps what is retained (D2) and the old class is released on the next swap.

### D6 — Phone adoption: pooled target for the transient render result

Full-render path: acquire → `MapRenderUtil` writes into it → `Canvas(backBuffer).drawBitmap(target)`
→ release (replacing `bitmap.recycle()` at `:866`). The composition bitmap of the tile path (`:666`)
is acquired the same way and released after the `frontBuffer` copy (`:838`); the per-tile bitmaps that
go into the LRU `TileCache` (`:685`) stay ordinary allocations owned by the cache.

*Alternatives:* (a) leave the phone path alone — the seam is shared, so the same allocation would
simply remain on the busier surface; (b) pool the tile bitmaps too — their lifetime is the LRU cache's
eviction policy (`TileCache`), so pooling them would couple the pool to a second owner for a much
smaller win. Chosen: transient results only.

### D7 — Lifecycle: a stopped renderer holds no target

Both renderers release every target they hold on shutdown/stop: the car on
`AutoMapRenderer.stop`/surface release, the phone on renderer shutdown (`MapRenderer.kt:478-479`
currently recycles `backBuffer`/`frontBuffer` — those two are the *double buffer*, not pool targets,
and stay as they are). After a stop, the pool's free list holds at most the D2 bound.

### D8 — Threading and lock ordering

The pool guards its free list with one lock (`synchronized` on the pool; no coroutine suspension
inside, since callers hold it only for bookkeeping). Renders run on the renderers' existing background
dispatchers (`MapRenderer`'s render coroutine, `AutoMapRenderer`'s render scope); the car's swap and
release happen under `surfaceLock`, the phone's under `bufferLock`. The pool lock is **never** held
while taking `surfaceLock`/`bufferLock`, and vice versa: acquisition happens before entering the
critical section, release happens at the end of it. `MapRenderUtil` itself stays a pure
"write pixels into the target you were given" helper.

*Alternative:* a suspending `Mutex` in the pool — would force the renderer coroutines' call sites to
suspend around a bookkeeping step and risks holding a suspension point inside the display lock.
Rejected.

### D9 — Car overlay draw objects: lazily built field group, invalidated by input

`drawGpsMarker` (`:1563`) and `drawDestinationMarker` (`:1703`) keep their `Path`s (core, scaled
casing), `Paint`s, the shadow `BlurMaskFilter` and the core `LinearGradient` in a small cached holder
owned by the renderer, built on first use and rebuilt when one of the inputs changes: surface bounds,
density (`projectionDpi`), or `darkPresentation`. The marker's geometry and palette contract is
untouched — only the object lifetime changes.

*Alternatives:* (a) cache keyed by a data class of the inputs — the key object itself is a per-frame
allocation; (b) a generic object pool for draw objects — more machinery than three call sites need;
(c) hoist to `VehicleMarkerGeometry` (a `:core` object) — would make a stateless pure-geometry helper
stateful and shared across surfaces. Rejected.

### D10 — Reuse is observable

The pool exposes an `internal` allocation counter (incremented only when a target is actually
allocated) plus `internal` free-list introspection for tests. Production behaviour does not depend on
it.

*Alternative:* no hook, infer reuse from timing — not assertable; or a memory-tracking framework —
out of proportion. Rejected.

## Risks / Trade-offs

- **Retained memory vs. churn** (D2) — quantified above; the follow-up `dumpsys meminfo` task is the
  check that the trade-off is net-positive on the car.
- **Aliasing a pooled target that is still displayed** (D3/D4/D5) — the failure mode the guideline §2
  documents (invisible content or pixel jumps). Mitigated by the per-target state flag, by
  hold-until-replaced on the car and by `emitFrame`'s copy; a unit test asserts that a render during an
  uncompensated display uses a different target.
- **Double release / double hand-out** — refused and reported through `DiagnosticsLog` (`RENDER`
  tag) rather than silently aliasing.
- **Retention after a size change** (overrun multiplier switch, rotation, car surface resize) — capped
  by the two-size-class bound; the surplus is recycled at release.
- **Overlay caching and the day/night contract** — a stale `LinearGradient` would render the day
  palette at night (`auto-map-renderer` — "Map follows host day/night"). Mitigated by rebuilding on
  the `darkPresentation` change, asserted by a unit test on the rebuild trigger.
- **Robolectric cannot verify pixels** (`guidelines/Build.md` §40.21: the shadow canvas discards
  `drawBitmap`/`drawPath`) — so tests assert allocation counts and rebuild triggers, while visual
  identity stays an on-device check.

## Verification

- **Unit** (`:core`): pool reuse for consecutive same-size acquires, allocation counter, size-class
  change, bound enforcement and surplus recycling, double-release refusal, concurrent acquire/release
  from two threads, and that a released target is never returned twice without an intervening acquire.
- **Unit** (`:app`): `MapRenderUtilTest` extended for the pooled entry point — pixels written, target
  identity reused across calls, no allocation reported on the second call; `MapRenderer` state-level
  test that a render during a held front buffer uses a different target and that `emitFrame` still
  hands out an independent copy.
- **Unit** (`:auto`): renderer test using `RendererTestRule` that N consecutive frames report zero new
  allocations and reuse the same draw objects (identity assertions on the cached holder); rebuild on
  `darkPresentation` change and on a density change.
- **Revert-check**: with the pool call sites reverted to `createBitmap`/`recycle`, the
  "no allocation on the second render" and "frame draw allocates nothing" assertions fail.
- **Build/tests**: `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug` and the full
  `./gradlew test` (result dirs cleared first — `TODO.md` §17), with executed-task counts quoted.
- **On-device (deferred, not a gate of this change)**: on the AAOS AVD or a head unit with a map
  installed, `dumpsys meminfo` native/Java heap before and after a 10-minute drive, plus the render
  count from the `MAP` diagnostics, compared against the same run before the change (`TODO.md` §65's
  stationary-route variant); recorded in `TODO.md` §49 as the remaining verification.
