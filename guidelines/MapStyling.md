# Map Styling — stylesheets, icons and the tile cache

This document owns the stylesheet contract (day/night, basemap, load failure, source and on-device
copy), the POI icons and where they come from, and the native tile-data cache. The history and the
packaging failures behind these rules are in `guidelines/MapStyling.why.md`.

---

## 15. Day/Night (daylight flag) contract

- The stylesheet `daylight` flag selects the map variant: set = daylight, unset = dark.
  Native side: `setStyleSheetFlag("daylight", !dark)` → `DBThread.SetStyleFlag` →
  `LoadStyleInternal(stylesheet, flags)` reloads the variant on the DB thread; the next
  render uses it. No tile cache survives the reload (variant switch must not show
  tiles from the other variant).
- **Rule: Phone**: the environment source is `DarkModeController` (system night mode, or the
  ambient light sensor when enabled — see `dark-mode` spec). `pushDarkPresentation`
  dedupes and calls `invalidateStyle()` (epoch bump + full re-render).
- **Rule: Android Auto**: the source is the HOST's day/night state, never the phone's system
  mode — the car process runs on the phone but the head unit decides (its own light
  sensor / time). `NavigationSession.hostDark` (init from `CarContext.isDarkMode()`,
  updated in `onCarConfigurationChanged`) feeds both map screens; `CarDaylightApplier`
  dedupes the push **by value**; `invalidateStyle()` (blit bypass + full render) re-renders
  so the stale-variant overrun buffer is never blitted. Templates are host-rendered and
  follow the host automatically — only the app-drawn surface needs this.
- **Rule: Startup race (both variants)**: a flag push made before any database is open is **not** lost, so no variant
  needs a retry. `DBThread::SetStyleFlag` stores the value in `stylesheetFlags` whenever the database set is
  initialized, and `isInitializedInternal()` is an `all_of` over that set — vacuously true while it is empty
  (`DBThread.cpp:91-95`, `:422-427`) — while a load over an empty set reports success
  (`DBThread.cpp:455-467`, `:492`). The open path is where the stored flag reaches the maps: each database is
  pushed only after `Open` succeeded and gets its style config from `makeStyleConfig`, which applies
  `stylesheetFlags` (`DBThread.cpp:290-323`, `:360-369`), so the first render after warmup already carries the
  value. The one state that would drop a push — a database in the list that is not open — is not reachable from
  the app: a list change closes and clears the set before rebuilding it under the same write lock
  (`DBThread.cpp:183-188`). Consequence for the appliers: the flag is keyed on the value alone on both variants
  (phone `stylePushedToNative` re-pushes on the first rendered frame, car `CarDaylightApplier` as soon as a
  renderer exists) and **no** application follows a style load (change `fix-car-style-switch-costs-one-load`).
  Forbidding an application on every settings re-read is what change `fix-aaos-host-crash` (design D5)
  established: a bare `reset()` there pushed a flag, reloaded the variant and forced a full render every few
  seconds for nothing.
- **Rule: Periodic work bound (Android Auto)**: the same change bounds the follow-mode
  full-render request — no request while a full render is in flight, and the interval is
  `max(200 ms, the last measured render duration)`. On the automotive AVD a full render
  at 1080x600 takes 2-5 s, so the fixed 200 ms interval re-requested a frame the renderer
  could not keep up with.
- Initial `isDarkMode()` may be false until the host sends configuration
  (`UI_MODE_UNKNOWN`); the first `onCarConfigurationChanged` corrects it (one extra
  render at startup).
