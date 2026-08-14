# Design: fix-download

## Context

See proposal.md — Why. Current download flow: Java `MapDownloadManager.downloadMapInJava()` downloads all files, then `nativeRegisterMapDirectory()` → C++ `RegisterMapDirectory()` → `MapManager::AddLookupDirectory()` → async `LookupDatabases()` on the MapManager worker thread. `onComplete` fires immediately, and the ViewModel's `refreshInstalledMaps()` reads `GetDatabaseDirectories()` — which can still be empty because the async scan has not finished (race). Additionally, `AddLookupDirectory` early-returns on duplicate directories without re-scanning, and `nativeDeleteMap` deletes files but never removes the directory from the lookup set — so delete + re-download of the same map never re-scans and the map stays invisible.

## Goals / Non-Goals

**Goals:**
- A completed download is always present in the installed list (no race).
- Delete + re-download of the same map works from a clean state.
- Download, delete, and refresh errors are visible with an explicit [OK] dismiss.

**Non-Goals:**
- No changes to the HTTP download mechanism itself (file transfer works).
- No changes to map rendering or `openDatabase` — the renderer opens maps by path and is unaffected once the map is listed.
- No UI redesign beyond error display in the download task.

## Decisions

### D1: Wait for native lookup before reporting completion — native side
After `AddLookupDirectory`, the JNI `nativeRegisterMapDirectory` explicitly runs `MapManager::LookupDatabases()` and blocks until the returned `CancelableFuture` completes, using `OnComplete` + a `std::promise` (the future API has no blocking `Wait()`).

- **Why native, not ViewModel polling:** the installed-list contract lives in the native `MapManager`; waiting at the source makes the guarantee deterministic for every caller, not just the current UI. Polling in the ViewModel adds a timeout heuristic and can still show a stale list.
- **Why a second explicit `LookupDatabases()` instead of waiting on the one queued by `AddLookupDirectory`:** `AddLookupDirectory` returns `void` and discards its future. Queuing one more lookup and waiting on it is safe — the MapManager worker serializes lookups, so the second scan runs after the first and its completion implies both are done.
- **Deadlock check:** the JNI call runs on the Java download worker thread (`map-download-<name>`); the lookup runs on the MapManager's own worker thread. Different threads, no re-entrancy, no deadlock. A timeout guard (e.g., 30 s) prevents a hung worker from blocking the download thread forever.

### D2: Always re-scan on registration — dedup fix
`MapManager::AddLookupDirectory` SHALL trigger `LookupDatabases()` even when the directory is already in `databaseLookupDirs` (move the call out of the dedup early-return).

- **Why:** after delete + re-download, the directory is still in the lookup set; the dedup early-return skipped the scan, so the re-downloaded map never appeared. Re-scanning on every registration is cheap (a few directories) and makes registration idempotent.

### D3: Delete removes the directory from the lookup set
Add `MapManager::RemoveLookupDirectory(dir)` that removes the directory from `databaseLookupDirs`. `nativeDeleteMap` calls it after `MapDirectory::DeleteDatabase()` succeeds, then triggers `LookupDatabases()` (already done today).

- **Why:** `DeleteDatabase()` only deletes files; the stale lookup entry is what breaks re-downloads. Removing it makes delete symmetric with register and keeps the lookup set consistent with storage.

### D4: Error surfacing in ViewModel + UI
- `MapManagerViewModel.deleteMap()` and `refreshInstalledMaps()` stop swallowing exceptions; failures set `uiState.error` (the existing error banner) with a descriptive message. `refreshInstalledMaps` keeps the previously known installed list on failure.
- Download errors already set `downloadStates[name] = Error` with `statusText = "Error: $msg"`. Add `dismissError(mapName)` that removes the error entry (entry returns to Available), and render the error task with an explicit [OK] button in `MapManagerScreen`.

- **Why ViewModel-level, not a new dialog system:** the error banner and task-state machinery already exist; this reuses them instead of introducing a new error channel.

## Risks / Trade-offs

- [Blocking the download thread on lookup] → bounded by a 30 s timeout; lookup is a local directory scan, normally milliseconds.
- [Double lookup per registration (D1 + D2)] → negligible cost (small directory tree); correctness over micro-optimization.
- [Error banner overwritten by concurrent operations] → banner shows the latest error; acceptable for this scope, each action's error is still visible.
- [Native changes touch vendored libosmscout-client code] → changes are additive (new method, moved call); the submodule is already locally patched (see AGENTS.md), keep changes minimal and documented.

## Migration Plan

1. Implement native changes (D1–D3) in `MapManager.cpp`/`.h` and `OSMScoutClient.cpp`.
2. Implement ViewModel + UI changes (D4).
3. Build debug APK, install, verify: download map A → appears; download map B → appears without restart; delete B → re-download B → appears; kill app mid-download → re-download works.
4. No rollback concern: changes are additive; revert = remove the new wait/removal logic.

## Open Questions

None — the three reported symptoms map directly to the three fixes (race, dedup, silent errors).
