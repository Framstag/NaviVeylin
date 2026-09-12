## 1. Screen reorder (spec: `map-download-ui` — Section ordering, Loading indicator placement, Error banner placement)

- [x] 1.1 Reorder the `LazyColumn` items in `MapManagerScreen.kt` to: provider row → search field → error banner → active downloads → basemap section → installed maps → available maps, and verify the new `MapManagerScreenOrderingTest` (task 3.1) passes
- [x] 1.2 Move the loading indicator item to directly below the provider row; when `isLoading && availableEntries.isEmpty() && installedEntries.isEmpty()` render a centered spinner as the only content, and verify the ordering test covers the loading position
- [x] 1.3 Move the error banner item to directly below the search field (above active downloads), and verify the ordering test covers error placement
- [x] 1.4 Gate the installed-maps section on `searchQuery.isBlank()` so it hides while a query is non-blank, and verify the search-hides-installed test (task 3.2) passes
- [x] 1.5 Unify the "Installed maps" and "Available maps" header styling (`titleSmall` + `FontWeight.Bold` + `colorScheme.primary`), and verify no header uses `onSurfaceVariant` for section titles

## 2. Basemap progress dedup (spec: `basemap-ui` — Provide basemap download/update control)

- [x] 2.1 In `BasemapSection.kt`, render a compact status line (no progress bar) when `state.isDownloading`, keeping the cancel control, and verify the downloading-state test (task 3.3) passes
- [x] 2.2 Keep the basemap progress row (`basemapDownloading` / `basemapProgress` / `onCancelBasemap` + `BasemapDownloadRow`) in `ActiveDownloadsSection` so basemap progress shows exactly once, in the active-downloads section (spec `map-download-ui` "Basemap download shown with progress"), and verify the screen compiles and the ordering test still passes

## 3. Tests (spec: `map-download-ui`, `basemap-ui`)

- [x] 3.1 Create `MapManagerScreenOrderingTest.kt` (Robolectric + Compose, default sandbox per the AGENTS.md classloader rule) asserting section order: provider row, search field, active downloads, basemap section, installed maps, available maps, and verify it passes
- [x] 3.2 Add a test asserting the installed-maps section is hidden while a search query is non-blank and reappears when cleared, and verify it passes
- [x] 3.3 Extend `BasemapSectionComposeTest.kt` with a downloading-state test asserting the compact status line is shown and no progress bar renders in the basemap section, and verify it passes

## 4. Build and regression verification

- [x] 4.1 Run `./gradlew :app:assembleMobileDebug` and verify the build compiles without errors
- [x] 4.2 Run `./gradlew test` and verify all existing tests still pass (including `BasemapSectionComposeTest`)

## 5. Guidelines and on-device verification

- [x] 5.1 Check `guidelines/UI.md` for any section-ordering or map-manager guidance; update the document if this change supersedes a stated principle, and verify the diff is limited to the guideline file
- [x] 5.2 On an emulator, open the map manager with an active download and verify: sections render in the spec order, basemap download shows a single progress indication in the active-downloads section, and searching hides the installed section
