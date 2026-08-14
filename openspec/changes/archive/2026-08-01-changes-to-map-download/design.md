## Context

Map download currently uses `MapManagerViewModel` with `MapDownloadManager` JNI bridge. Progress callbacks arrive via `MapDownloadListener` but state updates don't reliably trigger Compose recomposition. No wake lock or foreground service exists — screen may turn off or app may be hibernated mid-download. Installed maps are tracked via `installedMapPaths` set but synthetic entries are lost after a provider refresh.

See proposal.md for motivation. See specs/ for detailed requirements.

## Goals / Non-Goals

**Goals:**
- Keep screen on + CPU awake during active map downloads
- Progress bar updates reactively (every frame during download)
- Installed maps always visible in tree, even before any provider refresh
- After refresh, installed maps stay at top of tree for easy deletion

**Non-Goals:**
- No changes to native download engine (libosmscout-client)
- No changes to map rendering or routing
- No Android Auto integration

## Decisions

### Decision 1: Foreground service + wake lock instead of just wake lock
**Choice:** Use a foreground service (`MapDownloadService`) with a partial wake lock.

**Rationale:** Android 12+ aggressively hibernates background apps. A wake lock alone prevents CPU sleep but doesn't prevent the system from killing the process. A foreground service with a visible notification prevents both hibernation and process death. The notification also gives the user visibility into active downloads.

**Alternatives considered:**
- Wake lock only: Cheaper but app can still be killed on low memory
- `FLAG_KEEP_SCREEN_ON` on window: Only works when screen is already on and activity is visible; doesn't prevent CPU sleep
- WorkManager: Designed for short background work, not long downloads with user-visible progress

### Decision 2: Progress fix via `snapshotFlow` + keyed items
**Choice:** Add a `progressMap: Map<String, Int>` to `MapManagerUiState` that updates on every progress callback, and use `snapshotFlow` in the composable to drive `LinearProgressIndicator`. Also add `key` to `LazyColumn` items for stable identity.

**Rationale:** The current `downloadStates` map is updated from a background thread. While `StateFlow` is thread-safe, Compose recomposition may not trigger reliably when the same list reference is reused with new item contents. Moving progress into the UI state as a flat map and using `key` on `LazyColumn` items ensures stable recomposition.

**Alternatives considered:**
- `derivedStateOf`: Adds indirection without fixing the root cause
- `LaunchedEffect` polling: Wasteful and introduces latency
- `withFrameMillis`: Over-engineered for this use case

### Decision 3: Merge installed maps into tree at ViewModel level
**Choice:** `MapManagerViewModel` maintains a merged list of `AvailableMapEntry` objects — installed maps from `installedMapPaths` plus fetched entries from `fetchAvailableMaps()`. Installed entries are flagged and sorted to the top.

**Rationale:** The current approach of synthetic entries only works when `availableEntries` is empty. After refresh, installed entries disappear. Merging at the ViewModel level gives a single source of truth for the tree and keeps the screen composable simple.

**Alternatives considered:**
- Two separate lists in UI state: Requires the screen to manage two sections, more complexity
- Post-processing in composable: Business logic in UI layer, harder to test

### Decision 4: Installed section header in tree
**Choice:** Add an "Installed Maps" section header at the top of the tree when installed maps exist, visually separated from available maps below.

**Rationale:** Users need to find installed maps quickly for deletion. A section header with visual separation (divider, different background tint) makes them immediately discoverable.

## Risks / Trade-offs

- [Foreground service notification may annoy users] → Show only during active downloads; auto-dismiss when done
- [Wake lock increases battery drain] → Only held while downloads are active; released immediately on completion/cancel/error
- [Progress updates at high frequency may cause jank] → Throttle UI updates to ~100ms intervals; Compose skips duplicate frames
- [Merged tree may confuse users if installed maps differ from provider list] → Installed section is clearly labeled; available section shows what the provider offers
