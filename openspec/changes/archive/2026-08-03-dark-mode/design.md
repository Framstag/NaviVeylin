## Context

See proposal.md — Why for motivation. Current state that shapes the approach:

- Compose theme (`ui/theme/Theme.kt`) follows system night mode directly: `NaviVeylinTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = true)`.
- Settings persist as a `@Serializable AppSettings` data class to JSON via `SettingsStorage` (filesDir, `ignoreUnknownKeys`); loaded in `MapCanvasViewModel`, toggled from `LocationOptionsOverlay` bottom sheet.
- Map rendering goes through JNI: Kotlin `MapRenderer` → `OSMScoutClient` (libosmscout-client-java) → C++ `ClientData` with `osmscout::DBThread`.
- `DBThread` already owns the day/night mechanism: `stylesheetFlags` map, `daylight` member, `Slot<> toggleDaylight`, `Slot<std::string,bool> setStyleFlag`, `Slot<std::string> reloadStyle`, `LoadStyleInternal(...)` which rebuilds every style config with the given flags and loads them per database.
- `standard.oss` ships `daylight = true` flag + `IF daylight { ... }` dark variants for land, water, roads, and areas.
- `MapRenderer` uses an epoch counter (`epoch.incrementAndGet()` on route/favorite changes) to discard stale renders; tiles stored in `TileCache` are tagged with the render epoch.
- OSMScout2 demo (upstream) toggles this via `map.toggleDaylight()` (Ctrl+D) — the reference for this change's on-map button.

## Goals / Non-Goals

**Goals:**
- Single resolved dark-presentation state (preference × environment) that drives both Compose theming and the native style sheet flag — one truth, no divergence.
- Three-state preference (On/Off/Automatic, default Automatic) persisted in the existing `AppSettings` JSON.
- Automatic = system night mode; resolution path open for a future car-environment signal source.
- Native JNI API to set the `daylight` style flag, triggering stylesheet reload + full map re-render.
- Three-state settings control (On/Off/Automatic).

**Non-Goals:**
- Car environment integration (`CarContext.isDarkMode()`) — explicitly deferred per user decision; only the resolution abstraction is prepared.
- New/dedicated dark style sheet files; reuse existing `daylight` flag in `standard.oss`.
- Per-screen theming or partial dark mode.
- Changing `dynamicColor` behavior.

## Decisions

### D1: One resolved presentation state drives both layers
`DarkModeController` (Hilt singleton) computes `resolvedDark: Boolean = pref == ON || (pref == AUTO && environmentDark)` via `combine(_preference, _environmentDark)`. The same value flows to `NaviVeylinTheme(darkTheme = resolvedDark)` in `MainActivity` and to the native flag push (`setStyleSheetFlag("daylight", !resolvedDark)`) collected in `MapCanvasViewModel`.
- **Why singleton:** the theme is applied in `MainActivity`, which sits above the `MapCanvasViewModel`/screen layer — the resolved state must be observable at activity level, and survive ViewModel recreation (e.g. navigating to the map manager screen). A singleton with eager `stateIn` provides that.
- **Alternative:** resolve inside `MapCanvasViewModel` only — rejected: theme state would die with the ViewModel and the activity could not read it.
- **Alternative:** theme reads `isSystemInDarkTheme()` independently while native follows the preference — rejected: settings + map + controls could disagree (e.g. user sets On but system is light).

### D2: Preference as enum in existing `AppSettings` JSON
Add `darkMode: DarkModePreference = DarkModePreference.AUTOMATIC` to `AppSettings`; `DarkModePreference` enum with `ON`, `OFF`, `AUTOMATIC`. `ignoreUnknownKeys` + default values keep old JSON files decodable (covered by spec scenario "Old settings files remain valid"). Enum names are serialized literally — keep them stable.
- **Alternative:** DataStore/SharedPreferences — rejected: inconsistent with existing persistence layer; JSON path already used for all settings.

### D3: JNI API `setStyleSheetFlag(String, boolean)` instead of `toggleDaylight()`
Expose `public native void setStyleSheetFlag(String key, boolean value)` on `OSMScoutClient`; implement in `OSMScoutClient.cpp` by calling `clientData->dbThread->setStyleFlag(key, value)` (existing slot → `LoadStyleInternal` → per-database `LoadStyle`). Caller passes `"daylight"` with `false`/`true`.
- **Alternative:** mirror the demo's `toggleDaylight()` — rejected: toggle is stateless (wrong semantics for On/Off preference, double-application could flip twice); absolute set matches preference semantics.

### D4: Style change forces full re-render and invalidates style-dependent tiles
After pushing the flag, the Kotlin side bumps the render epoch and issues `forceFullRender` (existing `requestRenderPreserveRoute`/epoch mechanism) so no front-buffer blit or cached tile from the previous variant survives. Upstream PR #1701 confirms daylight flag changes fill patterns — cache invalidation is required, not optional.
- **Alternative:** rely on native reload only — rejected: front buffer + tile cache are Kotlin-side and would show stale light tiles after switching to dark.

### D5: Threading — push flag on DBThread, re-render on UI scope
`setStyleFlag` runs the reload on `DBThread` (its slots execute on DBThread's own event loop); the re-render request goes through the existing render pipeline (debounced, background coroutine). No new threading model.
- **Risk:** reload is synchronous-ish per render call — mitigated by the existing debounce; first dark render may take longer than a pan (logged by slow-render threshold).

### D6: Settings control only — no on-map button
The three-state control lives in the existing `LocationOptionsOverlay` bottom sheet (radio group, same pattern as the orientation options); preference changes apply immediately via the resolved presentation flow. An on-map button was initially planned (OSMScout2 demo pattern) but dropped per user decision — the settings control suffices and keeps the map surface uncluttered.

### D7: First render honors the preference
`initMap` applies the resolved flag before/at `openDatabase` render start so the first frame is already correct (spec: "Map darkens from the start"). Implementation: resolve preference before first render request; push flag once client is built (flag push is idempotent — `DBThread` reloads with the same map).

## Risks / Trade-offs

- [Stylesheet reload cost / visible flicker on toggle] → Epoch-bumped full re-render with existing debounce; native reload already rebuilds style configs per database; acceptable one-time cost on manual toggle.
- [Stale tiles/front buffer between variants] → Epoch invalidation covers both (D4); verify `TileCache` keys are epoch-scoped during implementation.
- [`standard.oss` dark coverage gaps (e.g. un-dimmed roads, missing area variants)] → Audit `IF daylight` blocks per feature during implementation; minor style sheet touch-ups are in scope if a feature has no dark variant.
- [JNI/native rebuild required for new method] → Existing CMake target `osmscout_client_java`; ABI-safe signature; run assembleDebug for one ABI during dev.
- [Enum name stability in JSON] → Stable names `ON/OFF/AUTOMATIC`; decode failure falls back to defaults via existing try/catch.

## Migration Plan

- No data migration: new field defaults to `AUTOMATIC` for existing installs.
- No style sheet migration: `standard.oss` already supports `daylight` flag.
- Rollback: revert preference wiring; map falls back to light rendering; JNI method is additive.

## Open Questions

None blocking. Car environment signal source deferred (user decision) — the resolution path in D1 leaves a single extension point (swap/extend `environmentDark`).
