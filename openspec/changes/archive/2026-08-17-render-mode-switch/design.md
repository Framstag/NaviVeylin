# Render Mode Switch — Design

## Context

See `proposal.md` — Why. Current state: `MapRenderer.executeRender` (MapRenderer.kt:640) picks the
render path at line 648:

```kotlin
val tilePath = job.angle == 0.0 || !job.forceFullRender
```

- `renderFromTiles` (L530) renders missing geographic tiles natively (one JNI call per tile,
  `tileSizePx` = 256 @ 96 dpi scaled by device dpi), caches them in `TileCache` (LRU, epoch-keyed),
  and composes them onto a screen-sized bitmap. Rotated views are composed by placing tiles
  north-up and rotating the whole canvas about the viewport center (`canvas.rotate(deg, W/2, H/2)`)
  — labels stay north-up.
- `MapRenderUtil.renderToBitmap` (core module) renders the full 1.2× overrun buffer in one native
  call, no cache; the result is copied into the back buffer, swapped to front, and the visible
  center region extracted (`extractCenterRegion`). Labels render natively in the viewport
  direction.

Both paths already coexist; neither is broken. The change makes the choice user-controllable and
removes the dead remnants of the old direct-render design.

## Goals / Non-Goals

**Goals:**
- Persisted, user-visible switch between tile-cached and direct rendering
- Switch applies immediately with a full re-render and no stale tiles/buffers from the other mode
- Remove dead direct-render code and fix the `tile-cache` spec that still describes it
- Both modes keep the existing guarantees: double buffering, sub-region blit pans, atomic
  frame emission (`FrameState`), epoch-based stale-frame discard

**Non-Goals:**
- No native/JNI changes; `renderWithRouteAndPois` and `render` stay as-is
- No changes to zoom limits (`MIN_MAG`/`GESTURE_MIN_MAG` floors stay 4 — low-zoom direct renders
  are a known risk, unchanged by this switch)
- No new settings screen; the switch lives in the existing `LocationOptionsOverlay`
- No Android Auto changes (`:auto` module untouched)

## Decisions

### D1. Persisted `AppSettings.renderMode`, default `TILES`

Add `enum class RenderMode { TILES, DIRECT }` and `val renderMode: RenderMode = RenderMode.TILES`
to `AppSettings` (SettingsStorage.kt:24). Persisted via the existing JSON file
(`maps/settings.json`) with `ignoreUnknownKeys = true`, so old settings files load unchanged.
Rationale: matches every other user preference (dark mode, auto-zoom, lane hints); survives
restarts; no new storage mechanism. Alternatives rejected: (a) in-memory only — mode resets every
launch, inconsistent with the rest of the settings surface; (b) build-time constant — not a user
switch, useless for on-device debugging.

### D2. Renderer mode as `@Volatile` field, read at job execution

Add `@Volatile var renderMode: RenderMode = RenderMode.TILES` to `MapRenderer` with a setter
(`setRenderMode(mode: RenderMode)`). `executeRender` computes:

```kotlin
val tilePath = renderMode == RenderMode.TILES && (job.angle == 0.0 || !job.forceFullRender)
```

Rationale: the render queue is a conflation channel of `RenderJob`s; a job-field approach would
require threading the mode through `RenderJob`/`PendingRender` and would snapshot stale modes in
queued jobs. Reading the volatile at execution time applies the switch at the next render with no
plumbing. A single in-flight job may still render in the old mode — harmless, because:
- old-mode tiles written to the cache while switching to DIRECT are never read (direct path ignores
  the cache);
- switching back to TILES clears the cache via `invalidateStyle()`.
Alternatives rejected: (a) constructor parameter — renderer is created once at map open
(MapCanvasViewModel L1066-1070); a switch mid-session would need renderer recreation (teardown of
the render scope, re-open of the JNI surface) — heavy and risks races with in-flight JNI renders;
(b) mode in `RenderJob` — snapshot semantics, extra plumbing, no benefit.

### D3. Apply via `invalidateStyle()` — full re-render, cache clear, epoch bump

The ViewModel toggle calls `mapRenderer?.invalidateStyle()` after persisting. `invalidateStyle`
(MapRenderer.kt:361) already bumps the epoch, clears the tile cache, and submits a forced full
render — exactly the "no tiles/front-buffer content from the previous variant survive" semantics
used for the dark-mode style-sheet switch. Additionally, entering DIRECT mode clears the tile cache
explicitly (the `invalidateStyle` path already does this) so cached tiles from TILES mode do not
hold memory while unused. Rationale: reuses a proven, spec'd mechanism; one code path for
"everything must be re-rendered". Alternatives rejected: (a) just `renderMap()` without epoch bump —
cached tiles with the old epoch would be composed into the first TILES-mode frame after switching
back; (b) renderer recreation (see D2).

