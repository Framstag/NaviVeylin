# Proposal: fix-download

## Why

Map downloads are unreliable: the second map download (island) completed without any error, but the map never appeared in the installed list and could not be rendered. Deleting and re-downloading did not help — still no error, still no map. Root causes are in the download lifecycle: the native map lookup runs asynchronously after registration (race), re-registration of a previously deleted map is skipped by a dedup check, and delete does not remove the directory from the map manager's lookup set. Errors are also silently swallowed in several paths, so the user has no way to understand or recover.

## What Changes

- **Guarantee installed-list visibility after download**: after a successful download + registration, the installed map list is refreshed only after the native `LookupDatabases()` scan has completed (synchronous wait or explicit re-scan), eliminating the race where `onComplete` fires before the async lookup finishes.
- **Clean redownload**: deleting a map removes its directory from the native `MapManager` lookup directories, and re-registering a directory always triggers a fresh `LookupDatabases()` even when the directory is already in the lookup set (dedup must not skip the re-scan).
- **Clean delete**: `nativeDeleteMap` removes the directory from `databaseLookupDirs` in addition to deleting files, so a subsequent download of the same map starts from a clean state.
- **Explicit error surfacing**: download errors are shown in the download task UI with an explicit [OK] dismiss action; errors from delete and installed-map refresh are surfaced to the UI instead of being silently swallowed.

## Capabilities

### New Capabilities

- *(none)*

### Modified Capabilities

- `map-download-infrastructure`: download completion must guarantee the map appears in the installed list (lookup completes before refresh); delete must remove the directory from the map manager's lookup set; re-download of a previously deleted map must trigger a fresh lookup.
- `map-download-ui`: download errors must be shown in the task with an explicit [OK] dismiss; delete and refresh errors must be visible to the user.

## Impact

- `app/src/main/cpp/libosmscout/libosmscout-client-java/src/OSMScoutClient.cpp` — JNI `nativeRegisterMapDirectory`, `nativeDeleteMap` (sync lookup, lookup-dir removal)
- `app/src/main/cpp/libosmscout/libosmscout-client/src/osmscoutclient/MapManager.cpp` — `AddLookupDirectory` dedup behavior, lookup-dir removal on delete
- `app/src/main/cpp/libosmscout/libosmscout-client-java/java/com/framstag/libosmscout/client/MapDownloadManager.java` — download completion flow (wait for lookup before `onComplete`)
- `app/src/main/java/com/naviveylin/ui/mapmanager/MapManagerViewModel.kt` — error surfacing for delete/refresh, refresh-after-download ordering
- `app/src/main/java/com/naviveylin/ui/mapmanager/MapManagerScreen.kt` — error task UI with [OK] dismiss