- **Rule: Load budget and dedupe key (change `dedupe-stylesheet-loads`)**: the app-side state that decides
  whether to touch the native side is the pair **(style name, `daylight`)**. Phone:
  `MapCanvasViewModel.ensureMapStyle` is the single entry, with the reason
  `RE_OBSERVED` / `STYLE_SWITCH` / `STARTUP_DB_READY` / `FIRST_RENDERED_FRAME`; car:
  `CarStyleApplier` (style name) plus `CarDaylightApplier` (`daylight`). Both native calls
  (`loadStyleSheet`, `setStyleSheetFlag`) reload the whole stylesheet set, so one startup loads it
  **once**, plus one load per legitimate trigger: a style switch, a real presentation change, a **map
  database open** (a freshly opened database carries no style config, so `STARTUP_DB_READY` forces
  it), the bundled-asset refresh after an update (it runs before the start's load), and the basemap's
  own stylesheet per basemap open. A style load carries the flag, so a platform-driven surface (Android Auto)
  does **not** re-apply it: `loadStyleSheet` hands the load the enabled flags
  (`app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp:2226-2235`, `GetStyleFlags()`),
  and `DBThread::LoadStyleInternal` stores them for the new style and its basemap
  (`.../libosmscout-client/src/osmscoutclient/DBThread.cpp:455-460`) and installs them before the parse
  (`DBThread.cpp:366-369`). That is the native check `TODO.md` §120 asked for, which is why the car's
  re-application after a switch is gone (change `fix-car-style-switch-costs-one-load`). The dedupe is
  app-side only: no native accessor exposes the installed pair, and the app is its only writer
  (`getActiveStyleSheet()` / `wasLastStyleLoadSuccessful()` report the outcome).

---
## 15a. Stylesheet load failure (app side) — never render a rejected style, never crash

- **Rule: Client contract (libosmscout, branch `naviveylin-local`; change `client-style-load-resilience`): a stylesheet that fails to parse NEVER becomes the active style.** The previously installed style
  configuration stays; a database that never had one gets the empty configuration (draws no content);
  the painter is never handed a rejected or absent configuration. The load outcome is reported per
  attempt through `OSMScoutClient.wasLastStyleLoadSuccessful()` and the active style through
  `getActiveStyleSheet()` (which now reports what is *actually* installed, not what was requested).
  Before that change a rejected stylesheet left the database without a style and the render pipeline
  faulted (`Fatal signal 11` in `osmscout::StyleConfig::HasNodeTextStyles` from
  `MapPainter::PrepareNode`).
- **Rule: App side**: every load path reports the failure once through the shared seam
  `com.naviveylin.core.MapStyleLoadReporter` (one `DiagnosticsLog` entry + the same wording on both
  surfaces; strings `map_style_load_failed` / `map_style_load_failed_kept` in `:core`, EN + DE):
  - phone: `MapCanvasViewModel.applyStyleSheet` (style switch, persisted style at start) and
    `pushDarkPresentation` (style-flag reload) → message via `uiState.snackbarMessage`;
  - car: `CarStyleApplier.onLoadFailed` on both map screens → `reportCarStyleLoadFailure` +
    `CarStyleLoadNotifier` (channel `map_style`, IMPORTANCE_LOW, silent, auto-cancel). The car
    templates have no general message slot, so a notification is the non-blocking surface — do not
    add a template slot for this without a decision (design D2 of the change).
- **Rule: Startup fallback**: a persisted style whose stylesheet cannot be loaded falls back to
  `BundledMapStyles.DEFAULT_STYLE_NAME` (`standard`) so the first map display is never empty; the
  failure is still reported. Do not "fix" this by clearing the user's selection.
- **Rule: Verification**: a stylesheet defect is NOT caught by building (§40.55) — the app must be run once with
  `adb logcat -s NaviVeylin | grep -i "style error"`; the expected outcome is a degraded map (no
  content for the affected database) plus the message, and no `Fatal signal 11`.
  `StylesheetHexColorCaseTest` guards the one packaging-time trigger it detects (uppercase hex).
- **Rule: Test fixtures and report parsing (2026-10-05).** A fixture stylesheet must follow the OSS grammar:
  a node rule is `NODE.TEXT` / `NODE.ICON` (`NODESTYLEDEF = "NODE" "." (NODETEXTSTYLE | NODEICONSTYLE)`), a
  way rule needs no suffix (`[TYPE _route] WAY { … }`) — writing `NODE { … }` makes every fixture load fail
  with `Symbol: "." expected`. Give the test seam a failure path that prints `GetErrors()` instead of only
  asserting `Load() == false`. And when two emitted lines intentionally share a prefix (the condensed
  `Unknown types in '…'` report and its gated Debug detail line), select the one under test by **level**
  (or an exact marker), never by substring — the aggregate helper counted its own detail line and reported
  `2 == 1`.

---
## 16. Basemap stylesheet contract

- The basemap database has its own type config (basemap.ost, ~11 types + water-index
  tiles), so it is rendered with a **dedicated stylesheet** (`basemap-render.oss`)
  instead of the user-selected main style. Loading the main style into the basemap DB
  would emit ~450 "Unknown type" parser warnings per load.
- Native side: `DBThread` holds a `basemapStyleFilename` (absolute path, configured
  via `OSMScoutClientBuilder.withBasemapStyleSheet`); `LoadStyleInternal` loads it for
  the basemap database and the main style for the regular databases. Empty filename =
  fall back to the main style (default). The renderer already uses per-database style
  configs (`mapData.styleConfig = db->GetStyleConfig()`), so no render-path change is
  needed.
- `basemap-render.oss` references ONLY basemap types — zero unknown-type warnings. It
  mirrors `include/place.oss` magnification ranges (labels persist at the same zoom
  levels as the standard stylesheet) and handles the `daylight` flag (dark mode).
  Symbols (`place_capital_city`, `place_city`) are duplicated locally, NOT included
  via `MODULE "include/place"` — the module would pull in style rules for types not
  present in the basemap DB and reintroduce warnings.
- `basemap-render` is NOT offered in the style picker: `MapCanvasViewModel` filters it
  from `getAvailableStyleSheets()` (spec: map-styles). It is the basemap's internal
  style, not a user-facing map style.
- Style switches and the `daylight` flag reload BOTH styles through
  `LoadStyleInternal`; the basemap style is unaffected by main-style switches. The pair key of §15
  covers it too: a flag application reloads the basemap stylesheet as well, which is why a redundant
  flag push costs two parse sets, not one.

---
## 16a. POI icons — submodule source, configured directory, symbol fallback

- The stylesheets request POI icons **by name** (`NODE.ICON { name: bus_stop; }`); `symbol:` is a
  vector symbol defined inline in the stylesheet, and the renderer prefers the image
  (`MapPainter::LayoutPointLabels`): image → else symbol → else nothing. A style carrying a name and
  no symbol therefore draws **nothing** unless its PNG loads (that was the defect `fix-poi-symbol-icons` closed).
- Sourcing: only the raster leaf (`libosmscout/data/icons/14x14/standard/`) is packaged — at build
  time by `syncSubmoduleIcons` into `assets/icons/14x14/standard`, mirroring the stylesheet rule — and
  mirrored to internal storage by `AssetCopier.ensureIcons()`. The client gets it once from
  `MapDownloadModule.configureClient(...)`. The neighbouring SVG set is deliberately **not** shipped:
  `MapPainterCairo` loads `<name>.png` only ("TODO: add support for reading svg images"), so a
  symbol-backed entry whose PNG upstream ships as SVG draws its symbol and logs one failed-load line.
- **Rule: The configured directory must end with a path separator.** The loader concatenates
  (`*path + name + ".png"`), so a path without one fails *silently*, with the same message as a
  directory that was never configured. `IconAssets.clientDirectory(...)` is the only place that
  builds this path — do not hand-build it.
- Gates: `PackagedPoiIconsTest` fails when a stylesheet requests an icon (a `name:` without a
  `symbol:`) that the packaged set does not carry; `checkSubmoduleIcons` fails the build when the
  submodule or its raster leaf is missing.
- On-device evidence is the absence of the failure line:
  `adb logcat -s NaviVeylin | grep "ERROR while loading image"` leaves only `charging_station` and
  `mini_roundabout` (symbol-backed, upstream SVG only).
- The **icon-versus-symbol preference** moves that line. With it on (`preferSymbolIcons` in
  `MapParameter`, the phone setting `preferSymbolPoiIcons`, default off), `MapPainter::LayoutPointLabels`
  draws an entry that carries a symbol from the symbol and never reaches `HasIcon`, so no image is
  looked for: `charging_station` and `mini_roundabout` then produce **no** failure line, and a dual
  entry such as `hospital` or `bus_stop` stops drawing its raster icon. The recipe above is the
  **preference-off** one; on device, name which value is in effect next to the log.

---
## 16b. Stylesheet source and the on-device copy

- The libosmscout submodule's `stylesheets/` directory (`app/src/main/cpp/libosmscout/stylesheets/`) is the
  **single source of truth** for the map style sheets. The build copies it into
  `build/generated/assets/stylesheets` — `Build.md` §12 names the sync and check tasks and their `preBuild`
  wiring — so a submodule bump changes the next APK with no manual step and there is **no committed snapshot**
  in `app/src/main/assets/`.
- `AssetCopier` refreshes the on-device copy from the APK on every app start (per-file size + SHA-256
  compare, deleting stale files), so an existing install picks up new styles after an update without clearing
  its data. A stylesheet failure at load time is §15a's contract, not this one.

---
## 17. Native tile-data cache — capacity, retention and its release

`NativeTileDataCache` (core) is the single owner of libosmscout's per-database `MapService` cache
capacity. It is not the app's own bitmap tile cache (`ui/map/TileCache.kt`): this one holds the loaded
tile *data* per database, which is why walking a wide area is what makes a process big.

- Constants: `PHONE_TILES` 512 (phone map path, `MapCanvasViewModel.initMap`), `CAR_TILES` 128 (car
  warmup, `AutoServiceModule`), library default 25. Applied per database (regional maps + basemap) and
  re-applied on every render, so it is a property of the *client*, not of a database.
- **Rule: Configuration is raise-only**: the highest value any surface has requested wins, a lower request is
  reported `REJECTED`. That is what keeps a car session from shrinking the phone's working set (and vice
  versa) mid-session.
