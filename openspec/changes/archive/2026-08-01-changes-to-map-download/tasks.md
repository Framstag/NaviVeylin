## 1. Wake Lock + Foreground Service

- [x] 1.1 Add `WAKE_LOCK` and `FOREGROUND_SERVICE` permissions to `AndroidManifest.xml`
- [x] 1.2 Create `MapDownloadService` foreground service with download notification
- [x] 1.3 Integrate `PowerManager` wake lock — acquire on first download, release on last download end
- [x] 1.4 Wire service start/stop into `MapManagerViewModel` download lifecycle
- [x] 1.5 Add Hilt module binding for `PowerManager` and notification channel setup

## 2. Progress Bar Reactive Update

- [x] 2.1 Add `progressMap: Map<String, Int>` to `MapManagerUiState` for per-map progress
- [x] 2.2 Update `onProgress` callback to write progress into `progressMap` and emit new UI state
- [x] 2.3 Fix `LinearProgressIndicator` in `ActiveDownloadRow` to read from reactive state
- [x] 2.4 Add `key` to `LazyColumn` items in `MapManagerScreen` for stable identity

## 3. Installed Maps Always Visible + On Top After Refresh

- [x] 3.1 Merge installed maps into `availableEntries` at ViewModel level — synthetic entries always present
- [x] 3.2 Add "Installed Maps" section header at top of tree when installed maps exist
- [x] 3.3 Sort installed entries to top of tree, available maps below
- [x] 3.4 Ensure `refreshAvailableMaps()` preserves installed entries in merged list

## 4. Build & Verify

- [x] 4.1 Run `./gradlew :app:assembleDebug` and fix any compilation errors
- [x] 4.2 Verify progress bar updates during test download
- [x] 4.3 Verify installed maps visible on screen open without refresh
- [x] 4.4 Verify installed maps stay on top after refresh
- [x] 4.5 Verify wake lock acquired/released correctly
