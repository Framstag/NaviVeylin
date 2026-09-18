# basemap-own-stylesheet

## Why

The world basemap is rendered with the same user-selected stylesheet as the regional maps (`DBThread::LoadStyleInternal` loads one style file for every database, including the basemap). The basemap database has only ~11 types (basemap.ost) plus water-index tiles, so loading `standard.oss` (~485 `[TYPE]` rules) into it emits ~450 "Unknown type" parser warnings on every style load, and the basemap is styled by rules written for regional data rather than for its actual content. A dedicated, complete `basemap-render.oss` already exists in the stylesheets directory but is dead code — never wired into the build or runtime.

## What Changes

- **Basemap gets its own stylesheet**: `DBThread` loads a separate style file for the basemap database (`basemap-render.oss`) instead of the main style. The renderer already uses per-database style configs, so no render-path change is needed.
- **`basemap-render.oss` completed**: dark mode (`IF daylight`/`ELSE` branches mirroring `land_sea_color.oss`/`place.oss`), the undefined `"star"` symbol replaced with the existing `place_capital_city` symbol, `AREA.TEXT` for sea/continent/capital/millioncity (all `NODE AREA` types in basemap.ost), and magnification ranges matching `standard.oss`'s `place.oss` so basemap labels persist at the same zoom levels as today. Only types present in the basemap database are referenced — zero unknown-type warnings.
- **JNI bridge**: `OSMScoutClientBuilder.withBasemapStyleSheet(String name)` configures the basemap style name; `build()` resolves it against the stylesheet directory (same path-traversal validation as `loadStyleSheet`) and passes the absolute path to `DBThread`.
- **App wiring**: `MapDownloadModule` adds `.withBasemapStyleSheet("basemap-render")`. Phone and Android Auto share the same `OSMScoutClient` singleton, so both variants get the fix.
- **Style picker excludes `basemap-render`**: `basemap-render.oss` is a basemap-internal style, not a user-selectable map style — selecting it for the main map would render an almost empty map (only basemap types styled). The picker filters it out.
- **Additive, non-breaking**: when no basemap style is configured, `DBThread` falls back to the current behavior (main style for the basemap). Rollback: revert the submodule commit and the app wiring; behavior returns to today's single-style path.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `basemap-loading` (`openspec/specs/basemap-loading/spec.md`): the basemap SHALL render with its own dedicated stylesheet (`basemap-render.oss`) that references only basemap types, loads without unknown-type warnings, follows the dark-mode flag, and is unaffected by main-style switches.
- `map-styles` (`openspec/specs/map-styles/spec.md`): the "All bundled styles are selectable" requirement gains an exception — `basemap-render` is not offered in the style picker because it is the basemap's internal style, not a user-facing map style.

## Impact

| Area | Files |
|------|-------|
| Native core (submodule) | `app/src/main/cpp/libosmscout/libosmscout-client/src/osmscoutclient/DBThread.cpp`, `app/src/main/cpp/libosmscout/libosmscout-client/include/osmscoutclient/DBThread.h` — `basemapStyleFilename` member; `LoadStyleInternal` and `makeStyleConfig` (incl. `LoadBasemap`) use it for the basemap DB |
| JNI bridge (submodule) | `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` (builder field read + resolution), `app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/OSMScoutClientBuilder.java` (`withBasemapStyleSheet`) |
| App DI | `app/src/main/java/com/naviveylin/di/MapDownloadModule.kt` — `.withBasemapStyleSheet("basemap-render")` |
| App style picker | `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` — filter `basemap-render` from `availableStyleSheets` |
| Stylesheet | `app/src/main/cpp/libosmscout/stylesheets/basemap-render.oss` — dark mode, symbol fix, AREA.TEXT, MAG ranges matching `place.oss` |
| Specs | `openspec/specs/basemap-loading/spec.md`, `openspec/specs/map-styles/spec.md` (deltas in this change) |
| Guidelines | `guidelines/MapRendering.md` — note that the basemap uses its own stylesheet (style-loading section) |

**Native/JNI strategy**: submodule patch (minimal, upstreamable) — a per-basemap style filename on `DBThread` is a natural upstream addition (the basemap overlay already exists upstream; the style is currently shared). No local override in the bridge module.

**Scope**: general feature — affects both phone/mobile and Android Auto (shared `OSMScoutClient` singleton).

**Rollback**: revert the submodule commit and the app wiring; the basemap falls back to the main style (today's behavior). No data impact.

**Verification**: build both flavors; on-device — logcat shows zero "Unknown type" warnings for the basemap DB, dark mode follows the basemap, main-style switches leave the basemap on `basemap-render`, basemap download/delete while running still re-renders correctly.
