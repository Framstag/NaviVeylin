# basemap-own-stylesheet — Design

## Context

See proposal.md — Why. Current state shaping the approach:

- `DBThread::LoadStyleInternal` (libosmscout-client) loads one style file (`stylesheetFilename + suffix`) for every regular database AND the basemap database. The basemap DB has ~11 types (basemap.ost) + 3 water-index tiles, so loading `standard.oss` (~485 `[TYPE]` rules) into it emits ~450 "Unknown type" parser warnings per load (`Parser::STYLEFILTER_TYPE`, oss/Parser.cpp:1108).
- The renderer already uses per-database style configs: `renderWithRouteAndPois` sets `mapData.styleConfig = db->GetStyleConfig()` per DB (OSMScoutClient.cpp:1280) and skips DBs without a style config. No render-path change needed — only the style loaded into the basemap DB.
- `basemap-render.oss` exists in the stylesheets dir (copied to device assets by `AssetCopier`) but is referenced nowhere in code. It is currently listed by `getAvailableStyleSheets()` and thus selectable in the style picker — selecting it for the main map would render an almost empty map.
- The app pushes dark mode via `client.setStyleSheetFlag("daylight", !dark)` (MapCanvasViewModel.kt:516), which triggers `LoadStyleInternal` with the flag — so the basemap stylesheet must handle `IF daylight`/`ELSE` like the main styles.
- Phone and Android Auto share one `OSMScoutClient` singleton (`AutoServiceModule.provideAutoClientProvider`), so a builder-level change covers both variants.

## Goals / Non-Goals

**Goals:**
- Basemap renders with `basemap-render.oss`, loaded with zero unknown-type warnings.
- `basemap-render.oss` is a complete, functional stylesheet: dark mode, valid symbols, AREA text, MAG ranges matching `standard.oss`'s `place.oss`.
- `basemap-render` is not offered in the style picker.
- Backward compatible: no basemap style configured → current behavior.

**Non-Goals:**
- No changes to the render path (per-DB style configs already work).
- No new basemap types in basemap.ost — the stylesheet styles only what the basemap DB already contains.
- No changes to the main stylesheets (`standard.oss` etc.) — the ~450 warnings for the basemap DB disappear by loading a basemap-appropriate style, not by editing standard.oss.
- No dark-mode color redesign — mirror upstream dark values from `land_sea_color.oss`/`place.oss`.

## Decisions

### D1: Basemap style filename lives on DBThread, set at construction

`DBThread` gains a `basemapStyleFilename` member (absolute path, default empty). `LoadStyleInternal` loads `basemapStyleFilename + suffix` for the basemap DB when set, else falls back to the main style file:

```cpp
if (basemapDatabase) {
  std::string basemapFile = basemapStyleFilename.empty() ? file : basemapStyleFilename + suffix;
  basemapDatabase->LoadStyle(basemapFile, stylesheetFlags, styleErrors);
}
```

`makeStyleConfig` (used when a database opens, including `LoadBasemap`) gains an optional `styleFilename` parameter so the basemap's initial style config is also `basemap-render.oss` — without this, the basemap briefly loads the default style (standard.oss) at open time and emits the same unknown-type warnings.

Rationale: the style filename is static configuration, not runtime state — unlike the basemap lookup directory (which changes on download/delete and already has `SetBasemapLookupDirectory`). A constructor parameter keeps the API minimal and the value immutable; every style load (initial, flag change, style switch) re-reads it through `LoadStyleInternal`.

Alternatives considered:
- **Runtime setter (`SetBasemapStyleFilename`)** mirroring `SetBasemapLookupDirectory` — rejected: no runtime path changes the basemap style; a setter would add API surface without a caller.
- **Hardcode `"basemap-render"` in DBThread** — rejected: DBThread has no stylesheet directory and hardcoding a file name is not upstreamable.

### D2: JNI resolves the basemap style file against the stylesheet directory

`OSMScoutClientBuilder.withBasemapStyleSheet(String name)` stores the style name (without `.oss`). `build()` validates it (reject empty, path-like, `.`/`..` — same rules as `loadStyleSheet`, OSMScoutClient.cpp:833-839), resolves `stylesheetDir + "/" + name + ".oss"`, and passes the absolute path to the `DBThread` constructor.

Rationale: `DBThread` receives absolute paths for styles today (`loadStyleSheet` resolves `dir + "/" + name + ".oss"` before calling `LoadStyle`), so the basemap style follows the same convention. The JNI layer owns path resolution because it owns the stylesheet directory (via `Settings::GetStyleSheetDirectory`).

Alternatives considered:
- **Pass the bare name to DBThread and resolve there** — rejected: DBThread has no stylesheet directory reference.
- **Hardcode the resolution in the app** — rejected: the app would have to duplicate the stylesheet-dir path logic that the JNI bridge already owns.

### D3: Picker exclusion happens in the app, not the JNI bridge

