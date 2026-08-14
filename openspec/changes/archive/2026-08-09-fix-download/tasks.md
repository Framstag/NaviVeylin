# Tasks: fix-download

## 1. Native MapManager changes

- [x] 1.1 `MapManager::AddLookupDirectory` — move `LookupDatabases()` call out of the dedup early-return so registration always triggers a re-scan (design D2)
- [x] 1.2 Add `MapManager::RemoveLookupDirectory(dir)` — remove directory from `databaseLookupDirs` (design D3)
- [x] 1.3 Declare `RemoveLookupDirectory` in `MapManager.h`

## 2. JNI bridge changes

- [x] 2.1 `nativeRegisterMapDirectory` — after `RegisterMapDirectory`, run `LookupDatabases()` and block on its `CancelableFuture` via `OnComplete` + `std::promise`, with a 30 s timeout guard (design D1)
- [x] 2.2 `nativeDeleteMap` — after `DeleteDatabase()` succeeds, call `RemoveLookupDirectory` before `LookupDatabases()` (design D3)

## 3. ViewModel changes

- [x] 3.1 `deleteMap()` — remove silent exception catch; surface failure via `uiState.error` (spec: map-download-ui, delete failure shown)
- [x] 3.2 `refreshInstalledMaps()` — remove silent exception catch; surface failure via `uiState.error`, keep previously known installed list on failure (spec: map-download-ui, refresh failure shown)
- [x] 3.3 Add `dismissError(mapName)` — clears the error entry from `downloadStates`, entry returns to Available (spec: map-download-ui, OK dismisses error)

## 4. UI changes

- [x] 4.1 `MapManagerScreen` — render download error task with error message and explicit [OK] button wired to `dismissError` (spec: map-download-ui, error shown with explicit OK)
- [x] 4.2 Verify error task persists until dismissed — not cleared by progress updates or list refreshes (spec: map-download-ui, error persists until dismissed)

## 5. Verification

- [x] 5.1 Build debug APK (`./gradlew :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a`)
- [x] 5.2 Manual test: download map A → appears in installed list without restart (verified via device logcat 22:39–22:51: onComplete + installed with Delete)
- [x] 5.3 Manual test: download map B → appears in installed list without restart (verified via device logcat 22:53–22:55: Iceland onComplete)
- [x] 5.4 Manual test: delete map B → re-download B → appears in installed list (verified via device logcat 22:56–22:58: delete OK, redownload onComplete)
- [x] 5.5 Manual test: kill app mid-download → re-download same map → completes cleanly, map appears
- [x] 5.6 Manual test: force download failure (airplane mode) → error task shown with [OK], OK dismisses, entry back to Available
- [x] 5.7 Run unit tests (`./gradlew test`)

## 6. Multi-map rendering fix (found during device testing)

- [x] 6.1 `OSMScoutClient.openDatabase` — keep accumulating paths (multi-map); renderer shows whichever loaded map covers the viewport (reverted an exclusive-open experiment that broke multi-map)
- [x] 6.2 Add JNI `getDatabaseBoundingBox(path)` + Java declaration — bounding box of a single map database without disturbing loaded databases
- [x] 6.3 `ViewportStorage` per-map keying; `MapCanvasViewModel.initMap` centers on map bounding box, falls back to per-map saved viewport
- [x] 6.4 Manual test: maps A + B installed, open B → B renders centered on its region; pan to A's region → A renders; reopen B → resumes saved position

## 7. Main screen shows downloaded maps (found during device testing)

- [x] 7.1 `NavGraph` MAIN route — when MAIN becomes current (e.g., back from Map Manager), re-check installed maps; render `MapCanvasScreen` with the first installed map instead of the welcome screen; `key(visitCount)` forces re-initialisation on every return
- [x] 7.2 `MapCanvasViewModel.initMap` — tear down previous renderer on re-entry; open all other installed maps in addition to the selected one (multi-map viewport coverage)
- [x] 7.3 Manual test: start with no maps → MAIN shows welcome → download map(s) in Map Manager → back → MAIN shows the map
- [x] 7.4 Manual test: with 2+ maps installed, MAIN map view renders the map covering the viewport when panning between regions
