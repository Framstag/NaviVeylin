# Proposal: Fix basemap live reload after download

## Why

On a fresh install (no basemap), downloading and installing the basemap from the map manager works, but the basemap only appears after the app is restarted. Root cause: the basemap lookup directory is baked into the native `DBThread` at client build time, and `MapDownloadModule` only registers it when the directory already exists (`app/src/main/java/com/naviveylin/di/MapDownloadModule.kt:67`). A fresh install therefore registers nothing; the later `client.reloadBasemap()` call (`BasemapViewModel.kt:103`) is a no-op because `DBThread::LoadBasemap()` early-returns on an empty lookup directory (`libosmscout-client/src/osmscoutclient/DBThread.cpp:559`). This violates the `basemap-loading` spec scenario "Basemap downloaded while app is running", which only works today when a basemap existed at startup (the update path).

Secondary gap: even when the native reload succeeds (update/delete while running), the Java-side geographic tile cache (`MapRenderer.tileCache`, epoch-scoped) keeps serving tiles rendered without the basemap, and nothing triggers a re-render — the map view is stale until restart.

## What Changes

- **Native (submodule patch, minimal/upstreamable):** add `DBThread::SetBasemapLookupDirectory(const std::string&)` so the basemap lookup directory can be changed at runtime (thread-safe, then triggers the existing `ReloadBasemap()` reopen path); add a JNI bridge method `OSMScoutClient.setBasemapLookupDirectory(String)` in `libosmscout-client-java`.
- **App (phone + Android Auto):** after basemap download/update completes, call `setBasemapLookupDirectory(basemapDir)` + `reloadBasemap()`; after delete, `setBasemapLookupDirectory("")` + `reloadBasemap()`. Keep the startup registration in `MapDownloadModule` as-is (still correct for basemap-present-at-startup).
- **Renderer invalidation:** add a data-invalidation hook on `MapRenderer` (epoch bump + `tileCache.clear()` + forced full re-render, mirroring `MapRenderer.invalidateStyle()`, `MapRenderer.kt:410`) and wire `BasemapViewModel` → `MapCanvasViewModel` through a small shared notifier so the current view re-renders with the basemap overlay without a restart.
- **Spec:** extend `basemap-loading` requirements so live reload covers the fresh-install download case and the delete case, and the current view actually re-renders (not just DB reopen).
- Additive change, no breaking behavior. Rollback: revert the submodule commit and the app wiring; startup-path behavior unchanged.

## Capabilities

- **New Capabilities:** none.
- **Modified Capabilities:** `basemap-loading` (existing `openspec/specs/basemap-loading/spec.md`) — the "Reload basemap after download or delete" requirement gains fresh-install + visible re-render coverage. `basemap-download` behavior is unchanged (its "reload in rendering engine" scenario is already satisfied by this fix).

## Impact

Affected code:

| Area | Files |
|------|-------|
| Native core (submodule) | `app/src/main/cpp/libosmscout/libosmscout-client/src/osmscoutclient/DBThread.cpp`, `app/src/main/cpp/libosmscout/libosmscout-client/include/osmscoutclient/DBThread.h` |
| JNI bridge (submodule) | `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp`, `app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/OSMScoutClient.java` |
| App DI | `app/src/main/java/com/naviveylin/di/MapDownloadModule.kt` (unchanged startup gate; verify builder path) |
| App VM | `app/src/main/java/com/naviveylin/ui/mapmanager/BasemapViewModel.kt` (call setter + notifier on complete/delete) |
| App renderer | `app/src/main/java/com/naviveylin/ui/map/MapRenderer.kt` (invalidate-data hook), `app/src/main/java/com/naviveylin/ui/map/MapCanvasViewModel.kt` (subscribe, forward to renderer) |
| New shared notifier | `app/src/main/java/com/naviveylin/…/BasemapReloadNotifier.kt` (singleton, Hilt) |

Guidelines affected: `guidelines/Design.md` (§4 threading/lifecycle: DBThread reopen is async; ordering between reload job and render job), `guidelines/MapRendering.md` (tile-cache invalidation rules), `guidelines/UI.md` (phone + Android Auto parity — shared singleton client means both variants get the fix; verify Auto re-render path).

Scope: general feature — affects both phone/mobile and Android Auto (same `OSMScoutClient` singleton).

Native/JNI strategy: **submodule patch (minimal, upstreamable)** — a `SetBasemapLookupDirectory` on `DBThread` is a natural upstream addition (`ReloadBasemap`/`basemapLookupDirectory` already live upstream); no local override in the bridge module needed.

Rollback: revert submodule commit + app-side wiring; behavior falls back to today's restart-required path (basemap still loads at next startup).