- **Rule: The one lowering path is an explicit release**: `NativeTileDataCache.trim(client, tiles)` reports
  `TRIMMED`/`UNCHANGED`/`FAILED` and is called only by `MemoryPressureResponder`. The native side evicts
  least-recently-used tile data on a smaller value (`DataTileCache::SetSize` → `CleanupCache`), so no
  bridge change is needed and rendered content never changes — only data reuse.
- **Rule: Triggers** (change `bound-tile-data-retention`, design D1):
  - a 30 s poll of `ActivityManager.MemoryInfo` while a client has a configured capacity —
    `lowMemory` halves the capacity, `availMem <= threshold / 2` floors it at 25. This is the *only*
    pressure signal the platform still offers: **`TRIM_MEMORY_RUNNING_MODERATE/LOW/CRITICAL`, `MODERATE`
    and `COMPLETE` are not delivered to apps since API 34** (AOSP `ComponentCallbacks2`), and
    `ComponentCallbacks.onLowMemory()` is deprecated since API 35. Do not build a release on them.
  - `TRIM_MEMORY_UI_HIDDEN` (halves) and `TRIM_MEMORY_BACKGROUND` (floors) — the two levels that *are*
    still delivered — **only while no car session is live**: with one, the car surface keeps rendering
    from those caches and a release would buy a refetch for nothing.
