# Tasks — render-mode-switch

## 1. Settings + State Plumbing

- [x] 1.1 Add `enum class RenderMode { TILES, DIRECT }` and `val renderMode: RenderMode = RenderMode.TILES` to `AppSettings` in `SettingsStorage.kt` (spec: render-mode-switch — "User-selectable render mode")
- [x] 1.2 Add `renderMode: RenderMode = RenderMode.TILES` field to `MapCanvasUiState` (spec: render-mode-switch — "User-selectable render mode")
- [x] 1.3 In `MapCanvasViewModel` init settings load (~L729): read persisted `renderMode` into `_uiState` (spec: render-mode-switch — "Selection persists across restarts")
- [x] 1.4 Add `onSetRenderMode(mode: RenderMode)`: update `_uiState`, persist via `settingsStorage.save`, apply via `mapRenderer?.invalidateStyle()` (spec: render-mode-switch — "Mode switch re-renders from scratch")

## 2. Renderer Gating

- [x] 2.1 Add `@Volatile var renderMode: RenderMode = RenderMode.TILES` + `setRenderMode(mode)` to `MapRenderer` (spec: render-mode-switch — "Render pipeline honors the selected mode")
- [x] 2.2 In `executeRender` (~L648) change to `val tilePath = renderMode == RenderMode.TILES && (job.angle == 0.0 || !job.forceFullRender)` (spec: render-mode-switch — "Render pipeline honors the selected mode"; design D2)
- [x] 2.3 Verify direct mode never reads/writes `tileCache` and tile mode fallback (antimeridian, tile failure) still reaches `MapRenderUtil.renderToBitmap` (spec: render-mode-switch — "Direct mode ignores the tile cache" / "Tile mode falls back on tile-path failure")

## 3. UI

- [x] 3.1 Add "Rendering" radio group (Tile cache / Direct) to `LocationOptionsOverlay` using existing `RadioButton` pattern, params `renderMode: RenderMode` + `onSetRenderMode: (RenderMode) -> Unit` (spec: render-mode-switch — "User-selectable render mode"; design D4)
- [x] 3.2 Wire `uiState.renderMode` + `viewModel.onSetRenderMode` through `MapCanvasScreen` (spec: render-mode-switch — "User-selectable render mode")

## 4. Dead Code Removal + Spec Sync

- [x] 4.1 Delete `MapRenderer.blitSubRegion` (L840-868) and `ANGLE_EPSILON_RAD` (L896) (spec: render-mode-switch — "Dead direct-render remnants removed")
- [x] 4.2 Delete `TileCache.computeTileGrid`, `storeTiles`, `compose`, `CompositeResult`, `TILE_SIZE`; update class doc comment to describe the geographic-tile design (spec: render-mode-switch — "Dead direct-render remnants removed")
- [x] 4.3 Grep `blitSubRegion`, `ANGLE_EPSILON_RAD`, `storeTiles`, `computeTileGrid` — no references remain (spec: render-mode-switch — "No dead-code references remain")
- [x] 4.4 Archive the change to merge the corrected `tile-cache` requirements into `openspec/specs/` (spec: render-mode-switch modified requirements — "LRU tile cache" / "Tile composition")

## 5. Tests

- [x] 5.1 Migrate `TileCacheTest`: replace `storeTiles`/`compose` assertions with geographic-tile assertions (`put`/`get` keyed by zoom/x/y, LRU eviction, epoch invalidation) (spec: render-mode-switch modified — "LRU tile cache")
- [x] 5.2 Migrate `TileCacheRenderTest` to exercise per-tile native render + composition via the renderer's tile path (spec: render-mode-switch modified — "Tile composition")
- [x] 5.3 Add `RenderModeSwitchTest` (Robolectric): `executeRender` uses tile path in TILES mode, direct path in DIRECT mode; mode switch clears cache and bumps epoch; in-flight job discarded on switch (spec: render-mode-switch — "Render pipeline honors the selected mode" / "Mode switch re-renders from scratch")
- [x] 5.4 Add settings persistence test: old `settings.json` without `renderMode` loads as TILES, new value round-trips (spec: render-mode-switch — "Old settings file stays valid" / "Selection persists across restarts")
- [x] 5.5 Update `MapRendererSmokeTest`/`MapRendererRotatedRenderTest` if they assert path selection; extend with mode assertions (spec: render-mode-switch — "Render pipeline honors the selected mode")
- [x] 5.6 Run `./gradlew test` — full suite green (remember Robolectric classloader rule: no `@Config` on classes touching `FakeOSMScoutClient`)

## 6. Build & Device Verification

- [x] 6.1 Build debug APK: `./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a` — no warnings
- [x] 6.2 On device in TILES mode: pan/zoom/rotate work as before (tile hits logged, only missing tiles rendered) (spec: render-mode-switch modified — "Cached tile reused on subsequent render")
- [x] 6.3 On device in DIRECT mode: every render is full native (no tile hits), rotated labels correct, switch applies immediately with no stale tile content (spec: render-mode-switch — "Mode switch re-renders from scratch")
- [x] 6.4 On device: kill + restart app — mode restored; old app-data upgrade (settings.json without renderMode) renders in TILES (spec: render-mode-switch — "Selection persists across restarts")

## 7. Finalize

- [x] 7.1 Update `guidelines/MapRendering.md` §1 (pipeline): document the two modes, the gating condition, DIRECT-mode low-zoom slowness risk, and the removed screen-space tile-split helpers (spec: render-mode-switch — "Dead direct-render remnants removed")
- [x] 7.2 Verify no other docs reference removed helpers (search `storeTiles`, `computeTileGrid`, `blitSubRegion` in `docs/`, `README`, `AGENTS.md`)
- [x] 7.3 `openspec archive render-mode-switch` — validate passes, merges specs, archives the change