### D4. UI: radio group in `LocationOptionsOverlay`

`LocationOptionsOverlay` (ModalBottomSheet with follow/auto-zoom/keep-screen-on/dark-mode/lane-hints
toggles, `RadioButton` already imported at L23) gains a "Rendering" section: two `RadioButton`s —
"Tile cache" and "Direct" — bound to `renderMode`, calling `onSetRenderMode(RenderMode)`.
Rationale: it is the established settings surface; radio group matches the dark-mode
On/Off/Automatic pattern; no new dialog/screen. Alternatives rejected: (a) `DropdownMenu` item on
the map canvas (L544) — menu is for navigation actions (Download Maps/Favorites/About), not
settings; (b) dedicated settings screen — out of scope, no such screen exists.

### D5. Remove dead direct-render code

Delete `MapRenderer.blitSubRegion` (L840-868, zero callers — superseded by `trySubRegionBlit`),
`ANGLE_EPSILON_RAD` (L896, unused), and the unused `TileCache` screen-space helpers
(`computeTileGrid`, `storeTiles`, `compose`, `CompositeResult`, `TILE_SIZE` — zero callers from
`MapRenderer`; `TILE_SIZE` is only referenced by those helpers). Update the `TileCache` class doc
comment (currently describes the removed split-after-full-render flow) and the `tile-cache` spec
requirements that describe it. Rationale: dead code contradicts the specs, invites future misuse
(a future reader re-wiring `storeTiles` into a split path), and the spec/code drift would fail the
project's own review rules. Alternatives rejected: keep-and-deprecate — no deprecation mechanism
for private Kotlin members; keeping contradicts "no dead code" review rules.

### D6. Direct mode keeps the existing zoom floors

No change to `MIN_MAG`/`GESTURE_MIN_MAG` (both 4). Direct mode at mag < 4 renders huge world
viewports natively (z=2 ~5s, z=1 hangs per the L1803 comment). Rationale: the floors already
prevent gesture-driven low zooms in both modes; lowering them for DIRECT is a separate feature.
Risk documented in Risks; the switch itself does not widen the zoom range.

## Risks / Trade-offs

- [Direct mode slower at low zooms] → zoom floors already prevent mag < 4; document in
  `guidelines/MapRendering.md` that DIRECT at mag 4 renders a full-world viewport and can be slow
  on first draw (no cache warm-up)
- [Rotated labels differ between modes] → TILES composes rotated views by canvas rotation (labels
  north-up); DIRECT renders natively with rotated labels. This is the intended observable
  difference the switch exposes; the UI radio labels stay neutral ("Tile cache"/"Direct"), and the
  change does not alter either mode's rotation behavior
- [Mode switch mid-gesture discards a frame] → `invalidateStyle` bumps the epoch; the in-flight
  job's result is discarded (existing epoch check, executeRender L690). Next debounced render
  produces the new mode's frame; acceptable, matches dark-mode switch behavior
- [Old-mode tile writes during switch race] → volatile read at execution means at most one job
  renders in the old mode; its tiles are either ignored (DIRECT) or cleared by the switch back
  (TILES via `invalidateStyle`). No corruption possible — cache entries are epoch-keyed
- [Spec drift breaks review] → this change ships the `tile-cache` spec correction in the same
  change that removes the code the spec describes; `openspec validate` must pass before archive
- [Existing tests reference removed helpers] → `TileCacheTest`/`TileCacheRenderTest` exercise
  `storeTiles`/`compose`; they are migrated to geographic-tile assertions (per-tile render + cache
  + compose via `renderFromTiles`) or trimmed to the retained API (`get`/`put`/`contains`/`clear`/
  `retainEpoch`)

## Migration Plan

1. Add `RenderMode` + `renderMode` to `AppSettings`; verify old `settings.json` loads (unit test)
2. Add `renderMode` to `MapRenderer` + gate in `executeRender`; add `MapCanvasUiState.renderMode` +
   VM load/toggle/apply
3. Add radio group to `LocationOptionsOverlay`, wire through `MapCanvasScreen`
4. Delete dead code (`blitSubRegion`, `ANGLE_EPSILON_RAD`, `TileCache` screen-space helpers);
   update `TileCache` doc comment
5. Migrate/extend tests (`TileCacheTest`, `TileCacheRenderTest`, `MapRendererSmokeTest`,
   `MapRendererRotatedRenderTest`); add `RenderModeSwitchTest`; run `./gradlew test`
6. Update `openspec/specs/tile-cache/spec.md` + `guidelines/MapRendering.md`
7. Device-verify: switch modes at runtime, pan/zoom/rotate in both, restart persistence
8. `openspec validate` + `openspec sync` + archive

## Open Questions

None — remaining unknowns (radio group placement inside the sheet, exact label text) are cosmetic
and resolvable during implementation without affecting specs, approach, or task breakdown.