- **Rule: Measured retention ceiling** (2026-09-27, Pixel 8, scripted walk = 10 zoom-out steps + 14 pan swipes
  via `adb shell input`): native heap 115 MB → 337 MB, `TOTAL PSS` 377 MB → 600 MB, saturating under
  continued panning, retaining 324 MB after returning to the start viewport; the app's bitmap cache stayed
  flat. Quote the walk with any retention number, and see `guidelines/Build.md` §10 for the measurement
  rule (every footprint counter here is a high-water mark).
- **Rule: No fault escapes a release** (`MemoryPressureResponder`): the release runs on a process-wide
  dispatcher from a process-scoped responder, so its body is `runCatching`-wrapped and a confined fault is
  recorded under the `MEMORY` tag (throwable class only, never its message). Without that, a throwable
  raised inside a *memory-pressure* callback reaches the thread's uncaught-exception handler and kills the
  app — the one moment it can least afford it. The same missing confinement leaked an uncaught exception
  into the test JVM and was misattributed to another package for a whole session (closed 2026-09-27 by `bound-tile-data-retention`,
  `ki_processing_failures.log` 2026-09-27): any long-lived scope that a test can construct needs one.

---

## 20. Label wrapping — the bounds the renderer configures

- The app configures the wrapping on `MapParameter` in the native render (`renderMapIntoPixels` in
  `OSMScoutClient.cpp`): `LabelLineMinCharCount` 5, `LabelLineMaxCharCount` 15, `LabelLineFitToArea` true,
  `LabelLineFitToWidth` `min(width, height)` of the render buffer — the bounds the Qt plane renderer uses.
  Setting none of them leaves the library defaults (`MapParameter.cpp`: 5 / 15 / true / 8000 px), whose
  8000 px bound never binds.