`MapCanvasViewModel` filters `basemap-render` out of the list returned by `getAvailableStyleSheets()` before publishing `availableStyleSheets` to the UI.

Rationale: `libosmscout-client-java` is a reusable library — it must not hardcode NaviVeylin's basemap-internal style name. The app owns the product decision "this style is not user-selectable".

Alternatives considered:
- **Filter in `getAvailableStyleSheets()`** — rejected: bakes an app-specific convention into the generic library.
- **Rename the file so it isn't a top-level `.oss`** (e.g. move to a subdirectory) — rejected: `syncSubmoduleStylesheets` copies the whole stylesheets dir and `AssetCopier` mirrors it; a subdirectory would still be scanned by `getAvailableStyleSheets` (it lists top-level files only, so a subdir would work) but would diverge from upstream's stylesheets layout and complicate the submodule sync.

### D4: basemap-render.oss mirrors place.oss MAG structure

The stylesheet keeps its minimal, basemap-only type set but adopts the magnification ranges of `standard.oss`'s `include/place.oss` for the shared place types:

| Type | MAG ranges (matching place.oss) |
|------|----------------------------------|
| `_tile_sea`/`_tile_land`/`_tile_unknown` | 0- (all levels, like land_sea.oss) |
| `basemap_boundary_country` | 2-3, continent-stateOver (like include/basemap.oss) |
| `place_continent` | world-continent, 2-continent, 3-continent, continent-continent |
| `place_ocean` | world-continent, 2-continent, 3-continent |
| `place_sea` | continent-stateOver (NODE.TEXT + AREA.TEXT + dashed AREA.BORDER) |
| `place_country` | continent-stateOver, state-, stateOver- |
| `place_capitalcity` | state-city (NODE.TEXT + `place_capital_city` symbol) |
| `place_millioncity` | state-city (NODE.TEXT + `place_city` symbol) |
| place AREA.TEXT | county- |

Rationale: the user decision "match standard.oss" — basemap labels must persist at the same zoom levels the standard stylesheet provides, so areas without regional coverage don't lose basemap context when zoomed in. The basemap data is low-zoom optimized, but the stylesheet must not hide labels the standard style would show.

### D5: Dark mode mirrors upstream dark values

`IF daylight`/`ELSE` branches for all colors, copying the dark values from `land_sea_color.oss` (water `darken(#9acffd, 0.5)`, land `#333333`, unknown `#000000`) and `place.oss` (place label `#a0a0a0`, ocean label `#000020`, sea label `#000020`). The `daylight` flag is pushed by the app on dark-mode change and reloads both styles through `LoadStyleInternal`.

### D6: Symbol fix reuses `place_capital_city`

The undefined `symbol: "star"` on `place_capitalcity` is replaced with the existing `place_capital_city` symbol (double circle, `#8a4e4e`) — the same symbol `standard.oss` uses for capitals. `place_millioncity` gets `place_city` (single circle), matching place.oss. No new symbol definitions needed; both symbols are defined in `include/place.oss`, which `basemap-render.oss` must include (or duplicate) for the symbols to resolve.

## Risks / Trade-offs

- **basemap-render.oss parse failure** → basemap DB gets `styleConfig = nullptr`; the renderer skips the basemap DB (`loadDbData` early-returns) — no crash, but no basemap until the next successful load. Mitigation: the stylesheet is small and validated on-device; a failure is logged via the existing `styleErrorsChanged` path.
- **Dark-mode colors look off on the basemap** → Mitigation: copy upstream dark values verbatim; verify on-device in dark mode.
- **Symbols must resolve** → `place_capital_city`/`place_city` are defined in `include/place.oss`; `basemap-render.oss` must include it (or the symbols render nothing). Mitigation: include `include/place.oss` and verify capitals render on-device.
- **Picker filter is app-side** → a future basemap-internal style would need the filter updated. Mitigation: the filter is a single constant; documented in the spec exception.
- **Submodule patch** → the change touches `libosmscout-client` (platform-independent, uses `osmscout::log` only) — passes the CI "Android-free" gate. Upstreamable as a natural extension of the basemap overlay feature.

## Migration Plan

1. Submodule: DBThread `basemapStyleFilename` + `LoadStyleInternal` change; JNI builder field + resolution.
2. Stylesheet: complete `basemap-render.oss`.
3. App: `MapDownloadModule` `.withBasemapStyleSheet("basemap-render")`; picker filter in `MapCanvasViewModel`.
4. Verify: build both flavors; on-device — zero "Unknown type" warnings for the basemap DB, dark mode follows, style switch leaves basemap on `basemap-render`, basemap download/delete while running still re-renders.

Rollback: revert the submodule commit and the app wiring; the basemap falls back to the main style (today's behavior). No data impact.

## Open Questions

None — all decisions resolved with the user (picker exclusion, MAG ranges matching standard.oss, `place_capital_city` symbol reuse).