- **Rule: The character count is the bound that wraps**, not the pixel width: `MapPainter::GetProposedLabelWidth`
  always caps the proposed width at `LabelLineMaxCharCount * averageCharWidth` and floors it at
  `LabelLineMinCharCount * averageCharWidth`, so the pixel bound only ever narrows a wide area label.
- **Rule: A label is broken at a word boundary only**, in the backends that wrap at all: a one-word label stays
  on one line however wide it is (Pango `PANGO_WRAP_WORD`, and the no-Pango Cairo wrapper keeps an over-wide
  word on a line of its own). `Escaldes-Engordany` therefore stays one line while `Andorra la Vella` wraps.
  The AGG backend never wraps — it takes the object width and the wrapping flag as unused parameters — so
  none of this reaches it.
- **Rule: The Android build has no Pango**: `features.cmake`'s `find_package(Pango)` does not resolve under the NDK,
  so `MapCairoFeatures.h` carries `/* #undef OSMSCOUT_MAP_CAIRO_HAVE_LIB_PANGO */` and
  `MapPainterCairo::Layout` is the FreeType/cairo variant. Absence of the dependency is not the reason — the
  three Android vcpkg triplets install pango, harfbuzz and fribidi, and `pkg-config --modversion pangocairo`
  answers `1.58.0` when pointed at one of them (`MapStyling.why.md` §20); the module's own detection is what does
  not reach them. The variant wraps the text itself and reads the same bounds through
  `GetProposedLabelWidth`. The Pango variant wraps at the width of the *object* it is handed
  (`PANGO_WRAP_WORD`) and ignores the label-line parameters, so it does not wrap a point label, whose
  object width is 0; Android does not build it. The Qt and Skia backends compute their width from
  `GetProposedLabelWidth` too.
- **Rule: What the Android configure does define** is `OSMSCOUT_MAP_CAIRO_HAVE_LIB_FREETYPE` and
  `OSMSCOUT_MAP_CAIRO_HAVE_LIB_CAIRO_FT` (same build tree, same date), so the backend also compiles
  `OSMSCOUT_MAP_CAIRO_LOAD_FONT_FILE`: a configured **font file** is loaded with FreeType and drawn through
  the FreeType font backend of cairo, and `FontNameResolution::CanReadFamilyFromFile()` answers for it. No
  packaged stylesheet configures a font file — the styles name font families — so this changes no label the
  app draws today; it is the path that would be taken the moment a style named a file.
- A wrapped label reports the ink width of its widest line and the lines' block height, so the label
  layouter places, culls and breaks overlaps with the size the label is drawn at.

---
